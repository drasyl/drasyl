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

/**
 * Represents the public identity of a peer (includes address and public key).
 */
public class Identity extends AbstractIdentity {
    protected final CompressedPublicKey publicKey;

    private Identity() {
        super(null);
        publicKey = null;
    }

    protected Identity(Address address, CompressedPublicKey publicKey) {
        super(address);
        this.publicKey = publicKey;

        if (this.publicKey != null && !Address.verify(address, publicKey)) {
            throw new IllegalArgumentException("Address '" + address +"' does not correspond to Public Key '" + publicKey + "'");
        }
    }

    public CompressedPublicKey getPublicKey() {
        return publicKey;
    }

    @Override
    public String toString() {
        return "Identity{" +
                "address=" + address +
                ", publicKey=" + publicKey +
                '}';
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    public boolean hasPublicKey() {
        return publicKey != null;
    }

    public static Identity of(Address address) {
        return new Identity(address, null);
    }

    public static Identity of(String address) {
        return of(Address.of(address));
    }

    public static Identity of(String address, String publicKey) throws CryptoException {
        return new Identity(Address.of(address), CompressedPublicKey.of(publicKey));
    }

    public static Identity of(Address address, String publicKey) throws CryptoException {
        return new Identity(address, CompressedPublicKey.of(publicKey));
    }

    public static Identity of(Address address, CompressedPublicKey publicKey) {
        return new Identity(address, publicKey);
    }
}
