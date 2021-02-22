/*
 * Copyright (c) 2020-2021.
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

/**
 * This event signals that the node has established a direct connection to a peer. This means that
 * messages do not have to be relayed through a super peer.
 * <p>
 * This is an immutable object.
 *
 * @see PeerRelayEvent
 * @see NodeOnlineEvent
 * @see NodeOfflineEvent
 */
public class PeerDirectEvent extends AbstractPeerEvent {
    /**
     * @throws NullPointerException if {@code peer} is {@code null}
     * @deprecated Use {@link #of(Peer)} instead.
     */
    @Deprecated(since = "0.4.0", forRemoval = true)
    public PeerDirectEvent(final Peer peer) {
        super(peer);
    }

    @Override
    public String toString() {
        return "PeerDirectEvent{" +
                "peer=" + peer +
                '}';
    }

    /**
     * @throws NullPointerException if {@code peer} is {@code null}
     */
    public static PeerDirectEvent of(final Peer peer) {
        return new PeerDirectEvent(peer);
    }
}
