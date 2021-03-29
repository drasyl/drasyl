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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.crypto.HexUtil;
import org.drasyl.pipeline.address.Address;

import java.util.Arrays;
import java.util.Base64;

abstract class AbstractCompressedKey<K> implements Address {
    public static final int LEGACY_KEY_LENGTH = 44;
    @JsonValue
    protected final byte[] compressedKey;
    @JsonIgnore
    protected K key;

    AbstractCompressedKey() {
        compressedKey = null;
        key = null;
    }

    /**
     * @throws NullPointerException     if {@code compressedKey} is {@code null}
     * @throws IllegalArgumentException if {@code compressedKey} is empty
     */
    protected AbstractCompressedKey(final byte[] compressedKey) {
        if (compressedKey.length == 0) {
            throw new IllegalArgumentException("compressedKey must not be empty.");
        }
        this.compressedKey = compressedKey;
        this.key = null;
    }

    /**
     * @throws NullPointerException     if {@code compressedKey} is {@code null}
     * @throws IllegalArgumentException if {@code compressedKey} does not conform to a valid
     *                                  hexadecimal or base64 scheme or is empty
     */
    @JsonCreator
    protected AbstractCompressedKey(final String compressedKey) {
        if (compressedKey.isEmpty()) {
            throw new IllegalArgumentException("compressedKey must not be empty.");
        }
        // For backwards compatibility we check if the given string represents a base64 (new) or
        // a normal string.
        // base64 encoded 32 up to 33 bytes long key ((4 * n / 3) + 3) & ~3
        if (compressedKey.length() == LEGACY_KEY_LENGTH) {
            this.compressedKey = Base64.getDecoder().decode(compressedKey);
        }
        else {
            this.compressedKey = HexUtil.fromString(compressedKey);
        }
        this.key = null;
    }

    protected AbstractCompressedKey(final byte[] compressedKey, final K key) {
        this.compressedKey = compressedKey;
        this.key = key;
    }

    public abstract K toUncompressedKey();

    public byte[] byteArrayValue() {
        return this.compressedKey;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(compressedKey);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractCompressedKey<?> that = (AbstractCompressedKey<?>) o;
        return Arrays.equals(compressedKey, that.compressedKey);
    }

    @Override
    public String toString() {
        return HexUtil.bytesToHex(this.compressedKey);
    }

    /**
     * @deprecated Use {@link #toString()} ()} instead.
     */
    @Deprecated(since = "0.4.0", forRemoval = true)
    public String getCompressedKey() {
        return toString();
    }
}
