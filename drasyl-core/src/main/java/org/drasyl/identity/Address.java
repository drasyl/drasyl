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
    private final String address;

    protected Address() {
        this.address = null;
    }

    Address(String address) {
        requireNonNull(address);
        if (!isValid(address)) {
            throw new IllegalArgumentException("This is not a valid Address: '" + address + "'");
        }

        this.address = address;
    }

    public static boolean isValid(String id) {
        return syntax.matches(id);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(address);
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
        return Objects.equals(this.address, address.address);
    }

    @Override
    public String toString() {
        return address;
    }

    public static Address of(CompressedPublicKey compressedPublicKey) {
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
     * Checks whether the identity corresponds to the compressed public key.
     *
     * @param compressedPublicKey the {@link CompressedPublicKey} to be checked
     * @param address             the {@link Address} to be checked
     * @return true, iff the compressedPublicKey corresponds to the given identity
     */
    public static boolean verify(CompressedPublicKey compressedPublicKey, Address address) {
        requireNonNull(compressedPublicKey);
        requireNonNull(address);
        String hash = DigestUtils.sha256Hex(compressedPublicKey.toString());

        return hash.startsWith(address.getAddress());
    }

    public String getAddress() {
        return address;
    }
}
