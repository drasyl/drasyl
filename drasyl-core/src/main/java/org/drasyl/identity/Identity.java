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

import org.drasyl.crypto.CryptoException;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.SecretUtil.maskSecret;

public class Identity {
    private final Address address;
    private final CompressedPublicKey publicKey;
    private final CompressedPrivateKey privateKey;

    Identity(Address address,
             CompressedPublicKey publicKey,
             CompressedPrivateKey privateKey) {
        this.address = requireNonNull(address);
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public CompressedPublicKey getPublicKey() {
        return publicKey;
    }

    public CompressedPrivateKey getPrivateKey() {
        return privateKey;
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
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
        return Objects.equals(address, identity.address);
    }

    @Override
    public String toString() {
        return "Identity{" +
                "address=" + address +
                ", publicKey=" + publicKey +
                ", privateKey=" + maskSecret(privateKey) +
                '}';
    }

    public CompressedKeyPair getKeyPair() {
        return CompressedKeyPair.of(publicKey, privateKey);
    }

    public Address getAddress() {
        return address;
    }

    public static Identity of(Address address) {
        return new Identity(address, null, null);
    }

    public static Identity of(CompressedPublicKey publicKey) {
        return of(publicKey, null);
    }

    public static Identity of(CompressedPublicKey publicKey, CompressedPrivateKey privateKey) {
        return of(CompressedKeyPair.of(publicKey, privateKey));
    }

    public static Identity of(CompressedKeyPair keyPair) {
        return new Identity(Address.of(keyPair.getPublicKey()), keyPair.getPublicKey(), keyPair.getPrivateKey());
    }

    public static Identity of(String publicKey, String privateKey) throws CryptoException {
        return of(CompressedKeyPair.of(publicKey, privateKey));
    }

    public static Identity of(PublicKey publicKey, PrivateKey privateKey) throws CryptoException {
        return of(CompressedKeyPair.of(publicKey, privateKey));
    }
}
