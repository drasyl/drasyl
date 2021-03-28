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
