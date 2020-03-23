package com.fchen_group.CloudObjectStorageIntegrityChecking.Core;

import java.io.Serializable;
import java.math.BigInteger;

public class ProofData implements Serializable {
	private static final long serialVersionUID = 8074523617533993986L;
	public BigInteger[] u;
	public BigInteger o;

	public ProofData(BigInteger[] u, BigInteger o) {
		this.u = u;
		this.o = o;
	}
}
