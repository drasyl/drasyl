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

import com.google.common.hash.HashFunction;
import com.sun.jna.NativeLong;
import org.drasyl.util.ArrayUtil;

import java.nio.charset.StandardCharsets;

/**
 * Util class that provides hashing functions for drasyl.
 */
public final class Hashing {
    public static final HashFunction MURMUR3_32 = com.google.common.hash.Hashing.murmur3_32_fixed();

    private Hashing() {
        // util class
    }

    /**
     * Generates a SHA-256 hash of the given input.
     *
     * @param input the input to hash
     * @return SHA-256 hash of the input
     * @throws IllegalArgumentException if SHA-256 hash could not be created
     */
    @SuppressWarnings("java:S1845")
    public static byte[] sha256(final String input) {
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a SHA-256 hash of the given input.
     *
     * @param input the input to hash
     * @return SHA-256 hash of the input
     * @throws IllegalArgumentException if SHA-256 hash could not be created
     */
    @SuppressWarnings("java:S1845")
    public static byte[] sha256(final byte[]... input) {
        try {
            return Crypto.INSTANCE.sha256(ArrayUtil.concat(input));
        }
        catch (final CryptoException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Generates a MurMur3x32 hash of the input.
     *
     * @param input the input to hash
     * @return MurMur3x32 hash
     */
    public static byte[] murmur3x32(final byte[]... input) {
        return MURMUR3_32.hashBytes(ArrayUtil.concat(input)).asBytes();
    }

    public static byte[] argon2id(final long opsLimit,
                                  final NativeLong memLimit,
                                  final byte[]... input) {
        try {
            return Crypto.INSTANCE.argon2idHash(ArrayUtil.concat(input), opsLimit, memLimit);
        }
        catch (final CryptoException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static boolean argon2idVerify(final byte[] hash,
                                         final byte[]... input) {
        return Crypto.INSTANCE.argon2idHashVerify(hash, ArrayUtil.concat(input));
    }
}
