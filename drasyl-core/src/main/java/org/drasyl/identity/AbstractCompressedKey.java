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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.HexUtil;
import org.drasyl.pipeline.address.Address;

import java.util.Arrays;

abstract class AbstractCompressedKey<K> implements Address {
    @JsonValue
    protected final byte[] compressedKey;
    @JsonIgnore
    protected K key;

    AbstractCompressedKey() {
        compressedKey = null;
        key = null;
    }

    @JsonCreator
    protected AbstractCompressedKey(final byte[] compressedKey) throws CryptoException {
        this.compressedKey = compressedKey;
        this.key = toUncompressedKey();
    }

    protected AbstractCompressedKey(final byte[] compressedKey, final K key) {
        this.compressedKey = compressedKey;
        this.key = key;
    }

    public abstract K toUncompressedKey() throws CryptoException;

    public byte[] getCompressedKey() {
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
}