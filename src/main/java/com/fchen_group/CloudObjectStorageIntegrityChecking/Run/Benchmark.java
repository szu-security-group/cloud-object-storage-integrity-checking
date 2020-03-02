package com.fchen_group.CloudObjectStorageIntegrityChecking.Run;

import com.fchen_group.CloudObjectStorageIntegrityChecking.main.CloudObjectStorageIntegrityChecking;
import com.fchen_group.CloudObjectStorageIntegrityChecking.main.ChallengeData;
import com.fchen_group.CloudObjectStorageIntegrityChecking.main.Key;
import com.fchen_group.CloudObjectStorageIntegrityChecking.main.ProofData;
import com.javamex.classmexer.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;

public class Benchmark {
    private String filename;
    private String filePath;

    // we run the performance
    // evaluation for such times and
    // then average the result.
    public final static int LOOP_TIMES = 10;

    public static void main(String[] args) throws IOException {
        String filepath = args[0];
        (new Benchmark(filepath)).run();
    }

    public Benchmark(String filepath) {
        this.filePath = filepath;
        this.filename = (new File(filepath)).getName();
    }

    /**
     * This is the main function to evaluate the performance.
     */
    public void run() throws IOException {
        randomizedAudit();
    }

    private void randomizedAudit() throws IOException {
        long storage = 0;
        long communication = 0;
        long[] time = new long[5];   // 0: key generation; 1: outsource; 2: audit; 3: prove;4: verify.
        long startTime = 0, endTime = 0;

        //Initializes an object
        CloudObjectStorageIntegrityChecking cloudObjectStorageIntegrityChecking = new CloudObjectStorageIntegrityChecking(this.filePath, 64);

        // keyGen
        startTime = System.nanoTime();
        Key key = cloudObjectStorageIntegrityChecking.keyGen();
        endTime = System.nanoTime();
        time[0] = endTime - startTime;

        // outsource
        // System.out.println("\n-----Outsource-----\n");
        startTime = System.nanoTime();
        BigInteger[] tags = cloudObjectStorageIntegrityChecking.outsource(key);
        endTime = System.nanoTime();
        time[1] = endTime - startTime;

        //Carry a large integer space
        storage = MemoryUtil.deepMemoryUsageOf(tags);

        // System.out.println(".....Auditting.....\n");

        boolean b;
        int count = 0, challengeLen = 460;
        long auditTime, proveTime, verifyTime, sumTime, singleCommunication;

        for (int i = 0; i < LOOP_TIMES; i++) {
            ChallengeData c;
            startTime = System.nanoTime();
            ChallengeData challengeData = cloudObjectStorageIntegrityChecking.audit(challengeLen);
            System.out.println(MemoryUtil.deepMemoryUsageOf(challengeData.indices)
                    + MemoryUtil.deepMemoryUsageOf(challengeData.coefficients));
            endTime = System.nanoTime();
            auditTime = endTime - startTime;
            time[2] = time[2] + (endTime - startTime);

            ProofData proof;
            startTime = System.nanoTime();
            ProofData proofData = cloudObjectStorageIntegrityChecking.prove(tags, challengeData);
            endTime = System.nanoTime();
            proveTime = endTime - startTime;
            time[3] = time[3] + (endTime - startTime);
            //The cost of communication
            singleCommunication = MemoryUtil.deepMemoryUsageOf(proofData.u) + MemoryUtil.deepMemoryUsageOf(proofData.o);
            communication = communication + MemoryUtil.deepMemoryUsageOf(proofData);

            startTime = System.nanoTime();
            b = cloudObjectStorageIntegrityChecking.verify(key, challengeData, proofData);
            endTime = System.nanoTime();
            verifyTime = endTime - startTime;
            time[4] = time[4] + (endTime - startTime);

            sumTime = auditTime + proveTime + verifyTime;
            System.out.printf("%s 第%d次,%d,%d,%d,%d,%d,%d\n", this.filename, i + 1, auditTime, proveTime, verifyTime, sumTime, storage, singleCommunication);

            if (!b)
                count++;
        }

        time[2] = (time[2] / LOOP_TIMES);
        time[3] = (time[3] / LOOP_TIMES);
        time[4] = (time[4] / LOOP_TIMES);
        communication = (communication / LOOP_TIMES);

        System.out.printf("%s %d次平均,%d,%d,%d,%d,%d,%d\n", this.filename, LOOP_TIMES, time[2], time[3], time[4], time[2] + time[3] + time[4], storage, communication);

        System.out.println("verification error: " + count);
    }
}
