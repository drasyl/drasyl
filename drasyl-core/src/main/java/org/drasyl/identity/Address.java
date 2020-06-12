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

import static java.util.Objects.requireNonNull;

/**
 * This class models the identity of a drasyl node. The identity is derived from the SHA-256 hash of
 * the public key and is 10 hexadecimal characters long.
 */
public class Address {
    private static final Pattern syntax = Pattern.compile("^[a-f0-9]{10}$");
    @JsonValue
    private final String id;

    protected Address() {
        this.id = null;
    }

    Address(String id) {
        requireNonNull(id);
        if (!isValid(id)) {
            throw new IllegalArgumentException("This is not a valid Address: '" + id + "'");
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
        Address address = (Address) o;
        return Objects.equals(this.id, address.id);
    }

    @Override
    public String toString() {
        return id;
    }

    /**
     * Derives an address from given <code>compressedPublicKey</code>.
     * <p>
     * Note: This method will be an really expensive in a future release and should only be used
     * when generating a new identity (see https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/issues/61).
     *
     * @param compressedPublicKey
     * @return
     */
    public static Address derive(CompressedPublicKey compressedPublicKey) {
        requireNonNull(compressedPublicKey);
        String cpk = compressedPublicKey.toString();
        String hash = DigestUtils.sha256Hex(cpk);
        String shortID = hash.substring(0, Math.min(hash.length(), 10));
        return new Address(shortID);
    }

    public static Address of(String id) {
        return new Address(id);
    }

    /**
     * Checks whether the address corresponds to the compressed public key.
     *
     * @param address             the {@link Address} to be checked
     * @param compressedPublicKey the {@link CompressedPublicKey} to be checked
     * @return true, iff the compressedPublicKey corresponds to the given identity
     */
    public static boolean verify(Address address,
                                 CompressedPublicKey compressedPublicKey) {
        requireNonNull(compressedPublicKey);
        requireNonNull(address);
        String hash = DigestUtils.sha256Hex(compressedPublicKey.toString());

        return hash.startsWith(address.getId());
    }

    public String getId() {
        return id;
    }
}
