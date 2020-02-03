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

public class InitComplete extends P2PMessage {
    private final SessionUID relayUID;

    InitComplete() {
        relayUID = null;
    }

    /**
     * Creates a new init complete message.
     *
     * @param relayUID the relay UID of the new peer
     */
    public InitComplete(SessionUID relayUID) {
        this.relayUID = Objects.requireNonNull(relayUID);
    }

    /**
     * @return the relayUID
     */
    public SessionUID getRelayUID() {
        return relayUID;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof InitComplete) {
            InitComplete m2 = (InitComplete) o;

            return Objects.equals(relayUID, m2.relayUID);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(relayUID);
    }
}
