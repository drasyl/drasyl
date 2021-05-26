/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.crypto;

import com.google.common.primitives.UnsignedBytes;
import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;
import com.goterl.lazysodium.exceptions.SodiumException;
import com.goterl.lazysodium.interfaces.AEAD;
import com.goterl.lazysodium.interfaces.Sign;
import com.goterl.lazysodium.utils.SessionPair;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.IdentitySecretKey;
import org.drasyl.identity.Key;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.identity.KeyAgreementSecretKey;
import org.drasyl.identity.KeyPair;
import org.drasyl.identity.PublicKey;
import org.drasyl.remote.protocol.Nonce;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Util class that provides cryptography functions for drasyl.
 */
public class Crypto {
    public static final Crypto INSTANCE;
    public static final SecureRandom CSPRNG;
    public static final short PK_LONG_TIME_KEY_LENGTH = Sign.PUBLICKEYBYTES;
    public static final short SK_LONG_TIME_KEY_LENGTH = Sign.SECRETKEYBYTES;
    public static final short PK_CURVE_25519_KEY_LENGTH = Sign.CURVE25519_PUBLICKEYBYTES;
    public static final short SK_CURVE_25519_KEY_LENGTH = Sign.CURVE25519_SECRETKEYBYTES;

    static {
        INSTANCE = new Crypto(
                new LazySodiumJava(new SodiumJava()));

        // check for the optimal cryptographically secure pseudorandom number generator for the current platform
        SecureRandom prng;
        try {
            prng = SecureRandom.getInstance("Windows-PRNG");
        }
        catch (final Throwable e) { //NOSONAR
            // the windows PRNG is not available switch over to default provider
            // default for Unix-like systems is NativePRNG
            prng = new SecureRandom();
        }

        CSPRNG = prng;
    }

    private final LazySodiumJava sodium;

    Crypto(final LazySodiumJava sodium) {
        this.sodium = sodium;
    }

    /**
     * Compares to keys {@code k1} and {@code k2} to allow to form a total order on the keys. This
     * is especially important in asynchronous environments to make deterministic decisions.
     *
     * @param k1 first key
     * @param k2 second key
     * @return -1 if the first key is smaller than, 0 if equals to, 1 if greater than the second key
     */
    public static int compare(final Key k1, final Key k2) {
        return Integer.signum(UnsignedBytes.lexicographicalComparator().compare(
                k1.toByteArray(),
                k2.toByteArray()));
    }

    /**
     * Generates a secure random HEX String with the given {@code entropy} of bytes.
     *
     * <p>
     * Recommendation:
     *     <ul>
     *         <li>4 byte for small sets</li>
     *         <li>8 bytes for unique internal strings, e.g. hash tables</li>
     *         <li>16 bytes for global uniqueness, e.g. auth token</li>
     *         <li>24 bytes for cryptographic operations, e.g. nonce's</li>
     *     </ul>
     * <p>
     * You can also use the following probability table for the "Birthday problem", as a starting point for a suitable
     * entropy size:
     * <a href="https://en.wikipedia.org/wiki/Birthday_problem#Probability_table">Birthday problem probability table</a>
     * </p>
     *
     * @param entropy entropy in bytes
     * @return a secure random HEX String
     */
    public static String randomString(final int entropy) {
        return HexUtil.bytesToHex(randomBytes(entropy));
    }

    /**
     * Generates a secure random bytes with the given {@code entropy}.
     *
     * <p>
     * Recommendation:
     *     <ul>
     *         <li>4 byte for small sets</li>
     *         <li>8 bytes for unique internal strings, e.g. hash tables</li>
     *         <li>16 bytes for global uniqueness, e.g. auth token</li>
     *         <li>24 bytes for cryptographic operations, e.g. nonce's</li>
     *     </ul>
     * <p>
     * You can also use the following probability table for the "Birthday problem", as a starting point for a suitable
     * entropy size:
     * <a href="https://en.wikipedia.org/wiki/Birthday_problem#Probability_table">Birthday problem probability table</a>
     * </p>
     *
     * @param entropy entropy in bytes
     * @return a secure random bytes
     */
    public static byte[] randomBytes(final int entropy) {
        final byte[] token = new byte[entropy];
        CSPRNG.nextBytes(token);

        return token;
    }

    /**
     * Generates a random number with the static {@link SecureRandom} of this class. Avoids overhead
     * of generating a new instance of {@link SecureRandom}.
     *
     * @param bound the upper bound (exclusive).  Must be positive.
     * @return the next pseudorandom, uniformly distributed {@code int} value between zero
     * (inclusive) and {@code bound} (exclusive) from this random number generator's sequence
     */
    public static int randomNumber(final int bound) {
        return CSPRNG.nextInt(bound);
    }

    /**
     * <b>Is only for internal usage.</b>
     *
     * @return returns the {@link LazySodiumJava} instance.
     */
    public LazySodiumJava getSodium() {
        return sodium;
    }

    /**
     * Generates a new ed25519 key pair for signing and on-demand encryption. This key pair can be
     * used as identity of a node.
     *
     * @return new ed25519 key pair
     * @throws CryptoException if any error occurs during key generation
     */
    public KeyPair<IdentityPublicKey, IdentitySecretKey> generateLongTimeKeyPair() throws CryptoException {
        final byte[] publicKey = randomBytes(PK_LONG_TIME_KEY_LENGTH);
        final byte[] secretKey = randomBytes(SK_LONG_TIME_KEY_LENGTH);

        if (!sodium.cryptoSignKeypair(publicKey, secretKey)) {
            throw new CryptoException("Could not generate a signing keypair.");
        }
        return KeyPair.of(IdentityPublicKey.of(publicKey), IdentitySecretKey.of(secretKey));
    }

    /**
     * Converts the given ed25519 long time {@code keyPair} into a curve25519 key pair for
     * (on-demand) key agreement.
     *
     * @param keyPair the ed25519 long time key pair
     * @return ed25519 key pair as curve25519
     * @throws CryptoException if any error occurs during conversion
     */
    public KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> convertLongTimeKeyPairToKeyAgreementKeyPair(
            final KeyPair<IdentityPublicKey, IdentitySecretKey> keyPair) throws CryptoException {
        final byte[] curve25519Pk = new byte[PK_CURVE_25519_KEY_LENGTH];
        final byte[] curve25519Sk = new byte[SK_CURVE_25519_KEY_LENGTH];

        final boolean pkSuccess = sodium.convertPublicKeyEd25519ToCurve25519(curve25519Pk, keyPair.getPublicKey().toByteArray());
        final boolean skSuccess = sodium.convertSecretKeyEd25519ToCurve25519(curve25519Sk, keyPair.getSecretKey().toByteArray());

        if (!pkSuccess || !skSuccess) {
            throw new CryptoException("Could not convert this key pair.");
        }

        return KeyPair.of(KeyAgreementPublicKey.of(curve25519Pk), KeyAgreementSecretKey.of(curve25519Sk));
    }

    /**
     * Converts the given ed25519 long time {@code publicKey} into a curve25519 key for (on-demand)
     * key agreement.
     *
     * @param publicKey the ed25519 public key
     * @return ed25519 public key as curve25519
     * @throws CryptoException if any error occurs during conversion
     */
    @SuppressWarnings("java:S3242")
    public KeyAgreementPublicKey convertIdentityKeyToKeyAgreementKey(final IdentityPublicKey publicKey) throws CryptoException {
        final byte[] curve25519Pk = new byte[PK_CURVE_25519_KEY_LENGTH];

        final boolean pkSuccess = sodium.convertPublicKeyEd25519ToCurve25519(curve25519Pk, publicKey.toByteArray());

        if (!pkSuccess) {
            throw new CryptoException("Could not convert this key.");
        }

        return KeyAgreementPublicKey.of(curve25519Pk);
    }

    /**
     * Generates a new curve25519 key pair for key exchange. This key should only be used for one
     * session and never be re-used.
     *
     * @return new curve25519 key pair
     * @throws CryptoException if any error occurs during key generation
     */
    public KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> generateEphemeralKeyPair() throws CryptoException {
        final byte[] publicKey = randomBytes(PK_CURVE_25519_KEY_LENGTH);
        final byte[] secretKey = randomBytes(SK_CURVE_25519_KEY_LENGTH);

        if (!sodium.successful(sodium.getSodium().crypto_kx_keypair(publicKey, secretKey))) {
            throw new CryptoException("Unable to create a public and private key.");
        }
        return KeyPair.of(KeyAgreementPublicKey.of(publicKey), KeyAgreementSecretKey.of(secretKey));
    }

    /**
     * Generates session key pair from the {@code myKeyPair} and {@code receiverKeyPair}.
     *
     * @param myKeyPair         my own curve25519 key pair (long time or ephemeral)
     * @param receiverPublicKey the receiver public key (long time or ephemeral)
     * @return a session key for sending and receiving messages
     * @throws CryptoException if any error occurs during generation
     */
    public <P extends PublicKey, S extends org.drasyl.identity.SecretKey> SessionPair generateSessionKeyPair(
            final KeyPair<P, S> myKeyPair,
            final PublicKey receiverPublicKey) throws CryptoException {
        // We must ensure some order on the keys to work properly in async environments
        final int order = compare(myKeyPair.getPublicKey(), receiverPublicKey);

        try {
            switch (order) {
                case -1:
                    return sodium.cryptoKxClientSessionKeys(myKeyPair.getPublicKey().toSodiumKey(), myKeyPair.getSecretKey().toSodiumKey(), receiverPublicKey.toSodiumKey());
                case 1:
                    return sodium.cryptoKxServerSessionKeys(myKeyPair.getPublicKey().toSodiumKey(), myKeyPair.getSecretKey().toSodiumKey(), receiverPublicKey.toSodiumKey());
                case 0:
                    throw new CryptoException("Attention, there is probably an implementation error. " +
                            "Sessions with yourself are not supported!");
                default:
                    throw new CryptoException("Unknown error during session generation.");
            }
        }
        catch (final SodiumException e) {
            throw new CryptoException(e);
        }
    }

    /**
     * Encrypts the given {@code message}, by adding {@code authTag} as an authentication tag, using
     * the given (<b>hopefully fresh</b>) {@code nonce} and encrypting with the <i>tx</i> part of
     * the {@code sessionPair}.
     *
     * @param message     the message to encrypt
     * @param authTag     some authentication tag
     * @param nonce       the fresh nonce
     * @param sessionPair the session pair
     * @return encrypted message
     * @throws CryptoException      if any error occurs during encryption
     * @throws NullPointerException if {@code message} or {@code authTag} is {@code null}
     */
    public byte[] encrypt(final byte[] message,
                          final byte[] authTag,
                          final Nonce nonce,
                          final SessionPair sessionPair) throws CryptoException {
        Objects.requireNonNull(message);
        Objects.requireNonNull(authTag);

        final long additionalDataLength = authTag.length;
        final byte[] cipherBytes = new byte[message.length + AEAD.XCHACHA20POLY1305_IETF_ABYTES];

        if (!sodium.cryptoAeadXChaCha20Poly1305IetfEncrypt(
                cipherBytes,
                null,
                message,
                message.length,
                authTag,
                additionalDataLength,
                null,
                nonce.toByteArray(),
                sessionPair.getTx()
        )) {
            throw new CryptoException("Could not encrypt the given message with the given parameters.");
        }

        return cipherBytes;
    }

    /**
     * Decrypt the given {@code cipher}, by verify the {@code authTag} as an authentication tag,
     * uses the given {@code nonce} and decrypting with the
     * <i>rx</i> part of the {@code sessionPair}.
     *
     * @param cipher      the cipher text to decrypt
     * @param authTag     some authentication tag
     * @param nonce       the fresh nonce
     * @param sessionPair the session pair
     * @return decrypted message
     * @throws CryptoException      if any error occurs during decryption
     * @throws NullPointerException if {@code message} or {@code authTag} is {@code null}
     */
    public byte[] decrypt(final byte[] cipher,
                          final byte[] authTag,
                          final Nonce nonce,
                          final SessionPair sessionPair) throws CryptoException {
        Objects.requireNonNull(cipher);
        Objects.requireNonNull(authTag);

        if (cipher.length < AEAD.XCHACHA20POLY1305_IETF_ABYTES) {
            throw new CryptoException("Could not decrypt the given cipher text. Cipher text is smaller than " + AEAD.XCHACHA20POLY1305_IETF_ABYTES + " bytes");
        }

        final long additionalDataLength = authTag.length;
        final byte[] messageBytes = new byte[cipher.length - AEAD.XCHACHA20POLY1305_IETF_ABYTES];

        if (!sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(
                messageBytes,
                null,
                null,
                cipher,
                cipher.length,
                authTag,
                additionalDataLength,
                nonce.toByteArray(),
                sessionPair.getRx()
        )) {
            throw new CryptoException("Could not decrypt the given cipher text.");
        }

        return messageBytes;
    }

    /**
     * Creates a signature for the given {@code message} with the given {@code secretKey} in
     * detached mode (signature is not appended to message, rather it is standalone).
     *
     * @param message   the message to sign
     * @param secretKey the secret key to sign
     * @return the signature of the message
     * @throws CryptoException if any error occurs during signing
     */
    public byte[] sign(final byte[] message,
                       final IdentitySecretKey secretKey) throws CryptoException {
        final byte[] signatureBytes = new byte[Sign.BYTES];

        if (!sodium.cryptoSignDetached(signatureBytes,
                message,
                message.length,
                secretKey.toByteArray())) {
            throw new CryptoException("Could not create a signature for your message in detached mode.");
        }

        return signatureBytes;
    }

    /**
     * Verifies that {@code signature} is valid for the {@code message}.
     *
     * @param signature the signature of the message
     * @param message   the message
     * @param publicKey the public key that signed the message
     * @return {@code true} if the signature is valid for the message
     */
    public boolean verifySignature(final byte[] signature,
                                   final byte[] message,
                                   final IdentityPublicKey publicKey) {

        return sodium.cryptoSignVerifyDetached(signature,
                message,
                message.length,
                publicKey.toByteArray());
    }
}
