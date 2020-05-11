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
package org.drasyl.core.common.message;

import org.drasyl.core.common.message.action.JoinMessageAction;
import org.drasyl.core.common.message.action.MessageAction;
import org.drasyl.core.models.CompressedPublicKey;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * A message representing a join to the node server.
 */
public class JoinMessage extends AbstractMessageWithUserAgent<JoinMessage> implements UnrestrictedPassableMessage {
    private final CompressedPublicKey publicKey;
    private final Set<URI> endpoints;

    protected JoinMessage() {
        publicKey = null;
        endpoints = null;
    }

    /**
     * Creates a new join message.
     *
     * @param publicKey the public key of the joining node
     * @param endpoints the endpoints of the joining node
     */
    public JoinMessage(CompressedPublicKey publicKey, Set<URI> endpoints) {
        this.publicKey = requireNonNull(publicKey);
        this.endpoints = requireNonNull(endpoints);
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
    public MessageAction<JoinMessage> getAction() {
        return new JoinMessageAction(this);
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
                Objects.equals(endpoints, join.endpoints);
    }

    @Override
    public String toString() {
        return "JoinMessage{" +
                "publicKey=" + publicKey +
                ", endpoints=" + endpoints +
                ", id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }
}
