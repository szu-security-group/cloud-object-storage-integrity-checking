package com.fchen_group.CloudObjectStorageIntegrityChecking.Run;

import java.io.*;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Properties;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fchen_group.CloudObjectStorageIntegrityChecking.Core.CloudObjectStorageIntegrityChecking;
import com.fchen_group.CloudObjectStorageIntegrityChecking.Core.ChallengeData;
import com.fchen_group.CloudObjectStorageIntegrityChecking.Core.ProofData;
import com.fchen_group.CloudObjectStorageIntegrityChecking.Utils.CloudAPI;
import com.fchen_group.CloudObjectStorageIntegrityChecking.Utils.FileTransferProtocol;
import com.fchen_group.CloudObjectStorageIntegrityChecking.Utils.FileTransferProtocolDecoder;
import com.fchen_group.CloudObjectStorageIntegrityChecking.Utils.FileTransferProtocolEncoder;

public class Server {
    private static Logger logger = LoggerFactory.getLogger("server");
    private String pathPrefix;
    private CloudAPI cloudAPI;

    public static void main(String[] args) throws Exception {
        if (args.length == 2) {
            new Server(args[0], args[1]).run();
        } else {
            show_help();
        }
    }

    public Server(String pathPrefix, String COSConfigFilePath) {
        this.pathPrefix = pathPrefix;
        this.cloudAPI = new CloudAPI(COSConfigFilePath);
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
                        protected void initChannel(SocketChannel ch) {
                            // 添加自定义协议的编解码工具
                            ch.pipeline().addLast(new FileTransferProtocolEncoder());
                            ch.pipeline().addLast(new FileTransferProtocolDecoder());
                            // 处理服务器端操作
                            ch.pipeline().addLast(new ServerHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 1024) // 设置tcp缓冲区
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            logger.info("Start server and listen at port 9999.");
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
            String filePath;
            FileTransferProtocol fileTransferProtocol;
            FileTransferProtocol fileTransferProtocolReceived = (FileTransferProtocol) msg;

            String filename = (new File(new String(fileTransferProtocolReceived.filename))).getName();
            boolean needDelete = true;

            switch (fileTransferProtocolReceived.op) {
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
                    fileOutputStream.write(fileTransferProtocolReceived.content);
                    fileOutputStream.close();
                    logger.info("Receive {}.", filename);
                    // upload file
                    cloudAPI.uploadFile(pathPrefix + filename, filename);
                    logger.info("Upload {} to cloud.", filename);
                    // delete file
                    if (needDelete) {
                        file.delete();
                        logger.info("Delete {}", filename);
                    }

                    // send confirm
                    filePathBytes = new byte[filePathLength];
                    System.arraycopy(filename.getBytes(), 0, filePathBytes, 0, filePathLength);
                    filePath = new String(filePathBytes);
                    fileTransferProtocol = new FileTransferProtocol(fileTransferProtocolReceived.op, filePath.getBytes(), "".getBytes());
                    ctx.writeAndFlush(fileTransferProtocol);
                    break;

                case 3:
                    filePathLength = filename.length() - ".challenge".length();
                    filePathBytes = new byte[filePathLength];
                    System.arraycopy(filename.getBytes(), 0, filePathBytes, 0, filePathLength);
                    filePath = new String(filePathBytes);
                    logger.info("Receive audit request. Audit file is {}", filePath);
                    ChallengeData challengeData = (ChallengeData) deserialize(fileTransferProtocolReceived.content);
                    logger.info("Receive challenge data.");
                    logger.info("Start prove process.");
                    ProofData proofData = prove(filePath, challengeData, filePath);
                    logger.info("Finish prove process.");
                    String proofFilePath = pathPrefix + filePath + ".proof";
                    logger.info("Send proof data.");
                    fileTransferProtocol = new FileTransferProtocol(5, proofFilePath.getBytes(), serialize(proofData));
                    ctx.writeAndFlush(fileTransferProtocol);
                    break;

                default:
                    System.out.println("Invalid op");
            }
        }
    }

    public ProofData prove(String filePath, ChallengeData challengeData, String cloudFileName) throws Exception {
        String filename = filePath;
        filePath = pathPrefix + filename;
        String propertiesFilePath = filePath + ".properties";

        // get BLOCK_NUMBER and SECTOR_NUMBER
        logger.info("Get BLOCK_NUMBER and SECTOR_NUMBER from file {}.", propertiesFilePath);
        FileInputStream propertiesFIS = new FileInputStream(propertiesFilePath);
        Properties properties = new Properties();
        properties.load(propertiesFIS);
        propertiesFIS.close();
        int BLOCK_NUMBER = Integer.parseInt(properties.getProperty("BLOCK_NUMBER"));
        int SECTOR_NUMBER = Integer.parseInt(properties.getProperty("SECTOR_NUMBER"));
        logger.info("BLOCK_NUMBER: {}, SECTOR_NUMBER: {}.", BLOCK_NUMBER, SECTOR_NUMBER);
        CloudObjectStorageIntegrityChecking cloudObjectStorageIntegrityChecking = new CloudObjectStorageIntegrityChecking(filePath, BLOCK_NUMBER, SECTOR_NUMBER);

        // 声明下载源文件所需变量
        long blockStart;
        byte[] sourceFileBlock;
        byte[][][] sourceFileData = new byte[challengeData.indices.length][SECTOR_NUMBER][16];
        // 声明下载 tags 文件所需变量
        long tagsStart;
        byte[] tagsFileBlock;
        BigInteger[] tags = new BigInteger[BLOCK_NUMBER];
        // get source file and tags file data from cloud
        logger.info("Getting source file and tags file data from cloud.");
        for (int i = 0; i < challengeData.indices.length; i++) {
            if ((i + 1) % (challengeData.indices.length/10) == 0) {
                logger.info("Progress rate: {}%.", (i + 1) * 100 / challengeData.indices.length);
            }
            // source file
            blockStart = (long) challengeData.indices[i] * SECTOR_NUMBER * 16;
            sourceFileBlock = cloudAPI.downloadPartFile(cloudFileName, blockStart, SECTOR_NUMBER * 16);
            for (int j = 0; j < SECTOR_NUMBER; j++) {
                sourceFileData[i][j] = Arrays.copyOfRange(sourceFileBlock, j * 16, (j + 1) * 16);
                if (challengeData.indices[i] == 0 && j == 0 && sourceFileData[i][j][0] == 67) {
                    System.out.println("error");
                }
            }
            // tags file
            tagsStart = (long) challengeData.indices[i] * 17;
            tagsFileBlock = cloudAPI.downloadPartFile(cloudFileName + ".tags", tagsStart, 17);
            tags[challengeData.indices[i]] = new BigInteger(tagsFileBlock);
        }

        // calc Proof and return
        logger.info("Calculate proof.");
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
                "    java -jar server.jar [tempPath] [COSPropertiesPath]\n" +
                "其中：\n" +
                "    tempPath 是服务器用于暂存客户端上传来的文件的目录\n" +
                "    COSPropertiesPath 是保存 COS 密钥信息等的配置文件的路径\n");
    }
}
