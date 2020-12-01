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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
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

    ProofOfWork(final int nonce) {
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProofOfWork that = (ProofOfWork) o;
        return nonce == that.nonce;
    }

    @Override
    public String toString() {
        return "ProofOfWork{" +
                "nonce=" + nonce +
                '}';
    }

    public int getNonce() {
        return nonce;
    }

    /**
     * Returns the value of this {@code ProofOfWork} as an {@code int}.
     */
    @JsonValue
    public int intValue() {
        return nonce;
    }

    @JsonCreator
    public static ProofOfWork of(final int nonce) {
        return new ProofOfWork(nonce);
    }

    private static ProofOfWork of() {
        return of(0);
    }

    public static ProofOfWork generateProofOfWork(final CompressedPublicKey publicKey,
                                                  final short difficulty) {
        LOG.info("Generate proof of work. This may take a while ...");
        final ProofOfWork pow = ProofOfWork.of();

        while (!pow.isValid(publicKey, difficulty)) {
            pow.incNonce();
        }

        LOG.info("Proof of work was performed.");

        return pow;
    }

    public boolean isValid(final CompressedPublicKey publicKey, final short difficulty) {
        requireNonNull(publicKey);

        final String hash = generateHash(publicKey, nonce);

        return hash.startsWith("0".repeat(difficulty));
    }

    private static String generateHash(final CompressedPublicKey publicKey, final int nonce) {
        return Hashing.sha256Hex(publicKey.toString() + nonce);
    }

    public static short getDifficulty(final ProofOfWork proofOfWork,
                                      final CompressedPublicKey publicKey) {
        final String hash = generateHash(publicKey, proofOfWork.getNonce());
        short i;

        for (i = 0; i < hash.length(); i++) {
            if (hash.charAt(i) != '0') {
                break;
            }
        }

        return i;
    }

    public void incNonce() {
        this.nonce++;
    }
}