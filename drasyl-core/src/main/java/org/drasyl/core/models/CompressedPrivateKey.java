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
package org.drasyl.core.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.HexUtil;

import java.security.PrivateKey;
import java.util.Objects;

/**
 * This interface models a compressed key that can be converted into a string and vice versa.
 */
public class CompressedPrivateKey {
    @JsonValue
    private final String compressedKey;
    @JsonIgnore
    private PrivateKey key;

    CompressedPrivateKey() {
        compressedKey = null;
    }

    private CompressedPrivateKey(String compressedKey) throws CryptoException {
        this.compressedKey = compressedKey;
        this.key = toPrivKey();
    }

    private CompressedPrivateKey(PrivateKey key) throws CryptoException {
        this(HexUtil.bytesToHex(Crypto.compressedKey(key)), key);
    }

    CompressedPrivateKey(String compressedKey, PrivateKey key) {
        this.compressedKey = compressedKey;
        this.key = key;
    }

    /**
     * Converts a {@link String} into a {@link CompressedPrivateKey}.
     *
     * @param compressedKey compressed key as String
     * @return {@link CompressedPrivateKey}
     */
    public static CompressedPrivateKey of(String compressedKey) throws CryptoException {
        return new CompressedPrivateKey(compressedKey);
    }

    /**
     * Converts a {@link PrivateKey} into a {@link CompressedPrivateKey}.
     *
     * @param key private key
     * @return {@link CompressedPrivateKey}
     */
    public static CompressedPrivateKey of(PrivateKey key) throws CryptoException {
        return new CompressedPrivateKey(key);
    }

    @Override
    public String toString() {
        return this.compressedKey;
    }

    public String getCompressedKey() {
        return this.compressedKey;
    }

    public PrivateKey toPrivKey() throws CryptoException {
        if (key == null) {
            key = Crypto.getPrivateKeyFromBytes(HexUtil.fromString(compressedKey));
        }

        return this.key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CompressedPrivateKey that = (CompressedPrivateKey) o;
        return Objects.equals(compressedKey, that.compressedKey);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(compressedKey);
    }
}
