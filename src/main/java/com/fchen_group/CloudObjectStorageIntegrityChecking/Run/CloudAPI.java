package com.fchen_group.CloudObjectStorageIntegrityChecking.Run;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class CloudAPI {
    private COSClient cosClient;
    private String bucketName;

    public CloudAPI(String COSConfigFilePath) {
        String COSSecretId = null;
        String COSSecretKey = null;
        String regionName = null;

        // 从配置文件获取 COSSecretId, COSSecretKey
        try {
            FileInputStream propertiesFIS = new FileInputStream(COSConfigFilePath);
            Properties properties = new Properties();
            properties.load(propertiesFIS);
            propertiesFIS.close();
            COSSecretId = properties.getProperty("secretId");
            COSSecretKey = properties.getProperty("secretKey");
            regionName = properties.getProperty("region");
            bucketName = properties.getProperty("bucket");
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 检查变量是否为空
        assert COSSecretId != null;
        assert COSSecretKey != null;
        assert regionName != null;
        assert bucketName != null;

        // 初始化 cos 客户端
        COSCredentials cosCredentials = new BasicCOSCredentials(COSSecretId, COSSecretKey);
        Region region = new Region(regionName);
        ClientConfig clientConfig = new ClientConfig(region);
        cosClient = new COSClient(cosCredentials, clientConfig);
    }

    /**
     * 将云端文件下载到本地
     * @param localFileName 本地文件路径
     * @param cloudFileName 云端文件路径
     */
    public void uploadFile(String localFileName, String cloudFileName) {
        File localFile = new File(localFileName);
        PutObjectResult putObjectResult = cosClient.putObject(bucketName, cloudFileName, localFile);
    }

    /**
     * 下载部分文件数据
     * @param cloudFileName 云端文件名字
     * @param startPos 起始位置
     * @param length 数据的长度
     * @return 从起始位置（包含）开始，总计长度为 length 的 byte[] 数据
     */
    public byte[] downloadPartFile(String cloudFileName, long startPos, int length) {
        // initialization
        COSObject cosObject;
        InputStream cloudFileIn;
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, cloudFileName);
        byte[] fileBlock = new byte[length];

        // download file
        getObjectRequest.setRange(startPos, startPos + length - 1);
        cosObject = cosClient.getObject(getObjectRequest);
        cloudFileIn = cosObject.getObjectContent();
        try {
            for (int n = 0; n != -1; ) {
                n = cloudFileIn.read(fileBlock, 0, fileBlock.length);
            }
            cloudFileIn.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return fileBlock;
    }
}
