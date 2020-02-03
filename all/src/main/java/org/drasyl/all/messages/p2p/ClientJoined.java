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

import org.drasyl.all.models.SessionChannel;
import org.drasyl.all.models.SessionUID;

import java.util.Objects;
import java.util.Set;

/**
 * A message representing an information for the relay network, that a client
 * joined this specific relay server.
 */
public class ClientJoined extends P2PMessage {
    private final SessionUID clientUID;
    private final Set<SessionChannel> sessionChannels;

    ClientJoined() {
        clientUID = null;
        sessionChannels = null;
    }

    /**
     * Creates a new {@link ClientJoined} message.
     * 
     * @param clientUID the session UID of the client
     * @param sessionChannels  the client channels
     */
    public ClientJoined(SessionUID clientUID, Set<SessionChannel> sessionChannels) {
        this.clientUID = Objects.requireNonNull(clientUID);
        this.sessionChannels = Objects.requireNonNull(sessionChannels);
    }

    /**
     * @return the session UID of the client
     */
    public SessionUID getClientUID() {
        return clientUID;
    }

    /**
     * @return the channels
     */
    public Set<SessionChannel> getSessionChannels() {
        return sessionChannels;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientUID, sessionChannels);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ClientJoined) {
            ClientJoined m2 = (ClientJoined) o;

            return Objects.equals(clientUID, m2.clientUID) && Objects.equals(sessionChannels, m2.sessionChannels);
        }

        return false;
    }
}
