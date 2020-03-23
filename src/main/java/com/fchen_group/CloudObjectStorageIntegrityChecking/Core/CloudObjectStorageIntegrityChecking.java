package com.fchen_group.CloudObjectStorageIntegrityChecking.Core;

import java.io.*;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
// import java.util.Properties;
import java.util.Random;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

// import com.qcloud.cos.COSClient;
// import com.qcloud.cos.ClientConfig;
// import com.qcloud.cos.auth.COSCredentials;
// import com.qcloud.cos.auth.BasicCOSCredentials;
// import com.qcloud.cos.model.COSObject;
// import com.qcloud.cos.region.Region;
// import com.qcloud.cos.model.GetObjectRequest;
// import com.javamex.classmexer.MemoryUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudObjectStorageIntegrityChecking extends AbstractAudit {
    private static Logger logger = LoggerFactory.getLogger("");
    private final BigInteger p = new BigInteger("100000000000000000000000000000033", 16);  // 2^128 + 51
    private int BLOCK_NUMBER;
    private int SECTOR_NUMBER;
    private final int sectorLen = 16;  // 16byes = 128bits
    private Mac mac;
    private String filePath;

    public CloudObjectStorageIntegrityChecking(String filePath, int SECTOR_NUMBER) {
        this(filePath, 0, SECTOR_NUMBER);
    }

    public CloudObjectStorageIntegrityChecking(String filePath, int BLOCK_NUMBER, int SECTOR_NUMBER) {
        logger = LoggerFactory.getLogger("newMain");

        this.filePath = filePath;
        this.BLOCK_NUMBER = BLOCK_NUMBER;
        this.SECTOR_NUMBER = SECTOR_NUMBER;  // if s = 64: sector(16B) * s = block(1KB)

        if (this.BLOCK_NUMBER == 0) {
            long sourceFileSize = (new File(filePath)).length();
            this.BLOCK_NUMBER = (int) Math.ceil(sourceFileSize * 1.0 / this.sectorLen / this.SECTOR_NUMBER);
        }
    }

    /**
     * Key generation function, executed only once
     */
    public Key keyGen() {
        logger = LoggerFactory.getLogger("keyGen");

        Key key = new Key("", new BigInteger[this.SECTOR_NUMBER]);

        // generate KeyPRF 16bytes = 128bit
        String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random KmacRandom = new Random();
        StringBuilder KeyPRF = new StringBuilder();
        for (int i = 0; i < 16; i++) {  // 16bytes = 128bit
            KeyPRF.append(chars.charAt(KmacRandom.nextInt(chars.length())));
        }
        key.KeyPRF = KeyPRF.toString();
        logger.debug("KeyPRF: {}", key.KeyPRF);

        // generate a
        Random random = new Random();
        for (int i = 0; i < this.SECTOR_NUMBER; i++) {
            key.a[i] = (new BigInteger(this.p.bitLength(), random)).mod(this.p);
            logger.debug("a[{}]: {}", i, key.a[i]);
        }

        return key;
    }

    /**
     * Error-correcting coding of the source data
     */
    public BigInteger[] outsource(Key key) {
        logger = LoggerFactory.getLogger("outsource");

        // 原文件𝑀分割为𝑛块大小为𝑠 sectors的block 𝑀 => sourceFileData
        // 对每一数据块，计算标签
        BigInteger[] tags = new BigInteger[this.BLOCK_NUMBER];
        try {
            FileInputStream in = new FileInputStream(filePath);
            byte[] sourceFileDataBlock = new byte[this.SECTOR_NUMBER * this.sectorLen];
            for (int i = 0; i < this.BLOCK_NUMBER; i++) {
                // 读取 1 个 block 大小的文件
                Arrays.fill(sourceFileDataBlock, (byte) 0);
                if (in.read(sourceFileDataBlock) == -1) {
                    logger.info("Read file complete");
                }

                BigInteger am = new BigInteger("0");
                for (int j = 0; j < this.SECTOR_NUMBER; j++) {
                    byte[] sourceFileDataSector = Arrays.copyOfRange(sourceFileDataBlock, j * this.sectorLen, (j + 1) * this.sectorLen);
                    BigInteger mij = new BigInteger(byteToBit(sourceFileDataSector), 2);
                    am = am.add(key.a[j].multiply(mij).mod(this.p)).mod(this.p);

                    // logger.debug("a[{}]: {}", j, this.a[j]);
                    // logger.debug("m[{}][{}]: {}", i, j, mij);
                    // logger.debug("am: {}", am);
                }
                tags[i] = funcPRF(key, i).add(am).mod(this.p);
                // logger.debug("funcPRF({}): {}", i, funcPRF(i));
                // logger.debug("am: {}", am);
                // logger.debug("this.o[{}]: {}", i, this.o[i]);
            }
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return tags;
    }

    private BigInteger funcPRF(Key key, int index) {
        // prepare PRF function
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacMD5");  // output of MD5 is 128bit
            keyGenerator.init(new SecureRandom(key.KeyPRF.getBytes()));
            SecretKey secretKey = keyGenerator.generateKey();
            this.mac = Mac.getInstance("HmacMD5");
            this.mac.init(secretKey);
        } catch (Exception e) {
            e.printStackTrace();
        }

        byte[] result = this.mac.doFinal(Integer.toString(index).getBytes());
        return new BigInteger(byteToBit(result), 2);
    }

    /**
     * Generate audit indexes and coefficients based on challenge length
     */
    public ChallengeData audit(int challengeLen) {
        logger = LoggerFactory.getLogger("audit");
        logger.debug("START");

        Random random = new Random();
        // 随机选择 𝑙 个数据块索引 𝑖 和随机系数 vi
        ChallengeData challengeData = new ChallengeData(new int[challengeLen], new BigInteger[challengeLen]);
        for (int i = 0; i < challengeLen; i++) {
            challengeData.indices[i] = random.nextInt(this.BLOCK_NUMBER);
            challengeData.coefficients[i] = (new BigInteger(this.p.bitLength(), random)).mod(this.p);
            // logger.debug("challengeData.indices[{}]: {}", i, challengeData.indices[i]);
            // logger.debug("challengeData.coefficients[{}]: {}", i, challengeData.coefficients[i]);
        }

        logger.debug("END");
        return challengeData;
    }

    /**
     * When the server receives the chal, it calculates the corresponding Proof
     */
    public ProofData prove(BigInteger[] tags, ChallengeData challengeData) {
        byte[][][] sourceFileData = new byte[challengeData.indices.length][this.SECTOR_NUMBER][this.sectorLen];
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(this.filePath, "r");
            for (int i = 0; i < challengeData.indices.length; i++) {
                randomAccessFile.seek(((long) challengeData.indices[i] * this.SECTOR_NUMBER) * this.sectorLen);
                for (int j = 0; j < this.SECTOR_NUMBER; j++) {
                    randomAccessFile.read(sourceFileData[i][j]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this.prove(sourceFileData, tags, challengeData);
    }

    public ProofData prove(byte[][][] sourceFileData, BigInteger[] tags, ChallengeData challengeData) {
        logger = LoggerFactory.getLogger("prove");
        logger.debug("START");

        ProofData proofData = new ProofData(new BigInteger[this.SECTOR_NUMBER], new BigInteger("0"));
        // calc u
        proofData.u = new BigInteger[this.SECTOR_NUMBER];
        for (int j = 0; j < this.SECTOR_NUMBER; j++) {
            proofData.u[j] = new BigInteger("0");
            for (int i = 0; i < challengeData.indices.length; i++) {
                BigInteger mij = new BigInteger(byteToBit(sourceFileData[i][j]), 2);
                proofData.u[j] = proofData.u[j].add(challengeData.coefficients[i].multiply(mij).mod(this.p)).mod(this.p);
            }
            // logger.debug("this.u[{}]: {}", j, this.u[j]);
        }
        // calc o
        for (int i = 0; i < challengeData.indices.length; i++) {
            proofData.o = proofData.o.add(challengeData.coefficients[i].multiply(tags[challengeData.indices[i]]).mod(this.p)).mod(this.p);
        }
        logger.debug("server_o: {}", proofData.o);

        logger.debug("END");
        return proofData;
    }

    /**
     * Verify the correctness of proof returned from the cloud
     */
    public boolean verify(Key key, ChallengeData challengeData, ProofData proofData) {
        logger = LoggerFactory.getLogger("verify");
        logger.debug("START");

        BigInteger sumOFvf = new BigInteger("0");
        for (int i = 0; i < challengeData.indices.length; i++) {
            sumOFvf = sumOFvf.add(challengeData.coefficients[i].multiply(funcPRF(key, challengeData.indices[i])).mod(this.p)).mod(this.p);
        }

        BigInteger sumOFau = new BigInteger("0");
        for (int j = 0; j < this.SECTOR_NUMBER; j++) {
            sumOFau = sumOFau.add(key.a[j].multiply(proofData.u[j]).mod(this.p)).mod(this.p);
        }

        BigInteger new_o = sumOFvf.add(sumOFau).mod(this.p);
        logger.debug("new_o: {}", new_o);

        logger.debug("END");
        return new_o.equals(proofData.o);
    }

    public static String byteToBit(byte[] bytes) {
        StringBuilder binary = new StringBuilder();
        for (byte b : bytes) {
            binary.append((b >> 7) & 0x1).
                    append((b >> 6) & 0x1).
                    append((b >> 5) & 0x1).
                    append((b >> 4) & 0x1).
                    append((b >> 3) & 0x1).
                    append((b >> 2) & 0x1).
                    append((b >> 1) & 0x1).
                    append((b) & 0x1);
        }
        return binary.toString();
    }

    public int getBLOCK_NUMBER() {
        return BLOCK_NUMBER;
    }

    public int getSECTOR_NUMBER() {
        return SECTOR_NUMBER;
    }
}
