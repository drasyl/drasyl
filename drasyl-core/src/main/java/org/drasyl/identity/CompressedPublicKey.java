/*
 * Copyright (c) 2021.
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
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;

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
     * @throws CryptoException if the string parameter does not conform to a valid key
     */
    public CompressedPublicKey(final String compressedKey) throws CryptoException {
        super(compressedKey);
    }

    /**
     * Creates a new compressed public key from the given byte array.
     *
     * @param compressedKey compressed public key
     * @throws CryptoException if the byte parameter does not conform to a valid key
     */
    private CompressedPublicKey(final byte[] compressedKey) throws CryptoException {
        super(compressedKey);
    }

    /**
     * Creates a new compressed public key from the given public key.
     *
     * @param key compressed public key
     * @throws IllegalArgumentException if parameter does not conform to a valid hexadecimal string
     * @throws CryptoException          if the parameter does not conform to a valid key
     */
    public CompressedPublicKey(final PublicKey key) throws CryptoException {
        super(Crypto.compressedKey(key), key);
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
            key = Crypto.getPublicKeyFromBytes(compressedKey);
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
    public static CompressedPublicKey of(final String compressedKey) throws CryptoException {
        return new CompressedPublicKey(compressedKey);
    }

    /**
     * Converts a {@link PublicKey} into a {@link CompressedPublicKey}.
     *
     * @param key public key
     * @return {@link CompressedPublicKey}
     * @throws CryptoException if string parameter does not conform to a valid key
     */
    public static CompressedPublicKey of(final PublicKey key) throws CryptoException {
        return new CompressedPublicKey(key);
    }

    /**
     * Converts a byte[] into a {@link CompressedPublicKey}.
     *
     * @param compressedKey public key
     * @return {@link CompressedPublicKey}
     * @throws CryptoException if string parameter does not conform to a valid key
     */
    @JsonCreator
    public static CompressedPublicKey of(final byte[] compressedKey) throws CryptoException {
        return new CompressedPublicKey(compressedKey);
    }
}