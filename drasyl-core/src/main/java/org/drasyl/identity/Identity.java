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

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.re2j.Pattern;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.Objects;

/**
 * This class models the identity of a drasyl node. The identity is derived from the SHA-256 hash of
 * the public key and is 10 hexadecimal characters long.
 */
public class Identity {
    private static final Pattern syntax = Pattern.compile("^[a-f0-9]{10}$");
    @JsonValue
    private final String id;

    protected Identity() {
        this.id = null;
    }

    Identity(String id) {
        Objects.requireNonNull(id);
        if (!isValid(id)) {
            throw new IllegalArgumentException("This is not a valid Identity: '" + id + "'");
        }

        this.id = id;
    }

    public static boolean isValid(String id) {
        return syntax.matches(id);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Identity identity = (Identity) o;
        return Objects.equals(id, identity.id);
    }

    @Override
    public String toString() {
        return "Identity{" +
                "id=" + id +
                "}";
    }

    public static Identity of(CompressedPublicKey compressedPublicKey) {
        Objects.requireNonNull(compressedPublicKey);
        var cpk = compressedPublicKey.toString();
        var hash = DigestUtils.sha256Hex(cpk);
        var shortID = hash.substring(0, Math.min(hash.length(), 10));
        return new Identity(shortID);
    }

    public static Identity of(String id) {
        return new Identity(id);
    }

    /**
     * Checks whether the identity corresponds to the compressed public key.
     *
     * @param compressedPublicKey the {@link CompressedPublicKey} to be checked
     * @param identity            the {@link Identity} to be checked
     * @return true, iff the compressedPublicKey corresponds to the given identity
     */
    public static boolean verify(CompressedPublicKey compressedPublicKey, Identity identity) {
        Objects.requireNonNull(compressedPublicKey);
        Objects.requireNonNull(identity);
        var hash = DigestUtils.sha256Hex(compressedPublicKey.toString());

        return hash.startsWith(identity.getId());
    }

    public String getId() {
        return id;
    }
}
