package com.fchen_group.CloudObjectStorageIntegrityChecking.main;

import java.io.Serializable;
import java.math.BigInteger;

public class Key implements Serializable {
    private static final long serialVersionUID = 8074523633229932986L;
    public String KeyPRF;
    public BigInteger[] a;

    public Key(String KeyPRF, BigInteger[] a) {
        this.KeyPRF = KeyPRF;
        this.a = a;
    }
}
