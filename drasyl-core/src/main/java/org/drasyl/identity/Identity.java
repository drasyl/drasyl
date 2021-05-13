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
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;

import java.util.Objects;

import static org.drasyl.identity.IdentityManager.POW_DIFFICULTY;

/**
 * Represents the private identity of a peer (includes the proof of work, the public and private
 * key). <b>Should be kept secret!</b>
 * <p>
 * This is an immutable object.
 */
public class Identity {
    private final ProofOfWork proofOfWork;
    private final KeyPair<IdentityPublicKey, IdentitySecretKey> identityKeyPair;
    private final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyAgreementKeyPair;

    @SuppressWarnings("unused")
    @JsonCreator
    private Identity(@JsonProperty("proofOfWork") final int proofOfWork,
                     @JsonProperty("identityKeyPair") final KeyPair<IdentityPublicKey, IdentitySecretKey> identityKeyPair,
                     @JsonProperty("keyAgreementKeyPair") final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyAgreementKeyPair) {
        this(ProofOfWork.of(proofOfWork), identityKeyPair, keyAgreementKeyPair);
    }

    private Identity(final ProofOfWork proofOfWork,
                     final KeyPair<IdentityPublicKey, IdentitySecretKey> identityKeyPair,
                     final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyAgreementKeyPair) {
        this.proofOfWork = proofOfWork;
        this.identityKeyPair = identityKeyPair;
        this.keyAgreementKeyPair = keyAgreementKeyPair;
    }

    private Identity(final ProofOfWork proofOfWork,
                     final KeyPair<IdentityPublicKey, IdentitySecretKey> identityKeyPair) {
        this.proofOfWork = proofOfWork;
        this.identityKeyPair = identityKeyPair;
        try {
            this.keyAgreementKeyPair = Crypto.INSTANCE.convertLongTimeKeyPairToKeyAgreementKeyPair(identityKeyPair);
        }
        catch (final CryptoException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Identity of(final ProofOfWork proofOfWork,
                              final IdentityPublicKey identityPublicKey,
                              final IdentitySecretKey identitySecretKey) {
        return of(proofOfWork, KeyPair.of(identityPublicKey, identitySecretKey));
    }

    public static Identity of(final ProofOfWork proofOfWork,
                              final KeyPair<IdentityPublicKey, IdentitySecretKey> identityKeyPair) {
        return new Identity(proofOfWork, identityKeyPair);
    }

    public static Identity of(final ProofOfWork proofOfWork,
                              final KeyPair<IdentityPublicKey, IdentitySecretKey> identityKeyPair,
                              final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyAgreementKeyPair) {
        return new Identity(proofOfWork, identityKeyPair, keyAgreementKeyPair);
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

    public KeyPair<IdentityPublicKey, IdentitySecretKey> getIdentityKeyPair() {
        return identityKeyPair;
    }

    @SuppressWarnings("unused")
    public KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> getKeyAgreementKeyPair() {
        return keyAgreementKeyPair;
    }

    @JsonIgnore
    public IdentityPublicKey getIdentityPublicKey() {
        return identityKeyPair.getPublicKey();
    }

    @JsonIgnore
    public IdentitySecretKey getIdentitySecretKey() {
        return identityKeyPair.getSecretKey();
    }

    @JsonIgnore
    public KeyAgreementPublicKey getKeyAgreementPublicKey() {
        return keyAgreementKeyPair.getPublicKey();
    }

    @JsonIgnore
    public KeyAgreementSecretKey getKeyAgreementSecretKey() {
        return keyAgreementKeyPair.getSecretKey();
    }

    @Override
    public int hashCode() {
        return Objects.hash(identityKeyPair);
    }

    public ProofOfWork getProofOfWork() {
        return proofOfWork;
    }

    /**
     * Validates the identity by checking whether the proof of work matches the public key.
     *
     * @return <code>true</code> if this identity is valid. Otherwise <code>false</code>
     */
    @JsonIgnore
    public boolean isValid() {
        return proofOfWork.isValid(identityKeyPair.getPublicKey(), POW_DIFFICULTY);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Identity identity = (Identity) o;
        return Objects.equals(proofOfWork, identity.proofOfWork) && Objects.equals(identityKeyPair, identity.identityKeyPair) && Objects.equals(keyAgreementKeyPair, identity.keyAgreementKeyPair);
    }

    @Override
    public String toString() {
        return "Identity{" +
                "proofOfWork=" + proofOfWork +
                ", identityKeyPair=" + identityKeyPair +
                ", keyAgreementKeyPair=" + keyAgreementKeyPair +
                '}';
    }
}
