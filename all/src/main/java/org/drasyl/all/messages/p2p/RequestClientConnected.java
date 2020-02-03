/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.messages.p2p;

import org.drasyl.all.models.SessionUID;

import java.util.Objects;

/**
 * A message to request if a given client is currently connected to the peer.
 */
public class RequestClientConnected extends P2PMessage {
    private final SessionUID clientUID;

    RequestClientConnected() {
        clientUID = null;
    }

    /**
     * Creates a new {@link RequestClientConnected} message.
     * 
     * @param clientUID the clientUID
     */
    public RequestClientConnected(SessionUID clientUID) {
        this.clientUID = Objects.requireNonNull(clientUID);
    }

    /**
     * @return the clientUID
     */
    public SessionUID getClientUID() {
        return clientUID;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientUID);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof RequestClientConnected) {
            RequestClientConnected m2 = (RequestClientConnected) o;

            return Objects.equals(clientUID, m2.clientUID);
        }

        return false;
    }
}
