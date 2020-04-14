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
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.HexUtil;

import java.security.PublicKey;
import java.util.Objects;

/**
 * This interface models a compressed key that can be converted into a string and vice versa.
 */
public class CompressedPublicKey {
    private final String compressedKey;
    @JsonIgnore
    private PublicKey key;

    CompressedPublicKey() {
        compressedKey = null;
        key = null;
    }

    private CompressedPublicKey(String compressedKey) throws CryptoException {
        this.compressedKey = compressedKey;
        this.key = Crypto.getPublicKeyFromBytes(HexUtil.fromString(compressedKey));
    }

    private CompressedPublicKey(PublicKey key) throws CryptoException {
        this.key = key;
        this.compressedKey = HexUtil.bytesToHex(Crypto.compressedKey(key));
    }

    CompressedPublicKey(String compressedKey, PublicKey key) {
        this.compressedKey = compressedKey;
        this.key = key;
    }

    /**
     * Converts a {@link String} into a {@link CompressedPublicKey}.
     *
     * @param compressedKey compressed key as String
     * @return {@link CompressedPublicKey}
     */
    public static CompressedPublicKey of(String compressedKey) throws CryptoException {
        return new CompressedPublicKey(compressedKey);
    }

    /**
     * Converts a {@link PublicKey} into a {@link CompressedPublicKey}.
     *
     * @param key public key
     * @return {@link CompressedPublicKey}
     */
    public static CompressedPublicKey of(PublicKey key) throws CryptoException {
        return new CompressedPublicKey(key);
    }

    @Override
    public String toString() {
        return this.compressedKey;
    }

    public String getCompressedKey() {
        return this.compressedKey;
    }

    public PublicKey toPubKey() throws CryptoException {
        if (key == null) {
            key = Crypto.getPublicKeyFromBytes(HexUtil.fromString(compressedKey));
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
        CompressedPublicKey that = (CompressedPublicKey) o;
        return Objects.equals(compressedKey, that.compressedKey);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(compressedKey);
    }
}
