package com.fchen_group.CloudObjectStorageIntegrityChecking.Core;

import java.math.BigInteger;

/**
 * An abstract class for Audit.
 * Class Audit should inherit this class and implement its five function.
 */
public abstract class AbstractAudit {
    public abstract Key keyGen();
    public abstract BigInteger[] outsource(Key key);
    public abstract ChallengeData audit(int challengeLen);
    public abstract ProofData prove(BigInteger[] tags, ChallengeData challengeData);
    public abstract boolean verify(Key key, ChallengeData challengeData, ProofData proofData);
}
