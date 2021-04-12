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

import java.security.PrivateKey;

import static org.drasyl.util.SecretUtil.maskSecret;

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
     * @throws NullPointerException     if {@code compressedKey} is {@code null}
     * @throws IllegalArgumentException if {@code compressedKey} is empty
     */
    private CompressedPrivateKey(final byte[] compressedKey) {
        super(compressedKey);
    }

    @Override
    public String toString() {
        return maskSecret(super.toString());
    }

    public String toUnmaskedString() {
        return super.toString();
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
