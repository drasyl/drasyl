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
package org.drasyl.crypto;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;

import java.nio.charset.StandardCharsets;

/**
 * Util class that provides hashing functions for drasyl.
 */
public final class Hashing {
    public static final HashFunction MURMUR3_128 = com.google.common.hash.Hashing.murmur3_128();
    public static final HashFunction SHA256 = com.google.common.hash.Hashing.sha256();

    private Hashing() {
        // util class
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
}