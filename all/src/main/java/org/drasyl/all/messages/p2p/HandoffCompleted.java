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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * A message representing a completed handoff to the relay server.
 */
public class HandoffCompleted extends P2PMessage {
    @JsonProperty("sessionUID")
    private final SessionUID clientUID;

    HandoffCompleted() {
        clientUID = null;
    }

    /**
     * Creates a new handoff completed message.
     * 
     * @param clientUID the session UID of the client
     */
    public HandoffCompleted(SessionUID clientUID) {
        this.clientUID = Objects.requireNonNull(clientUID);
    }

    /**
     * @return the session UID of the client
     */
    public SessionUID getSessionUID() {
        return this.clientUID;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientUID);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof HandoffCompleted) {
            HandoffCompleted m2 = (HandoffCompleted) o;

            return Objects.equals(clientUID, m2.clientUID);
        }

        return false;
    }
}
