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
import org.drasyl.crypto.Hashing;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * This class models the proof of work for a given public key. Hence, identity creation becomes an
 * expensive operation and sybil attacks should be made more difficult.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class ProofOfWork {
    private static final Logger LOG = LoggerFactory.getLogger(ProofOfWork.class);
    private static final short MIN_DIFFICULTY = 0;
    private static final short MAX_DIFFICULTY = 64;

    public abstract int getNonce();

    @Override
    public String toString() {
        return Integer.toString(getNonce());
    }

    /**
     * Returns the value of this {@code ProofOfWork} as an {@code int}.
     */
    public int intValue() {
        return getNonce();
    }

    /**
     * Checks if the current proof of work is valid for given public key and difficulty.
     *
     * @param address    the public key
     * @param difficulty the difficulty
     * @return if valid {@code true}, otherwise {@code false}
     * @throws IllegalArgumentException if the difficulty is not in between [0,64]
     */
    public boolean isValid(final DrasylAddress address, final byte difficulty) {
        requireNonNull(address);
        if (difficulty < MIN_DIFFICULTY || difficulty > MAX_DIFFICULTY) {
            throw new IllegalArgumentException("difficulty must in between the range of [0,64].");
        }

        final String hash = generateHash(address, getNonce());

        return hash.startsWith("0".repeat(difficulty));
    }

    private static String generateHash(final DrasylAddress address, final int nonce) {
        return Hashing.sha256Hex(address.toString() + nonce);
    }

    public static byte getDifficulty(final ProofOfWork proofOfWork,
                                     final IdentityPublicKey publicKey) {
        final String hash = generateHash(publicKey, proofOfWork.getNonce());
        byte i;

        for (i = 0; i < hash.length(); i++) {
            if (hash.charAt(i) != '0') {
                break;
            }
        }

        return i;
    }

    /**
     * Returns a Proof of Work with nonce incremented by 1.
     *
     * @return a Proof of Work with nonce incremented by 1
     * @throws IllegalStateException if incrementing would lead to an overflow of the nonce
     */
    public ProofOfWork incNonce() {
        if (getNonce() == Integer.MAX_VALUE) {
            throw new IllegalStateException("nonce cannot be incremented any further because this will cause an integer overflow.");
        }
        else {
            return of(getNonce() + 1);
        }
    }

    /**
     * @throws NullPointerException if {@code nonce} is {@code null}
     */
    public static ProofOfWork of(final int nonce) {
        return new AutoValue_ProofOfWork(nonce);
    }

    /**
     * @throws IllegalArgumentException if {@code nonce} does not contain a parsable integer.
     */
    public static ProofOfWork of(final String nonce) {
        return of(Integer.parseInt(nonce));
    }

    private static ProofOfWork of() {
        return of(Integer.MIN_VALUE);
    }

    /**
     * @throws IllegalStateException if there is no valid nonce for {@code address} at given {@code
     *                               difficulty}. In this case a new {@code address} should be
     *                               generated or {@code difficulty} should be reduced.
     */
    public static ProofOfWork generateProofOfWork(final DrasylAddress address,
                                                  final byte difficulty) {
        LOG.info("Generate proof of work for address `{}` and difficulty `{}`. This may take a while ...", address, difficulty);
        ProofOfWork pow = ProofOfWork.of();

        while (!pow.isValid(address, difficulty)) {
            if (LOG.isDebugEnabled() && pow.getNonce() % 1_000_000 == 0) {
                LOG.debug("[{}/{}] Proof of work generation in progress...", pow.getNonce(), Integer.MAX_VALUE);
            }
            pow = pow.incNonce();
        }

        LOG.info("Proof of work `{}` for address `{}` and difficulty `{}` was performed.", pow, address, difficulty);

        return pow;
    }
}
