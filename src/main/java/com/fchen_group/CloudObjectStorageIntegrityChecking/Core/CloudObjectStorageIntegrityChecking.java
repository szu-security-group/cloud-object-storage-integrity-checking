package com.fchen_group.CloudObjectStorageIntegrityChecking.Core;

import java.io.*;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudObjectStorageIntegrityChecking extends AbstractAudit {
    private static Logger logger = LoggerFactory.getLogger("");
    private final BigInteger p = new BigInteger("100000000000000000000000000000033", 16);  // 2^128 + 51
    private int BLOCK_NUMBER;
    private int SECTOR_NUMBER;
    private final int sectorLen = 16;  // 16bytes = 128bits
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
     * Key generation function, executed only once.
     * @return the generated key
     */
    public Key keyGen() {
        logger = LoggerFactory.getLogger("keyGen");

        Key key = new Key("", new BigInteger[this.SECTOR_NUMBER]);

        // generate KeyPRF
        String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random KmacRandom = new Random();
        StringBuilder KeyPRF = new StringBuilder();
        for (int i = 0; i < 16; i++) {  // 16bytes = 128bit
            KeyPRF.append(chars.charAt(KmacRandom.nextInt(chars.length())));
        }
        key.KeyPRF = KeyPRF.toString();
        logger.debug("KeyPRF: {}", key.KeyPRF);

        // generate s random elements {α1, ..., αs}
        Random random = new Random();
        for (int i = 0; i < this.SECTOR_NUMBER; i++) {
            key.a[i] = (new BigInteger(this.p.bitLength(), random)).mod(this.p);
            logger.debug("a[{}]: {}", i, key.a[i]);
        }

        return key;
    }

    /**
     * Calculate the tags of the source data.
     * @param key : the key generated in keyGen
     * @return tags of the source data
     */
    public BigInteger[] outsource(Key key) {
        logger = LoggerFactory.getLogger("outsource");

        // Divide the original file M into n blocks. Each block is further divided into s sectors.
        // For each blocks, calculate its tag.
        BigInteger[] tags = new BigInteger[this.BLOCK_NUMBER];
        try {
            FileInputStream in = new FileInputStream(filePath);
            byte[] sourceFileDataBlock = new byte[this.SECTOR_NUMBER * this.sectorLen];
            for (int i = 0; i < this.BLOCK_NUMBER; i++) {
                // Read a 1 block size data of source file data
                Arrays.fill(sourceFileDataBlock, (byte) 0);
                if (in.read(sourceFileDataBlock) == -1) {
                    logger.info("Read file complete");
                }

                // calculate tag for this block
                BigInteger am = new BigInteger("0");
                for (int j = 0; j < this.SECTOR_NUMBER; j++) {
                    byte[] sourceFileDataSector = Arrays.copyOfRange(sourceFileDataBlock, j * this.sectorLen, (j + 1) * this.sectorLen);
                    BigInteger mij = new BigInteger(byteToBit(sourceFileDataSector), 2);
                    am = am.add(key.a[j].multiply(mij).mod(this.p)).mod(this.p);
                }
                tags[i] = funcPRF(key, i).add(am).mod(this.p);
            }
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return tags;
    }

    /**
     * The pseudorandom function to generate random number.
     * @param key : the key generated in keyGen
     * @param index : index of the block in the source data
     * @return a pseudorandom number
     */
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

        // generate pseudorandom number
        byte[] result = this.mac.doFinal(Integer.toString(index).getBytes());
        return new BigInteger(byteToBit(result), 2);
    }

    /**
     * Generate audit indexes and coefficients based on challenge length.
     * @param challengeLen : length of challenge (default is 460)
     * @return challenge which will be sent to sever
     */
    public ChallengeData audit(int challengeLen) {
        Random random = new Random();
        // Randomly select l indices of source data block
        // and the coefficients that correspond to the challenged data block.
        ChallengeData challengeData = new ChallengeData(new int[challengeLen], new BigInteger[challengeLen]);
        for (int i = 0; i < challengeLen; i++) {
            challengeData.indices[i] = random.nextInt(this.BLOCK_NUMBER);
            challengeData.coefficients[i] = (new BigInteger(this.p.bitLength(), random)).mod(this.p);
        }
        return challengeData;
    }

    /**
     * Read source file blocks from file in local and call the real prove function.
     *
     * !! This function is only called in benchmark !!
     *
     * @param tags : tag blocks which indices are in challenge
     * @param challengeData : challenge sent by client
     * @return proof calculated by server
     */
    public ProofData prove(BigInteger[] tags, ChallengeData challengeData) {
        // read file from local storage
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

        // call the real prove function
        return this.prove(sourceFileData, tags, challengeData);
    }

    /**
     * When the server receives the challenge, it calculates the corresponding Proof.
     * @param sourceFileData : source file blocks which indices are in challenge
     * @param tags : tag blocks which indices are in challenge
     * @param challengeData : challenge sent by client
     * @return proof calculated by server
     */
    public ProofData prove(byte[][][] sourceFileData, BigInteger[] tags, ChallengeData challengeData) {
        ProofData proofData = new ProofData(new BigInteger[this.SECTOR_NUMBER], new BigInteger("0"));
        // calculate u of proof
        proofData.u = new BigInteger[this.SECTOR_NUMBER];
        for (int j = 0; j < this.SECTOR_NUMBER; j++) {
            proofData.u[j] = new BigInteger("0");
            for (int i = 0; i < challengeData.indices.length; i++) {
                BigInteger mij = new BigInteger(byteToBit(sourceFileData[i][j]), 2);
                proofData.u[j] = proofData.u[j].add(challengeData.coefficients[i].multiply(mij).mod(this.p)).mod(this.p);
            }
        }
        // calculate o of proof
        for (int i = 0; i < challengeData.indices.length; i++) {
            proofData.o = proofData.o.add(challengeData.coefficients[i].multiply(tags[challengeData.indices[i]]).mod(this.p)).mod(this.p);
        }

        return proofData;
    }

    /**
     * Verify the correctness of proof returned from the cloud.
     * @param key : the key generated in keyGen
     * @param challengeData : proof calculated by client
     * @param proofData : proof calculated by server
     * @return verify result in boolean
     */
    public boolean verify(Key key, ChallengeData challengeData, ProofData proofData) {
        BigInteger sumOFvf = new BigInteger("0");
        for (int i = 0; i < challengeData.indices.length; i++) {
            sumOFvf = sumOFvf.add(challengeData.coefficients[i].multiply(funcPRF(key, challengeData.indices[i])).mod(this.p)).mod(this.p);
        }

        BigInteger sumOFau = new BigInteger("0");
        for (int j = 0; j < this.SECTOR_NUMBER; j++) {
            sumOFau = sumOFau.add(key.a[j].multiply(proofData.u[j]).mod(this.p)).mod(this.p);
        }

        BigInteger new_o = sumOFvf.add(sumOFau).mod(this.p);
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
