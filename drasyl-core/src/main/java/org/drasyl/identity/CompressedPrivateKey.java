/*
 * Copyright (c) 2020-2021.
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
import org.drasyl.crypto.HexUtil;
import org.drasyl.util.InternPool;

import java.security.PrivateKey;

/**
 * This interface models a compressed key that can be converted into a string and vice versa.
 * <p>
 * This is an immutable object.
 */
public class CompressedPrivateKey extends AbstractCompressedKey<PrivateKey> {
    @SuppressWarnings("unused")
    public static final short PRIVATE_KEY_LENGTH = 64;
    public static final InternPool<CompressedPrivateKey> POOL = new InternPool<>();

    /**
     * Creates a new compressed private key from the given string.
     *
     * @param compressedKey compressed private key
     * @throws IllegalArgumentException if the string parameter does not conform to a valid key
     * @deprecated Use {@link #of(String)} instead.
     */
    // make method private on next release
    @Deprecated(since = "0.4.0", forRemoval = true)
    public CompressedPrivateKey(final String compressedKey) {
        super(compressedKey);
    }

    /**
     * Creates a new compressed private key from the given private key.
     *
     * @param key compressed private key
     * @throws IllegalArgumentException if parameter does not conform to a valid hexadecimal string
     * @throws CryptoException          if the parameter does not conform to a valid key
     * @deprecated this will be removed in the next release.
     */
    @Deprecated(since = "0.4.0", forRemoval = true)
    public CompressedPrivateKey(final PrivateKey key) throws CryptoException {
        this(HexUtil.bytesToHex(Crypto.compressedKey(key)));
    }

    /**
     * Creates a new compressed private key from the given byte array.
     *
     * @param compressedKey compressed private key
     */
    private CompressedPrivateKey(final byte[] compressedKey) {
        super(compressedKey);
    }

    /**
     * Converts a {@link String} into a {@link CompressedPrivateKey}.
     *
     * @param compressedKey compressed key as String
     * @return {@link CompressedPublicKey}
     * @throws IllegalArgumentException if string parameter does not conform to a valid key
     */
    public static CompressedPrivateKey of(final String compressedKey) {
        return new CompressedPrivateKey(compressedKey).intern();
    }

    /**
     * Converts a byte[] into a {@link CompressedPrivateKey}.
     *
     * @param compressedKey compressed key as byte array
     * @return {@link CompressedPublicKey}
     */
    @JsonCreator
    public static CompressedPrivateKey of(final byte[] compressedKey) {
        return new CompressedPrivateKey(compressedKey).intern();
    }

    /**
     * Returns the {@link PrivateKey} object of this compressed private key.
     *
     * @throws IllegalStateException if uncompressed private key could not be generated
     */
    @Override
    public PrivateKey toUncompressedKey() {
        if (key == null) {
            try {
                key = Crypto.getPrivateKeyFromBytes(compressedKey);
            }
            catch (final CryptoException e) {
                throw new IllegalStateException("Uncompressed private key could not be generated", e);
            }
        }
        return this.key;
    }

    /**
     * See {@link InternPool#intern(Object)}
     */
    public CompressedPrivateKey intern() {
        return POOL.intern(this);
    }
}
