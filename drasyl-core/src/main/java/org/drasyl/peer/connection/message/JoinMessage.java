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
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * A message representing a join to the node server.
 */
public class JoinMessage extends AbstractMessageWithUserAgent implements RequestMessage {
    private final CompressedPublicKey publicKey;
    private final Set<URI> endpoints;
    private final Map<CompressedPublicKey, Set<URI>> childrenAndGrandchildren;

    protected JoinMessage() {
        publicKey = null;
        endpoints = null;
        childrenAndGrandchildren = null;
    }

    /**
     * Creates a new join message.
     *
     * @param publicKey the public key of the joining node
     * @param endpoints the endpoints of the joining node
     */
    public JoinMessage(CompressedPublicKey publicKey,
                       Set<URI> endpoints,
                       Map<CompressedPublicKey, Set<URI>> childrenAndGrandchildren) {
        this.publicKey = requireNonNull(publicKey);
        this.endpoints = requireNonNull(endpoints);
        this.childrenAndGrandchildren = requireNonNull(childrenAndGrandchildren);
    }

    public Map<CompressedPublicKey, Set<URI>> getChildrenAndGrandchildren() {
        return this.childrenAndGrandchildren;
    }

    public Set<URI> getEndpoints() {
        return this.endpoints;
    }

    public CompressedPublicKey getPublicKey() {
        return this.publicKey;
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
        JoinMessage join = (JoinMessage) o;
        return Objects.equals(publicKey, join.publicKey) &&
                Objects.equals(endpoints, join.endpoints) &&
                Objects.equals(childrenAndGrandchildren, join.childrenAndGrandchildren);
    }

    @Override
    public String toString() {
        return "JoinMessage{" +
                "endpoints=" + endpoints +
                ", childrenAndGrandchildren=" + childrenAndGrandchildren +
                ", id='" + id + '\'' +
                ", publicKey=" + publicKey +
                '}';
    }
}
