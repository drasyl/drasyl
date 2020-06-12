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
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.crypto.CryptoException;

import java.util.Objects;

/**
 * Represents the private identity of a peer (includes the address and the public and private key).
 * Should be kept secret!.
 */
public class PrivateIdentity extends AbstractIdentity {
    private final CompressedKeyPair keyPair;

    private PrivateIdentity(@JsonProperty("address") String address,
                           @JsonProperty("publicKey") String publicKey,
                           @JsonProperty("privateKey") String privateKey) throws CryptoException {
        this(Address.of(address), CompressedKeyPair.of(publicKey, privateKey));
    }

    private PrivateIdentity(Address address,
                           CompressedKeyPair keyPair) {
        super(address);
        this.keyPair = keyPair;

        if (this.keyPair != null && !Address.verify(address, keyPair.getPublicKey())) {
            throw new IllegalArgumentException("Address '" + address +"' does not correspond to Public Key '" + keyPair.getPublicKey() + "'");
        }
    }

    @Override
    public String toString() {
        return "PrivateIdentity{" +
                "address=" + address +
                ", keyPair=" + keyPair +
                '}';
    }

    @JsonIgnore
    public CompressedKeyPair getKeyPair() {
        return keyPair;
    }

    public CompressedPublicKey getPublicKey() {
        return keyPair.getPublicKey();
    }

    public CompressedPrivateKey getPrivateKey() {
        return keyPair.getPrivateKey();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), keyPair);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        PrivateIdentity identity = (PrivateIdentity) o;
        return Objects.equals(keyPair, identity.keyPair);
    }

    public Identity toNonPrivate() {
        return Identity.of(address, keyPair.getPublicKey());
    }

    public static PrivateIdentity of(Address address,
                                     CompressedPublicKey publicKey,
                                     CompressedPrivateKey privateKey) {
        return of(address, CompressedKeyPair.of(publicKey, privateKey));
    }

    public static PrivateIdentity of(String address,
                                     CompressedPublicKey publicKey,
                                     CompressedPrivateKey privateKey) {
        return of(Address.of(address), publicKey, privateKey);
    }

    public static PrivateIdentity of(Address address, CompressedKeyPair keyPair) {
        return new PrivateIdentity(address, keyPair);
    }

    public static PrivateIdentity of(String address, String publicKey, String privateKey) throws CryptoException {
        return of(Address.of(address), CompressedKeyPair.of(publicKey, privateKey));
    }
}
