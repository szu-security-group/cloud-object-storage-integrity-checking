package com.fchen_group.CloudObjectStorageIntegrityChecking.Core;

import java.io.Serializable;
import java.math.BigInteger;

/**
 * Class ProofData is used to store proof data.
 * Proof include an aggregated block u and a big integer o.
 * The cloud combined the challenged data blocks linearly using the query q to obtain an aggregated block u.
 * The cloud also combines the authentication tags for the challenged blocks to obtain o.
 *
 * This class is serializable so that it can be easily stored and transferred.
 */
public class ProofData implements Serializable {
	private static final long serialVersionUID = 8074523617533993986L;
	public BigInteger[] u;
	public BigInteger o;

	public ProofData(BigInteger[] u, BigInteger o) {
		this.u = u;
		this.o = o;
	}
}
