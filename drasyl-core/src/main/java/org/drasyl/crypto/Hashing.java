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

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import org.drasyl.util.ArrayUtil;

import java.nio.charset.StandardCharsets;

/**
 * Util class that provides hashing functions for drasyl.
 */
public final class Hashing {
    public static final HashFunction MURMUR3_128 = com.google.common.hash.Hashing.murmur3_128();
    public static final HashFunction SHA256 = com.google.common.hash.Hashing.sha256();
    public static final HashFunction MURMUR3_32 = com.google.common.hash.Hashing.murmur3_32_fixed();

    private Hashing() {
        // util class
    }

    /**
     * Generates a SHA-256 hash of the given input.
     *
     * @param input the input to hash
     * @return SHA-256 hash of the input
     */
    public static byte[] sha256(final byte[]... input) {
        return SHA256.hashBytes(ArrayUtil.concat(input)).asBytes();
    }

    /**
     * Generates a SHA-256 hash of the given input.
     *
     * @param input the input to hash
     * @return SHA-256 hash of the input
     */
    public static String sha256Hex(final String input) {
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a SHA-256 hash of the given input.
     *
     * @param input the input to hash
     * @return SHA-256 hash of the input
     */
    public static String sha256Hex(final byte[] input) {
        return hashCode2Hex(SHA256.hashBytes(input));
    }

    /**
     * Converts the given {@link HashCode} into a hexadecimal string.
     *
     * @param code the hash code to be converted
     * @return hexadecimal string representation of the hash code
     */
    public static String hashCode2Hex(final HashCode code) {
        return HexUtil.bytesToHex(code.asBytes());
    }

    /**
     * Generates a hexadecimal representation of the MurMur3x64 hash of the input.
     *
     * @param input the input to hash
     * @return MurMur3x64 hash as hexadecimal string
     */
    public static String murmur3x64Hex(final String input) {
        return murmur3x64Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a hexadecimal representation of the MurMur3x64 hash of the input.
     *
     * @param input the input to hash
     * @return MurMur3x64 hash as hexadecimal string
     */
    public static String murmur3x64Hex(final byte[] input) {
        return hashCode2Hex(MURMUR3_128.hashBytes(input));
    }

    /**
     * Generates a hexadecimal representation of the MurMur3x32 hash of the input.
     *
     * @param input the input to hash
     * @return MurMur3x32 hash as hexadecimal string
     */
    public static String murmur3x32Hex(final String input) {
        return murmur3x32Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a hexadecimal representation of the MurMur3x32 hash of the input.
     *
     * @param input the input to hash
     * @return MurMur3x32 hash as hexadecimal string
     */
    public static String murmur3x32Hex(final byte[] input) {
        return hashCode2Hex(MURMUR3_32.hashBytes(input));
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
}
