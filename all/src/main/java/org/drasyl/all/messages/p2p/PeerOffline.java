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
import java.util.Set;

/**
 * A message to inform the other peers which clients were connected to that
 * peer.
 */
public class PeerOffline extends P2PMessage {
    private final Set<SessionUID> clientUIDs;

    PeerOffline() {
        clientUIDs = null;
    }

    /**
     * Creates a new {@link PeerOffline} message to inform the other peers
     * which clients were connected to that peer.
     * 
     * @param clientUIDs the set of locally connected clients
     */
    public PeerOffline(Set<SessionUID> clientUIDs) {
        this.clientUIDs = clientUIDs;
    }

    /**
     * @return the set of locally connected clients
     */
    public Set<SessionUID> getClientUIDs() {
        return clientUIDs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientUIDs);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PeerOffline) {
            PeerOffline m2 = (PeerOffline) o;

            return Objects.equals(clientUIDs, m2.clientUIDs);
        }

        return false;
    }
}