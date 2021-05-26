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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;

import static org.drasyl.identity.IdentityManager.POW_DIFFICULTY;

/**
 * Represents the private identity of a peer (includes the proof of work, the public and private
 * key). <b>Should be kept secret!</b>
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class Identity {
    public abstract ProofOfWork getProofOfWork();

    public abstract KeyPair<IdentityPublicKey, IdentitySecretKey> getIdentityKeyPair();

    public abstract KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> getKeyAgreementKeyPair();

    @JsonIgnore
    public IdentityPublicKey getIdentityPublicKey() {
        return getIdentityKeyPair().getPublicKey();
    }

    @JsonIgnore
    public IdentitySecretKey getIdentitySecretKey() {
        return getIdentityKeyPair().getSecretKey();
    }

    @JsonIgnore
    public KeyAgreementPublicKey getKeyAgreementPublicKey() {
        return getKeyAgreementKeyPair().getPublicKey();
    }

    @JsonIgnore
    public KeyAgreementSecretKey getKeyAgreementSecretKey() {
        return getKeyAgreementKeyPair().getSecretKey();
    }

    /**
     * Validates the identity by checking whether the proof of work matches the public key.
     *
     * @return {@code true} if this identity is valid. Otherwise {@code false}
     */
    @JsonIgnore
    public boolean isValid() {
        return getProofOfWork().isValid(getIdentityKeyPair().getPublicKey(), POW_DIFFICULTY);
    }

    public static Identity of(final ProofOfWork proofOfWork,
                              final KeyPair<IdentityPublicKey, IdentitySecretKey> identityKeyPair,
                              final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyAgreementKeyPair) {
        return new AutoValue_Identity(proofOfWork, identityKeyPair, keyAgreementKeyPair);
    }

    public static Identity of(final ProofOfWork proofOfWork,
                              final IdentityPublicKey identityPublicKey,
                              final IdentitySecretKey identitySecretKey) {
        return of(proofOfWork, KeyPair.of(identityPublicKey, identitySecretKey));
    }

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

    @JsonCreator
    public static Identity of(@JsonProperty("proofOfWork") final int proofOfWork,
                              @JsonProperty("identityKeyPair") final KeyPair<IdentityPublicKey, IdentitySecretKey> identityKeyPair,
                              @JsonProperty("keyAgreementKeyPair") final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyAgreementKeyPair) {
        return of(ProofOfWork.of(proofOfWork), identityKeyPair, keyAgreementKeyPair);
    }
}
