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

import org.drasyl.all.messages.UnrestrictedPassableMessage;
import org.drasyl.all.models.SessionUID;
import org.drasyl.all.tools.NetworkTool;

import java.util.Objects;

/**
 * A message containing a relay UID of a relay server that has joined the
 * network.
 */
public class PeerJoined extends P2PMessage implements UnrestrictedPassableMessage {
    private final SessionUID relayUID;
    private final int port;

    PeerJoined() {
        relayUID = null;
        port = 0;
    }

    /**
     * Creates a new peer joined message.
     * 
     * @param relayUID the relay UID of the new peer
     * @param port     the port to connect to
     * @throws IllegalArgumentException is thrown if the port number is out of range
     */
    public PeerJoined(SessionUID relayUID, int port) {
        if (!NetworkTool.isValidPort(port))
            throw new IllegalArgumentException("Invalid port: " + port);

        this.relayUID = Objects.requireNonNull(relayUID);
        this.port = port;
    }

    /**
     * @return the relayUID
     */
    public SessionUID getRelayUID() {
        return relayUID;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    @Override
    public int hashCode() {
        return Objects.hash(relayUID, port);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PeerJoined) {
            PeerJoined m2 = (PeerJoined) o;

            return Objects.equals(relayUID, m2.relayUID) && Objects.equals(port, m2.port);
        }

        return false;
    }
}
