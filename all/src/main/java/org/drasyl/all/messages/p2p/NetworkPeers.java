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

import org.drasyl.all.models.IPAddress;
import org.drasyl.all.models.SessionChannel;
import org.drasyl.all.models.SessionUID;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A message containing a map with all peers and clients in the network.
 */
public class NetworkPeers extends P2PMessage {
    private final Map<SessionUID, IPAddress> peers;
    private final Map<SessionUID, Set<SessionChannel>> clients;

    public NetworkPeers() {
        peers = null;
        clients = null;
    }

    /**
     * Creates a message containing a map with all peers in the network.
     * 
     * @param peers      the map containing all peers in the network and their
     *                   IPAddresses
     * @param clients the clients with the corresponding channels
     */
    public NetworkPeers(Map<SessionUID, IPAddress> peers, Map<SessionUID, Set<SessionChannel>> clients) {
        this.peers = Objects.requireNonNull(peers);
        this.clients = Objects.requireNonNull(clients);
    }

    /**
     * @return the peers
     */
    public Map<SessionUID, IPAddress> getPeers() {
        return peers;
    }

    /**
     * @return the clientUIDs
     */
    public Map<SessionUID, Set<SessionChannel>> getClients() {
        return clients;
    }

    @Override
    public String toString() {
        return "NetworkPeers [peers=" + peers + ", clients=" + clients + ", messageID=" + getMessageID() + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NetworkPeers)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        NetworkPeers that = (NetworkPeers) o;
        return Objects.equals(getPeers(), that.getPeers()) &&
                Objects.equals(getClients(), that.getClients());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getPeers(), getClients());
    }
}
