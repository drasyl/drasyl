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
package org.drasyl.identity;

import com.google.auto.value.AutoValue;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;

import java.io.IOException;

/**
 * Represents the private identity of a peer (includes the proof of work, the public and private
 * key). <b>Should be kept secret!</b>
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class Identity {
    public static final byte POW_DIFFICULTY = (byte) SystemPropertyUtil.getInt("org.drasyl.identity.pow-difficulty", 6);

    public abstract ProofOfWork getProofOfWork();

    public abstract KeyPair<IdentityPublicKey, IdentitySecretKey> getIdentityKeyPair();

    public abstract KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> getKeyAgreementKeyPair();

    public IdentityPublicKey getIdentityPublicKey() {
        return getIdentityKeyPair().getPublicKey();
    }

    public IdentitySecretKey getIdentitySecretKey() {
        return getIdentityKeyPair().getSecretKey();
    }

    /**
     * Unlike {@link #toString()}, this method returns the identity with the unmasked secret keys.
     *
     * @return identity with unmasked secret keys
     */
    public String toUnmaskedString() {
        return "Identity{"
                + "proofOfWork=" + getProofOfWork().toString() + ", "
                + "identityKeyPair=" + getIdentityKeyPair().toUnmaskedString() + ", "
                + "keyAgreementKeyPair=" + getKeyAgreementKeyPair().toUnmaskedString()
                + "}";
    }

    /**
     * Returns the address for this identity.
     *
     * @return returns the address for this identity.
     */
    public DrasylAddress getAddress() {
        return getIdentityPublicKey();
    }

    public KeyAgreementPublicKey getKeyAgreementPublicKey() {
        return getKeyAgreementKeyPair().getPublicKey();
    }

    public KeyAgreementSecretKey getKeyAgreementSecretKey() {
        return getKeyAgreementKeyPair().getSecretKey();
    }

    /**
     * Validates the identity by checking whether the proof of work matches the public key.
     *
     * @return {@code true} if this identity is valid. Otherwise {@code false}
     */
    public boolean isValid() {
        return getProofOfWork().isValid(getIdentityKeyPair().getPublicKey(), POW_DIFFICULTY);
    }

    /**
     * @throws NullPointerException if {@code proofOfWork}, {@code identityKeyPair} or {@code
     *                              keyAgreementKeyPair} is {@code null}.
     */
    public static Identity of(final ProofOfWork proofOfWork,
                              final KeyPair<IdentityPublicKey, IdentitySecretKey> identityKeyPair,
                              final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyAgreementKeyPair) {
        return new AutoValue_Identity(proofOfWork, identityKeyPair, keyAgreementKeyPair);
    }

    /**
     * @throws NullPointerException     if {@code proofOfWork}, {@code identityPublicKey} or {@code
     *                                  identitySecretKey} is {@code null}.
     * @throws IllegalArgumentException if {@code identityPublicKey} and {@code identitySecretKey}
     *                                  can not be converted to a key agreement key pair.
     */
    public static Identity of(final ProofOfWork proofOfWork,
                              final IdentityPublicKey identityPublicKey,
                              final IdentitySecretKey identitySecretKey) {
        return of(proofOfWork, KeyPair.of(identityPublicKey, identitySecretKey));
    }

    /**
     * @throws IllegalArgumentException if {@code identityKeyPair} can not be converted to a key
     *                                  agreement key pair.
     * @throws NullPointerException     if {@code proofOfWork}, {@code identityKeyPair} or a key
     *                                  within the pair is {@code null}
     */
    public static Identity of(final ProofOfWork proofOfWork,
                              final KeyPair<IdentityPublicKey, IdentitySecretKey> identityKeyPair) {
        try {
            return of(proofOfWork, identityKeyPair, Crypto.INSTANCE.convertLongTimeKeyPairToKeyAgreementKeyPair(identityKeyPair));
        }
        catch (final CryptoException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Identity of(final ProofOfWork proofOfWork,
                              final String identityPublicKey,
                              final String identitySecretKey) {
        return of(proofOfWork, KeyPair.of(
                IdentityPublicKey.of(identityPublicKey),
                IdentitySecretKey.of(identitySecretKey)));
    }

    public static Identity of(final int proofOfWork,
                              final String identityPublicKey,
                              final String identitySecretKey) {
        return of(ProofOfWork.of(proofOfWork), KeyPair.of(
                IdentityPublicKey.of(identityPublicKey),
                IdentitySecretKey.of(identitySecretKey)));
    }

    /**
     * @throws NullPointerException if {@code identityKeyPair} or {@code keyAgreementKeyPair} is
     *                              {@code null}.
     */
    public static Identity of(final int proofOfWork,
                              final KeyPair<IdentityPublicKey, IdentitySecretKey> identityKeyPair,
                              final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyAgreementKeyPair) {
        return of(ProofOfWork.of(proofOfWork), identityKeyPair, keyAgreementKeyPair);
    }

    /**
     * @throws NullPointerException if {@code identitySecretKey} is {@code null}.
     */
    public static Identity of(final int proofOfWork,
                              final IdentitySecretKey identitySecretKey) {
        return of(ProofOfWork.of(proofOfWork), identitySecretKey);
    }

    /**
     * @throws NullPointerException if {@code proofOfWork} or {@code identitySecretKey} is {@code
     *                              null}.
     */
    public static Identity of(final ProofOfWork proofOfWork,
                              final IdentitySecretKey identitySecretKey) {
        return of(proofOfWork, identitySecretKey.derivePublicKey(), identitySecretKey);
    }

    /**
     * @throws IllegalArgumentException if {@code identitySecretKey} is not a valid secret key.
     */
    public static Identity of(final int proofOfWork,
                              final String identitySecretKey) {
        return of(ProofOfWork.of(proofOfWork), IdentitySecretKey.of(identitySecretKey));
    }

    /**
     * Generates a new random identity.
     *
     * @return the generated identity
     * @throws IOException if an identity could not be generated
     */
    public static Identity generateIdentity() throws IOException {
        try {
            final KeyPair<IdentityPublicKey, IdentitySecretKey> identityKeyPair = Crypto.INSTANCE.generateLongTimeKeyPair();
            final ProofOfWork pow = ProofOfWork.generateProofOfWork(identityKeyPair.getPublicKey(), POW_DIFFICULTY);

            return of(pow, identityKeyPair);
        }
        catch (final CryptoException | IllegalStateException e) {
            throw new IOException("Unable to generate new identity", e);
        }
    }
}
