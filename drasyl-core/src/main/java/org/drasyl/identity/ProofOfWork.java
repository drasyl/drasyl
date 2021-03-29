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
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.crypto.Hashing;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * This class models the proof of work for a given public key. Hence, identity creation becomes an
 * expensive operation and sybil attacks should be made more difficult.
 */
public class ProofOfWork {
    private static final Logger LOG = LoggerFactory.getLogger(ProofOfWork.class);
    private static final short MIN_DIFFICULTY = 0;
    private static final short MAX_DIFFICULTY = 64;
    private int nonce;

    private ProofOfWork(final int nonce) {
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
        return of(Integer.MIN_VALUE);
    }

    public static ProofOfWork generateProofOfWork(final CompressedPublicKey publicKey,
                                                  final byte difficulty) {
        LOG.info("Generate proof of work. This may take a while ...");
        final ProofOfWork pow = ProofOfWork.of();

        while (!pow.isValid(publicKey, difficulty)) {
            pow.incNonce();
        }

        LOG.info("Proof of work was performed.");

        return pow;
    }

    /**
     * Checks if the current proof of work is valid for given public key and difficulty.
     *
     * @param publicKey  the public key
     * @param difficulty the difficulty
     * @return if valid true, otherwise false
     * @throws IllegalArgumentException if the difficulty is not in between [0,64]
     */
    public boolean isValid(final CompressedPublicKey publicKey, final byte difficulty) {
        requireNonNull(publicKey);
        if (difficulty < MIN_DIFFICULTY || difficulty > MAX_DIFFICULTY) {
            throw new IllegalArgumentException("difficulty must in between the range of [0,64].");
        }

        final String hash = generateHash(publicKey, nonce);

        return hash.startsWith("0".repeat(difficulty));
    }

    private static String generateHash(final CompressedPublicKey publicKey, final int nonce) {
        return Hashing.sha256Hex(publicKey.toString() + nonce);
    }

    public static byte getDifficulty(final ProofOfWork proofOfWork,
                                     final CompressedPublicKey publicKey) {
        final String hash = generateHash(publicKey, proofOfWork.getNonce());
        byte i;

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
