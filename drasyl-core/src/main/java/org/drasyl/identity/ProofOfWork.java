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

import org.drasyl.crypto.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * This class models the proof of work for a given public key. Hence, identity creation becomes an
 * expensive operation and sybil attacks should be made more difficult.
 */
public class ProofOfWork {
    private static final Logger LOG = LoggerFactory.getLogger(ProofOfWork.class);
    private int nonce;

    private ProofOfWork() {
        this.nonce = 0;
    }

    public ProofOfWork(int nonce) {
        if (nonce < 0) {
            throw new IllegalArgumentException("Nonce must be positive.");
        }

        this.nonce = nonce;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nonce);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProofOfWork that = (ProofOfWork) o;
        return nonce == that.nonce;
    }

    @Override
    public String toString() {
        return "ProofOfWork{" +
                "nonce=" + nonce +
                '}';
    }

    public int getNonce() {
        return this.nonce;
    }

    public static ProofOfWork of(int nonce) {
        return new ProofOfWork(nonce);
    }

    public static ProofOfWork of(CompressedPublicKey publicKey, short difficulty) {
        return ProofOfWork.generateProofOfWork(publicKey, difficulty);
    }

    public static ProofOfWork generateProofOfWork(CompressedPublicKey publicKey, short difficulty) {
        LOG.info("Generate proof of work. This may take a while ...");
        ProofOfWork pow = new ProofOfWork();

        while (!pow.isValid(publicKey, difficulty)) {
            pow.incNonce();
        }

        LOG.info("Proof of work was performed.");

        return pow;
    }

    public boolean isValid(CompressedPublicKey publicKey, short difficulty) {
        requireNonNull(publicKey);

        String hash = Hashing.sha256Hex(publicKey.getCompressedKey() + this.nonce);

        return hash.startsWith("0".repeat(difficulty));
    }

    public void incNonce() {
        this.nonce++;
    }
}
