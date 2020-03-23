package com.fchen_group.CloudObjectStorageIntegrityChecking.Core;

import java.io.Serializable;
import java.math.BigInteger;

/**
 * Class ChallengeData is used to store challenge data.
 * Challenge data include the indices
 * and the coefficients that correspond to the challenged data block.
 *
 * This class is serializable so that it can be easily stored and transferred.
 */
public class ChallengeData implements Serializable {
    private static final long serialVersionUID = 8074523611235693986L;
    public int[] indices;
    public BigInteger[] coefficients;

    public ChallengeData(int[] indices, BigInteger[] coefficients) {
        this.indices = indices;
        this.coefficients = coefficients;
    }
}
