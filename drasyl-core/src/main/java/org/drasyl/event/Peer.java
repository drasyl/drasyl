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
package org.drasyl.event;

import org.drasyl.identity.Identity;

import java.util.Objects;

/**
 * Used by {@link Event} to describe an event related to a Peer (e.g. {@link
 * EventCode#EVENT_PEER_DIRECT}, {@link EventCode#EVENT_PEER_DIRECT}).
 */
public class Peer {
    private final Identity address;

    public Peer(Identity address) {
        this.address = address;
    }

    public Identity getAddress() {
        return address;
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Peer peer = (Peer) o;
        return Objects.equals(address, peer.address);
    }

    @Override
    public String toString() {
        return "Peer{" +
                "address=" + address +
                '}';
    }
}
