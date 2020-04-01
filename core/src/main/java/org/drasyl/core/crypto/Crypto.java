/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.core.crypto;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.jcajce.provider.asymmetric.util.EC5Util;
import org.bouncycastle.jce.ECPointUtil;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.math.ec.ECCurve;

import java.math.BigInteger;
import java.security.Signature;
import java.security.*;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;

public class Crypto {
    private static final String PROVIDER = "BC";
    private static final String ECDSA = "ECDSA";
    private static final String SHA256_WITH_ECDSA = "SHA256withECDSA";
    private static final String CURVE_NAME = "curve25519";
    private static SecureRandom random = new SecureRandom();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static KeyPair generateKeys() {
        try {
            X9ECParameters ecP = CustomNamedCurves.getByName(CURVE_NAME);
            ECParameterSpec ecSpec = new ECParameterSpec(ecP.getCurve(), ecP.getG(), ecP.getN(), ecP.getH(), ecP.getSeed());

            KeyPairGenerator keygen = KeyPairGenerator.getInstance(ECDSA, PROVIDER);
            keygen.initialize(ecSpec, new SecureRandom());
            return keygen.generateKeyPair();
        }
        catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            throw new InternalError("Cannot Generate Keys");
        }
    }

    public static KeyPair makeKeyPair(byte[] compressedPrivate,
                                      byte[] compressedPublic) throws CryptoException {
        return new KeyPair(getPublicKeyFromBytes(compressedPublic), getPrivateKeyFromBytes(compressedPrivate));
    }

    public static ECPrivateKey parseCompressedPrivateKey(byte[] compressedPrivateKey) throws CryptoException {
        X9ECParameters ecP = CustomNamedCurves.getByName(CURVE_NAME);
        ECParameterSpec ecSpec = new ECParameterSpec(ecP.getCurve(), ecP.getG(), ecP.getN(), ecP.getH(), ecP.getSeed());
        ECPrivateKeySpec privkeyspec = new ECPrivateKeySpec(new BigInteger(compressedPrivateKey), ecSpec);
        try {
            return (ECPrivateKey) KeyFactory.getInstance(ECDSA, PROVIDER).generatePrivate(privkeyspec);
        }
        catch (Exception e) {
            throw new CryptoException(e);
        }
    }

    public static ECPublicKey parseCompressedPublicKey(byte[] compressedPubKey) throws CryptoException {
        try {
            X9ECParameters ecP = CustomNamedCurves.getByName(CURVE_NAME);

            KeyFactory fact = KeyFactory.getInstance(ECDSA, PROVIDER);
            ECCurve curve = ecP.getCurve();
            java.security.spec.EllipticCurve ellipticCurve = EC5Util.convertCurve(curve, ecP.getSeed());

            java.security.spec.ECParameterSpec ecSpec = EC5Util.convertToSpec(ecP);
            java.security.spec.ECPoint point = ECPointUtil.decodePoint(ellipticCurve, compressedPubKey);
            java.security.spec.ECPublicKeySpec keySpec = new java.security.spec.ECPublicKeySpec(point, ecSpec);
            return (ECPublicKey) fact.generatePublic(keySpec);
        }
        catch (Exception e) {
            throw new CryptoException(e);
        }
    }

    public static byte[] compressedKey(PublicKey key) throws CryptoException {
        if (key instanceof ECPublicKey) {
            return ((ECPublicKey) key).getQ().getEncoded(true);
        }
        throw new CryptoException(new IllegalArgumentException("Can only compress ECPublicKey"));
    }

    public static byte[] compressedKey(PrivateKey privkey) throws CryptoException {
        if (privkey instanceof ECPrivateKey) {
            return ((ECPrivateKey) privkey).getD().toByteArray();
        }
        throw new CryptoException(new IllegalArgumentException("Can only compress ECPrivateKey"));
    }

    public static byte[] signMessage(PrivateKey key, byte[] message) throws CryptoException {
        try {
            Signature ecdsaSign = Signature.getInstance(SHA256_WITH_ECDSA, PROVIDER);
            ecdsaSign.initSign(key);
            ecdsaSign.update(message);
            return ecdsaSign.sign();
        }
        catch (NoSuchAlgorithmException | NoSuchProviderException | SignatureException | InvalidKeyException e) {
            throw new CryptoException(e);
        }
    }

    /**
     * Signs the given signable with the Privatekey. This will also put the resulting signature into
     * the Signable object
     *
     * @param key      Key to use
     * @param signable signature to create
     * @throws CryptoException on failure
     */
    public static void sign(PrivateKey key, Signable signable) throws CryptoException {
        byte[] signatureBytes = signMessage(key, signable.getSignableBytes());
        signable.setSignature(new org.drasyl.core.crypto.Signature(signatureBytes));
    }

    private static ECPublicKeySpec getKeySpec(byte[] pubOrPrivKey) {
        X9ECParameters ecP = CustomNamedCurves.getByName(CURVE_NAME);
        ECParameterSpec ecSpec = new ECParameterSpec(ecP.getCurve(), ecP.getG(), ecP.getN(), ecP.getH(), ecP.getSeed());
        ECNamedCurveSpec params = new ECNamedCurveSpec(CURVE_NAME, ecSpec.getCurve(), ecSpec.getG(), ecSpec.getN(), ecSpec.getH(), ecSpec.getSeed());
        java.security.spec.ECParameterSpec actualParams = new java.security.spec.ECParameterSpec(params.getCurve(), params.getGenerator(), params.getOrder(), params.getCofactor());
        ECPoint point = ECPointUtil.decodePoint(actualParams.getCurve(), pubOrPrivKey);
        return new ECPublicKeySpec(point, actualParams);
    }

    public static PrivateKey getPrivateKeyFromBytes(byte[] privKey) throws CryptoException {
        if (privKey.length <= 33) {
            return parseCompressedPrivateKey(privKey);
        }
        else {
            try {
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privKey);
                KeyFactory factory = KeyFactory.getInstance(ECDSA, PROVIDER);
                return factory.generatePrivate(spec);
            }
            catch (Exception e) {
                throw new CryptoException("Could not parse Key: " + HexUtil.toString(privKey), e);
            }
        }
    }

    public static PublicKey getPublicKeyFromBytes(byte[] pubKey) throws CryptoException {
        if (pubKey.length <= 33) {
            return parseCompressedPublicKey(pubKey);
        }
        else {
            try {
                KeyFactory kf = KeyFactory.getInstance(ECDSA, PROVIDER);
                return kf.generatePublic(getKeySpec(pubKey));
            }
            catch (Exception e) {
                throw new CryptoException("Could not parse Key: " + HexUtil.toString(pubKey), e);
            }
        }
    }

    public static boolean verifySignature(byte[] compressedPublicKey,
                                          byte[] message,
                                          byte[] signature) {
        try {
            PublicKey publicKey = getPublicKeyFromBytes(compressedPublicKey);
            return verifySignature(publicKey, message, signature);
        }
        catch (CryptoException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean verifySignature(PublicKey pubkey, byte[] message, byte[] signature) {
        try {
            Signature ecdsaVerify = Signature.getInstance(SHA256_WITH_ECDSA, PROVIDER);
            ecdsaVerify.initVerify(pubkey);
            ecdsaVerify.update(message);
            return ecdsaVerify.verify(signature);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean verifySignature(PublicKey publicKey, Signable content) {
        if (content == null || content.getSignature() == null) {
            return false;
        }
        return verifySignature(publicKey, content.getSignableBytes(), content.getSignature().getBytes());
    }
}
