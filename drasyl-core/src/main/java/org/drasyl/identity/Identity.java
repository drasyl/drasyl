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
package org.drasyl.identity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.crypto.CryptoException;

import java.util.Objects;

/**
 * Represents the private identity of a peer (includes the proof of work, the public and private
 * key). Should be kept secret!.
 */
public class Identity {
    private final ProofOfWork proofOfWork;
    private final CompressedKeyPair keyPair;

    protected Identity(@JsonProperty("proofOfWork") int proofOfWork,
                       @JsonProperty("publicKey") String publicKey,
                       @JsonProperty("privateKey") String privateKey) throws CryptoException {
        this(ProofOfWork.of(proofOfWork), CompressedKeyPair.of(publicKey, privateKey));
    }

    private Identity(ProofOfWork proofOfWork, CompressedKeyPair keyPair) {
        this.proofOfWork = proofOfWork;
        this.keyPair = keyPair;
    }

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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Identity that = (Identity) o;
        return Objects.equals(keyPair, that.keyPair);
    }

    @Override
    public String toString() {
        return "PrivateIdentity{" +
                "keyPair=" + keyPair + ", " +
                " proofOfWork=" + proofOfWork +
                '}';
    }

    @JsonIgnore
    public ProofOfWork getPoW() {
        return proofOfWork;
    }

    @JsonProperty
    private int getProofOfWork() {
        return proofOfWork.getNonce();
    }

    public static Identity of(ProofOfWork proofOfWork,
                              CompressedPublicKey publicKey,
                              CompressedPrivateKey privateKey) {
        return of(proofOfWork, CompressedKeyPair.of(publicKey, privateKey));
    }

    public static Identity of(ProofOfWork proofOfWork,
                              CompressedKeyPair keyPair) {
        return new Identity(proofOfWork, keyPair);
    }

    public static Identity of(ProofOfWork proofOfWork,
                              String publicKey,
                              String privateKey) throws CryptoException {
        return of(proofOfWork, CompressedKeyPair.of(publicKey, privateKey));
    }

    public static Identity of(int proofOfWork,
                              String publicKey,
                              String privateKey) throws CryptoException {
        return of(ProofOfWork.of(proofOfWork), CompressedKeyPair.of(publicKey, privateKey));
    }
}
