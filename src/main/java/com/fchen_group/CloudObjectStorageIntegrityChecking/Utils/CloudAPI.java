package com.fchen_group.CloudObjectStorageIntegrityChecking.Utils;

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

/**
 * Provide a unified interface to access the cloud object store.
 * Class CloudAPI has two function:
 *     uploadFile => upload the whole file to cloud object store
 *     downloadPartFile => download a part of file from cloud object store
 */
public class CloudAPI {
    private COSClient cosClient;
    private String bucketName;

    /**
     * Initial cloud object store client.
     * @param COSConfigFilePath the path of file which store the configuration of cloud object store
     */
    public CloudAPI(String COSConfigFilePath) {
        String COSSecretId = null;
        String COSSecretKey = null;
        String regionName = null;

        // read configuration file
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

        // check if variables are null
        assert COSSecretId != null;
        assert COSSecretKey != null;
        assert regionName != null;
        assert bucketName != null;

        // initial cloud object store client
        COSCredentials cosCredentials = new BasicCOSCredentials(COSSecretId, COSSecretKey);
        Region region = new Region(regionName);
        ClientConfig clientConfig = new ClientConfig(region);
        cosClient = new COSClient(cosCredentials, clientConfig);
    }

    /**
     * upload file to cloud
     * @param localFileName the path of local file
     * @param cloudFileName the path of cloud file
     */
    public void uploadFile(String localFileName, String cloudFileName) {
        File localFile = new File(localFileName);
        PutObjectResult putObjectResult = cosClient.putObject(bucketName, cloudFileName, localFile);
    }

    /**
     * download partial file from cloud
     * @param cloudFileName the path of cloud file
     * @param startPos the start position of the partial file
     * @param length the length of the partial file
     * @return a byte array containing part of the file
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
