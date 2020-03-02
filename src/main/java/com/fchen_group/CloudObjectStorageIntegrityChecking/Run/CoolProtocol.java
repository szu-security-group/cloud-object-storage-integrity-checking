package com.fchen_group.CloudObjectStorageIntegrityChecking.Run;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class CoolProtocol {
    public static int magicNumber = 329;

    public int op;
    public int filenameLength;
    public int contentLength;
    public byte[] filename;
    public byte[] content;

    public CoolProtocol(int op, byte[] filename) {
        this.op = op;
        this.filenameLength = filename.length;
        this.filename = filename;

        try {
            File file = new File(new String(filename));
            FileInputStream fileInputStream = new FileInputStream(file);
            this.content = new byte[(int) file.length()];
            this.contentLength = fileInputStream.read(this.content);
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public CoolProtocol(int op, byte[] filename, byte[] content) {
        this.op = op;
        this.filenameLength = filename.length;
        this.filename = filename;
        this.contentLength = content.length;
        this.content = content;
    }

    @Override
    public String toString() {
        return "SmartCarProtocol{" +
                "filenameLength=" + filenameLength +
                ", contentLength=" + contentLength +
                ", filename=" + new String(filename) +
                ", content=" + new String(content) +
                '}';
    }
}
