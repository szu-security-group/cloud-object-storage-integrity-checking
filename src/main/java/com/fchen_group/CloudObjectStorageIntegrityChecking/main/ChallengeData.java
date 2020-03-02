package com.fchen_group.CloudObjectStorageIntegrityChecking.main;

import java.io.Serializable;
import java.math.BigInteger;

public class ChallengeData implements Serializable {
    private static final long serialVersionUID = 8074523611235693986L;
    public int[] indices;
    public BigInteger[] coefficients;

    public ChallengeData(int[] indices, BigInteger[] coefficients) {
        this.indices = indices;
        this.coefficients = coefficients;
    }
}
