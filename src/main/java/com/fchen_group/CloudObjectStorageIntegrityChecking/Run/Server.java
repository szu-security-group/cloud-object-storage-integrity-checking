package com.fchen_group.CloudObjectStorageIntegrityChecking.Run;

import java.io.*;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Properties;

import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.model.PutObjectResult;

import com.fchen_group.CloudObjectStorageIntegrityChecking.main.CloudObjectStorageIntegrityChecking;
import com.fchen_group.CloudObjectStorageIntegrityChecking.main.ChallengeData;
import com.fchen_group.CloudObjectStorageIntegrityChecking.main.ProofData;

public class Server {
    private String pathPrefix;
    private String COSConfigFilePath;
    private String COSSecretId;
    private String COSSecretKey;
    private COSClient cosClient;
    private String bucketName;

    public static void main(String[] args) throws Exception {
        if (args.length == 2) {
            new Server(args[0], args[1]).run();
        } else {
            show_help();
        }
    }

    public Server(String pathPrefix, String COSConfigFilePath) {
        this.pathPrefix = pathPrefix;
        this.COSConfigFilePath = COSConfigFilePath;
        // 从配置文件获取 COSSecretId, COSSecretKey
        try {
            FileInputStream propertiesFIS = new FileInputStream(COSConfigFilePath);
            Properties properties = new Properties();
            properties.load(propertiesFIS);
            propertiesFIS.close();
            COSSecretId = properties.getProperty("secretId");
            COSSecretKey = properties.getProperty("secretKey");
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 初始化用户身份信息（COSSecretId, COSSecretKey）。
        COSCredentials cred = new BasicCOSCredentials(COSSecretId, COSSecretKey);
        // 设置 bucket 的区域
        Region region = new Region("ap-chengdu");
        ClientConfig clientConfig = new ClientConfig(region);
        // 生成 cos 客户端。
        cosClient = new COSClient(cred, clientConfig);
        // 设置 BucketName-APPID
        bucketName = "crypto2019-1254094112";
    }

    public void run() throws Exception {
        // 配置NIO线程组
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            // 服务器辅助启动类配置
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            // 添加自定义协议的编解码工具
                            ch.pipeline().addLast(new CoolProtocolEncoder());
                            ch.pipeline().addLast(new CoolProtocolDecoder());
                            // 处理服务器端操作
                            ch.pipeline().addLast(new ServerHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 1024) // 设置tcp缓冲区
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(9999).sync();
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    class ServerHandler extends SimpleChannelInboundHandler<Object> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            int filePathLength = 0;
            byte[] filePathBytes;
            String filePath = "";
            CoolProtocol coolProtocol;
            CoolProtocol coolProtocolReceived = (CoolProtocol) msg;

            String filename = (new File(new String(coolProtocolReceived.filename))).getName();
            boolean needDelete = true;

            switch (coolProtocolReceived.op) {
                case 0:
                    filePathLength = filename.length();
                case 1:
                    if (filePathLength == 0) {
                        filePathLength = filename.length() - ".properties".length();
                        needDelete = false;
                    }
                case 2:
                    if (filePathLength == 0) {
                        filePathLength = filename.length() - ".tags".length();
                    }

                    // store file
                    File file = new File(pathPrefix + filename);
                    file.createNewFile();
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    fileOutputStream.write(coolProtocolReceived.content);
                    fileOutputStream.close();
                    // upload file
                    uploadFile(pathPrefix + filename, filename);
                    // delete file
                    if (needDelete) {
                        file.delete();
                    }

                    // send confirm
                    filePathBytes = new byte[filePathLength];
                    System.arraycopy(filename.getBytes(), 0, filePathBytes, 0, filePathLength);
                    filePath = new String(filePathBytes);
                    coolProtocol = new CoolProtocol(coolProtocolReceived.op, filePath.getBytes(), "".getBytes());
                    ctx.writeAndFlush(coolProtocol);
                    break;

                case 3:
                    filePathLength = filename.length() - ".challenge".length();
                    filePathBytes = new byte[filePathLength];
                    System.arraycopy(filename.getBytes(), 0, filePathBytes, 0, filePathLength);
                    filePath = new String(filePathBytes);
                    ChallengeData challengeData = (ChallengeData) deserialize(coolProtocolReceived.content);
                    ProofData proofData = prove(filePath, challengeData, filePath);
                    String proofFilePath = pathPrefix + filePath + ".proof";
                    coolProtocol = new CoolProtocol(5, proofFilePath.getBytes(), serialize(proofData));
                    ctx.writeAndFlush(coolProtocol);
                    break;

                default:
                    System.out.println("Invalid op");
            }
        }
    }

    public void uploadFile(String localFileName, String cloudFileName) {
        File localFile = new File(localFileName);
        PutObjectResult putObjectResult = cosClient.putObject(bucketName, cloudFileName, localFile);
    }

    public ProofData prove(String filePath, ChallengeData challengeData, String cloudFileName) throws Exception {
        String filename = filePath;
        filePath = pathPrefix + filename;
        String propertiesFilePath = filePath + ".properties";
        String tagsFilePath = filePath + ".tags";

        // get BLOCK_NUMBER and SECTOR_NUMBER
        FileInputStream propertiesFIS = new FileInputStream(propertiesFilePath);
        Properties properties = new Properties();
        properties.load(propertiesFIS);
        propertiesFIS.close();
        int BLOCK_NUMBER = Integer.parseInt(properties.getProperty("BLOCK_NUMBER"));
        int SECTOR_NUMBER = Integer.parseInt(properties.getProperty("SECTOR_NUMBER"));
        CloudObjectStorageIntegrityChecking cloudObjectStorageIntegrityChecking = new CloudObjectStorageIntegrityChecking(filePath, BLOCK_NUMBER, SECTOR_NUMBER);

        // initial cos
        COSObject cosObject;
        InputStream cloudFileIn;
        // 声明下载源文件所需变量
        GetObjectRequest getSourceFileRequest = new GetObjectRequest(bucketName, cloudFileName);
        long blockStart, blockEnd;
        byte[] sourceFileBlock = new byte[SECTOR_NUMBER * 16];
        byte[][][] sourceFileData = new byte[challengeData.indices.length][SECTOR_NUMBER][16];
        // 声明下载 tags 文件所需变量
        GetObjectRequest getTagsFileRequest = new GetObjectRequest(bucketName, cloudFileName + ".tags");
        long tagsStart, tagsEnd;
        byte[] tagsFileDataTemp = new byte[40];
        BigInteger[] tags = new BigInteger[BLOCK_NUMBER];
        // get source file and tags file data from cloud
        for (int i = 0; i < challengeData.indices.length; i++) {
            System.out.println(i);
            // source file
            blockStart = (long) challengeData.indices[i] * SECTOR_NUMBER * 16;
            blockEnd = (long) (challengeData.indices[i] + 1) * SECTOR_NUMBER * 16;
            getSourceFileRequest.setRange(blockStart, blockEnd - 1);
            cosObject = cosClient.getObject(getSourceFileRequest);
            cloudFileIn = cosObject.getObjectContent();
            Arrays.fill(sourceFileBlock, (byte) 0);
            try {
                for (int n = 0; n != -1; ) {
                    n = cloudFileIn.read(sourceFileBlock, 0, sourceFileBlock.length);
                }
                cloudFileIn.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (int j = 0; j < SECTOR_NUMBER; j++) {
                sourceFileData[i][j] = Arrays.copyOfRange(sourceFileBlock, j * 16, (j + 1) * 16);
                if (challengeData.indices[i] == 0 && j == 0 && sourceFileData[i][j][0] == 67) {
                    System.out.println("error");
                }
            }

            // tags file
            tagsStart = (long) challengeData.indices[i] * (40 + 2);  // 2 => "\r\n"
            tagsEnd = tagsStart + 40 - 1;
            getTagsFileRequest.setRange(tagsStart, tagsEnd);
            cosObject = cosClient.getObject(getTagsFileRequest);
            cloudFileIn = cosObject.getObjectContent();
            Arrays.fill(tagsFileDataTemp, (byte) 0);
            try {
                for (int n = 0; n != -1; ) {
                    n = cloudFileIn.read(tagsFileDataTemp, 0, tagsFileDataTemp.length);
                }
                cloudFileIn.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            tags[challengeData.indices[i]] = new BigInteger(new String(tagsFileDataTemp));
        }

        // calc Proof and return
        return cloudObjectStorageIntegrityChecking.prove(sourceFileData, tags, challengeData);
    }

    public static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(object);
        return byteArrayOutputStream.toByteArray();
    }

    public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
        return objectInputStream.readObject();
    }

    public static void show_help() {
        System.out.println("使用方法：\n" +
                "    java -jar server.jar [tempPath] [COSConfigFilePath]\n" +
                "其中：\n" +
                "    tempPath 是服务器用于暂存客户端上传来的文件的地方\n" +
                "    COSConfigFilePath 是保存 COS 密钥信息等的配置文件的路径\n");
    }

    public static void print(byte[] data) {
        for (int i = 0; i < 10; i++) {
            System.out.print(String.format("%02x ", data[i]));
        }
        System.out.println();
    }

    public static void print(byte[][] data) {
        if (data == null)
            return;
        for (int i = 0; i < 10; i++) {
            System.out.print(String.format("%02x ", data[0][i]));
        }
        System.out.println();
    }
}
