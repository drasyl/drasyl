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
package org.drasyl.peer.connection.message;

import org.drasyl.identity.CompressedPublicKey;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class RegisterGrandchildMessage extends AbstractMessage implements RequestMessage {
    private final CompressedPublicKey publicKey;
    private final Set<URI> endpoints;

    protected RegisterGrandchildMessage() {
        publicKey = null;
        endpoints = null;
    }

    /**
     * Creates a new register grandchild message.
     *
     * @param publicKey the public key of the new client
     */
    public RegisterGrandchildMessage(CompressedPublicKey publicKey, Set<URI> endpoints) {
        this.publicKey = requireNonNull(publicKey);
        this.endpoints = requireNonNull(endpoints);
    }

    public CompressedPublicKey getPublicKey() {
        return publicKey;
    }

    public Set<URI> getEndpoints() {
        return this.endpoints;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), publicKey, endpoints);
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
        RegisterGrandchildMessage registerGrandchildMessage = (RegisterGrandchildMessage) o;
        return Objects.equals(publicKey, registerGrandchildMessage.publicKey) &&
                Objects.equals(endpoints, registerGrandchildMessage.endpoints);
    }

    @Override
    public String toString() {
        return "RegisterGrandchildMessage{" +
                "publicKey=" + publicKey +
                ", endpoints=" + endpoints +
                ", id='" + id + '\'' +
                '}';
    }
}
