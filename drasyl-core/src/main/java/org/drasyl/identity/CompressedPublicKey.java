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

import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.HexUtil;

import java.security.PublicKey;

/**
 * This interface models a compressed key that can be converted into a string and vice versa.
 * <p>
 * This is an immutable object.
 */
public class CompressedPublicKey extends AbstractCompressedKey<PublicKey> {
    /**
     * Creates a new compressed public key from the given string.
     *
     * @param compressedKey compressed public key
     * @throws IllegalArgumentException if string parameter does not conform to a valid hexadecimal
     *                                  string
     * @throws CryptoException          if the string parameter does not conform to a valid key
     */
    public CompressedPublicKey(String compressedKey) throws CryptoException {
        super(compressedKey);
    }

    /**
     * Creates a new compressed public key from the given public key.
     *
     * @param key compressed public key
     * @throws IllegalArgumentException if parameter does not conform to a valid hexadecimal string
     * @throws CryptoException          if the parameter does not conform to a valid key
     */
    public CompressedPublicKey(PublicKey key) throws CryptoException {
        this(HexUtil.bytesToHex(Crypto.compressedKey(key)), key);
    }

    /**
     * Creates a new compressed public key from the given string and public key.
     *
     * @param compressedKey compressed public key
     * @param key           public key
     */
    CompressedPublicKey(String compressedKey, PublicKey key) {
        super(compressedKey, key);
    }

    /**
     * Returns the {@link PublicKey} object of this compressed public key.
     *
     * @throws IllegalArgumentException if string parameter does not conform to a valid hexadecimal
     *                                  string
     * @throws CryptoException          if the string parameter does not conform to a valid key
     */
    @Override
    public PublicKey toUncompressedKey() throws CryptoException {
        if (key == null) {
            key = Crypto.getPublicKeyFromBytes(HexUtil.fromString(compressedKey));
        }
        return this.key;
    }

    /**
     * Converts a {@link String} into a {@link CompressedPublicKey}.
     *
     * @param compressedKey compressed key as String
     * @return {@link CompressedPublicKey}
     * @throws CryptoException          if string parameter does not conform to a valid key
     * @throws IllegalArgumentException if string parameter does not conform to a valid hexadecimal
     *                                  string
     */
    public static CompressedPublicKey of(String compressedKey) throws CryptoException {
        return new CompressedPublicKey(compressedKey);
    }

    /**
     * Converts a {@link PublicKey} into a {@link CompressedPublicKey}.
     *
     * @param key public key
     * @return {@link CompressedPublicKey}
     * @throws CryptoException          if string parameter does not conform to a valid key
     * @throws IllegalArgumentException if string parameter does not conform to a valid hexadecimal
     *                                  string
     */
    public static CompressedPublicKey of(PublicKey key) throws CryptoException {
        return new CompressedPublicKey(key);
    }
}