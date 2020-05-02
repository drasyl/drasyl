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
package org.drasyl.core.common.messages;

import org.drasyl.core.models.CompressedPublicKey;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

/**
 * A message representing a join to the node server.
 */
public class Join extends UserAgentMessage implements UnrestrictedPassableMessage {
    private final CompressedPublicKey publicKey;
    private final Set<URI> endpoints;

    protected Join() {
        publicKey = null;
        endpoints = null;
    }

    /**
     * Creates a new join message.
     *
     * @param publicKey the public key of the joining node
     * @param endpoints the endpoints of the joining node
     */
    public Join(CompressedPublicKey publicKey, Set<URI> endpoints) {
        Objects.requireNonNull(publicKey);
        Objects.requireNonNull(endpoints);

        this.publicKey = publicKey;
        this.endpoints = endpoints;
    }

    public Set<URI> getEndpoints() {
        return this.endpoints;
    }

    /**
     * @return the public key of the joining node
     */
    public CompressedPublicKey getPublicKey() {
        return publicKey;
    }

    @Override
    public String toString() {
        return "Join{" +
                "messageID=" + getMessageID() +
                ", User-Agent=" + getUserAgent() +
                ", publicKey=" + publicKey +
                ", endpoints=" + endpoints +
                '}';
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
        Join join = (Join) o;
        return Objects.equals(publicKey, join.publicKey) &&
                Objects.equals(endpoints, join.endpoints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), publicKey, endpoints);
    }
}
