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

import java.util.Objects;

import static org.drasyl.identity.IdentityManager.POW_DIFFICULTY;

/**
 * Represents the private identity of a peer (includes the proof of work, the public and private
 * key). Should be kept secret!.
 * <p>
 * This is an immutable object.
 */
public class Identity {
    private final ProofOfWork proofOfWork;
    private final CompressedKeyPair keyPair;

    @SuppressWarnings("unused")
    @JsonCreator
    private Identity(@JsonProperty("proofOfWork") final int proofOfWork,
                     @JsonProperty("publicKey") final String publicKey,
                     @JsonProperty("privateKey") final String privateKey) {
        this(ProofOfWork.of(proofOfWork), CompressedKeyPair.of(publicKey, privateKey));
    }

    private Identity(final ProofOfWork proofOfWork, final CompressedKeyPair keyPair) {
        this.proofOfWork = proofOfWork;
        this.keyPair = keyPair;
    }

    @SuppressWarnings("unused")
    @JsonIgnore
    public CompressedKeyPair getKeyPair() {
        return keyPair;
    }

    public CompressedPublicKey getPublicKey() {
        return keyPair.getPublicKey();
    }

    public CompressedPrivateKey getPrivateKey() {
        return keyPair.getPrivateKey();
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyPair);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Identity that = (Identity) o;
        return Objects.equals(keyPair, that.keyPair);
    }

    @Override
    public String toString() {
        return "PrivateIdentity{" +
                "keyPair=" + keyPair + ", " +
                " proofOfWork=" + proofOfWork +
                '}';
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
        return proofOfWork.isValid(keyPair.getPublicKey(), POW_DIFFICULTY);
    }

    public static Identity of(final ProofOfWork proofOfWork,
                              final CompressedPublicKey publicKey,
                              final CompressedPrivateKey privateKey) {
        return of(proofOfWork, CompressedKeyPair.of(publicKey, privateKey));
    }

    public static Identity of(final ProofOfWork proofOfWork,
                              final CompressedKeyPair keyPair) {
        return new Identity(proofOfWork, keyPair);
    }

    public static Identity of(final ProofOfWork proofOfWork,
                              final String publicKey,
                              final String privateKey) {
        return of(proofOfWork, CompressedKeyPair.of(publicKey, privateKey));
    }

    public static Identity of(final int proofOfWork,
                              final String publicKey,
                              final String privateKey) {
        return of(ProofOfWork.of(proofOfWork), CompressedKeyPair.of(publicKey, privateKey));
    }
}
