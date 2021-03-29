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
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.HexUtil;
import org.drasyl.util.InternPool;

import java.security.PublicKey;

/**
 * This interface models a compressed key that can be converted into a string and vice versa.
 * <p>
 * This is an immutable object.
 */
public class CompressedPublicKey extends AbstractCompressedKey<PublicKey> {
    @SuppressWarnings("unused")
    public static final short PUBLIC_KEY_LENGTH = 66;
    public static final InternPool<CompressedPublicKey> POOL = new InternPool<>();

    /**
     * Creates a new compressed public key from the given string.
     *
     * @param compressedKey compressed public key
     * @throws NullPointerException     if {@code compressedKey} is {@code null}
     * @throws IllegalArgumentException if {@code compressedKey} does not conform to a valid string
     * @deprecated Use {@link #of(String)} instead.
     */
    // make method private on next release
    @Deprecated(since = "0.4.0", forRemoval = true)
    public CompressedPublicKey(final String compressedKey) {
        super(compressedKey);
    }

    /**
     * Creates a new compressed public key from the given public key.
     *
     * @param key compressed public key
     * @throws IllegalArgumentException if parameter does not conform to a valid hexadecimal string
     * @throws CryptoException          if the parameter does not conform to a valid key
     * @deprecated this will be removed in the next release.
     */
    @Deprecated(since = "0.4.0", forRemoval = true)
    public CompressedPublicKey(final PublicKey key) throws CryptoException {
        this(HexUtil.bytesToHex(Crypto.compressedKey(key)));
    }

    /**
     * Creates a new compressed public key from the given byte array.
     *
     * @param compressedKey compressed public key
     * @throws NullPointerException     if {@code compressedKey} is {@code null}
     * @throws IllegalArgumentException if {@code compressedKey} is empty
     */
    private CompressedPublicKey(final byte[] compressedKey) {
        super(compressedKey);
    }

    /**
     * Converts a {@link String} into a {@link CompressedPublicKey}.
     *
     * @param compressedKey compressed key as String
     * @return {@link CompressedPublicKey}
     * @throws NullPointerException     if {@code compressedKey} is {@code null}
     * @throws IllegalArgumentException if {@code compressedKey} does not conform to a valid key
     *                                  string
     */
    public static CompressedPublicKey of(final String compressedKey) {
        return new CompressedPublicKey(compressedKey).intern();
    }

    /**
     * Converts a byte[] into a {@link CompressedPublicKey}.
     *
     * @param compressedKey public key
     * @return {@link CompressedPublicKey}
     * @throws NullPointerException if {@code compressedKey} is {@code null}
     */
    @JsonCreator
    public static CompressedPublicKey of(final byte[] compressedKey) {
        return new CompressedPublicKey(compressedKey).intern();
    }

    /**
     * Returns the {@link PublicKey} object of this compressed public key.
     *
     * @throws IllegalStateException if uncompressed public key could not be generated
     */
    @Override
    public PublicKey toUncompressedKey() {
        if (key == null) {
            try {
                key = Crypto.getPublicKeyFromBytes(compressedKey);
            }
            catch (final CryptoException e) {
                throw new IllegalStateException("Uncompressed public key could not be generated", e);
            }
        }
        return this.key;
    }

    /**
     * See {@link InternPool#intern(Object)}
     */
    public CompressedPublicKey intern() {
        return POOL.intern(this);
    }
}
