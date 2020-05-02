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
 * A message representing the welcome message of the node server, including fallback information and
 * the public key of the node server.
 */
public class Welcome extends UserAgentMessage {
    private final CompressedPublicKey publicKey;
    private final Set<URI> endpoints;

    protected Welcome() {
        publicKey = null;
        endpoints = null;
    }

    /**
     * Creates new welcome message.
     *
     * @param publicKey the public key of the node server
     * @param endpoints the endpoints of the node server
     */
    public Welcome(CompressedPublicKey publicKey, Set<URI> endpoints) {
        Objects.requireNonNull(publicKey);
        Objects.requireNonNull(endpoints);

        this.publicKey = publicKey;
        this.endpoints = endpoints;
    }

    public CompressedPublicKey getPublicKey() {
        return this.publicKey;
    }

    public Set<URI> getEndpoints() {
        return this.endpoints;
    }

    @Override
    public String toString() {
        return "Welcome{" +
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
        Welcome welcome = (Welcome) o;
        return Objects.equals(publicKey, welcome.publicKey) &&
                Objects.equals(endpoints, welcome.endpoints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), publicKey, endpoints);
    }
}
