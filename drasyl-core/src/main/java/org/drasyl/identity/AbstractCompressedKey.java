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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.crypto.CryptoException;

import java.util.Objects;

abstract class AbstractCompressedKey<K> {
    @JsonValue
    protected final String compressedKey;
    @JsonIgnore
    protected K key;

    AbstractCompressedKey() {
        compressedKey = null;
        key = null;
    }

    protected AbstractCompressedKey(String compressedKey) throws CryptoException {
        this.compressedKey = compressedKey;
        this.key = toUncompressedKey();
    }

    protected AbstractCompressedKey(String compressedKey, K key) {
        this.compressedKey = compressedKey;
        this.key = key;
    }

    public abstract K toUncompressedKey() throws CryptoException;

    public String getCompressedKey() {
        return this.compressedKey;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(compressedKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractCompressedKey<?> that = (AbstractCompressedKey<?>) o;
        return Objects.equals(compressedKey, that.compressedKey);
    }

    @Override
    public String toString() {
        return this.compressedKey;
    }
}