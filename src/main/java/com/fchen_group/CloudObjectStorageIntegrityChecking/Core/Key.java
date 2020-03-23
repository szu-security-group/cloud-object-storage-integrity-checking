package com.fchen_group.CloudObjectStorageIntegrityChecking.Core;

import java.io.Serializable;
import java.math.BigInteger;

/**
 * Class Key is used to store key data.
 * The key is a combination of the random key K for the pseudorandom function
 * and s random elements {α1, ..., αs} which belongs to Zp.
 *
 * This class is serializable so that it can be easily stored and transferred.
 */
public class Key implements Serializable {
    private static final long serialVersionUID = 8074523633229932986L;
    public String KeyPRF;
    public BigInteger[] a;

    public Key(String KeyPRF, BigInteger[] a) {
        this.KeyPRF = KeyPRF;
        this.a = a;
    }
}
