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

import java.util.Objects;

/**
 * Represents the public identity of a peer.
 */
public class Identity {
    protected final CompressedPublicKey publicKey;

    private Identity() {
        this.publicKey = null;
    }

    protected Identity(String publicKey) throws CryptoException {
        this.publicKey = CompressedPublicKey.of(publicKey);
    }

    protected Identity(CompressedPublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public CompressedPublicKey getPublicKey() {
        return publicKey;
    }

    public boolean hasPublicKey() {
        return publicKey != null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicKey);
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
        return Objects.equals(publicKey, identity.publicKey);
    }

    @Override
    public String toString() {
        return "Identity{" +
                "publicKey=" + publicKey +
                '}';
    }

    public static Identity of(String publicKey) {
        try {
            return of(CompressedPublicKey.of(publicKey));
        }
        catch (CryptoException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Identity of(CompressedPublicKey publicKey) {
        return new Identity(publicKey);
    }
}
