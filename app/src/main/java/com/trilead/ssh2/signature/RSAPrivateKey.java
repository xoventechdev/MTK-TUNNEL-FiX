package com.trilead.ssh2.signature;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;

public class RSAPrivateKey
{
	private BigInteger d;
	private BigInteger e;
	private BigInteger n;

	public RSAPrivateKey(BigInteger d, BigInteger e, BigInteger n)
	{
		this.d = d;
		this.e = e;
		this.n = n;
	}

	public BigInteger getD()
	{
		return d;
	}
	
	public BigInteger getE()
	{
		return e;
	}

	public BigInteger getN()
	{
		return n;
	}
	
	public RSAPublicKey getPublicKey()
	{
		return new RSAPublicKey(e, n);
	}

	/**
	 * Converts this to a JCE API representation of the RSA key pair.
	 *
	 * @return the key pair
	 * @throws GeneralSecurityException the general security exception
	 */
	public KeyPair toJCEKeyPair() throws GeneralSecurityException {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return new KeyPair(
                kf.generatePublic(new RSAPublicKeySpec(getN(), getE())),
                kf.generatePrivate(new RSAPrivateKeySpec(getN(), getD())));
    }
}
