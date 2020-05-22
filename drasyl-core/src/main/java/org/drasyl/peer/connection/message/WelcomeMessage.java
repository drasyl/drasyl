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

import com.fasterxml.jackson.annotation.JsonInclude;
import org.drasyl.identity.CompressedPublicKey;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * A message representing the welcome message of the node server, including fallback information and
 * the public key of the node server.
 */
public class WelcomeMessage extends AbstractMessageWithUserAgent<WelcomeMessage> implements ResponseMessage<JoinMessage, WelcomeMessage> {
    private final CompressedPublicKey publicKey;
    private final Set<URI> endpoints;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String correspondingId;

    protected WelcomeMessage() {
        publicKey = null;
        endpoints = null;
        correspondingId = null;
    }

    /**
     * Creates new welcome message.
     *
     * @param publicKey       the public key of the node server
     * @param endpoints       the endpoints of the node server
     * @param correspondingId
     */
    public WelcomeMessage(CompressedPublicKey publicKey,
                          Set<URI> endpoints,
                          String correspondingId) {
        this.publicKey = requireNonNull(publicKey);
        this.endpoints = requireNonNull(endpoints);
        this.correspondingId = correspondingId;
    }

    public CompressedPublicKey getPublicKey() {
        return this.publicKey;
    }

    public Set<URI> getEndpoints() {
        return this.endpoints;
    }

    @Override
    public String getCorrespondingId() {
        return correspondingId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), publicKey, endpoints, correspondingId);
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
        WelcomeMessage that = (WelcomeMessage) o;
        return Objects.equals(publicKey, that.publicKey) &&
                Objects.equals(endpoints, that.endpoints) &&
                Objects.equals(correspondingId, that.correspondingId);
    }

    @Override
    public String toString() {
        return "WelcomeMessage{" +
                "publicKey=" + publicKey +
                ", endpoints=" + endpoints +
                ", correspondingId='" + correspondingId + '\'' +
                ", id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }
}
