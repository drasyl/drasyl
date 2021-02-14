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
 * This event signals that the node is currently connected to a super peer. This means that it can
 * be contacted by other peers connected to the same super peer. In addition, the super peer can
 * assist in establishing direct connections to other peers.
 * <p>
 * If the node has been configured with no super peer, this event will be never emitted.
 * <p>
 * This is an immutable object.
 *
 * @see NodeOfflineEvent
 * @see PeerDirectEvent
 * @see PeerRelayEvent
 */
public class NodeOnlineEvent extends AbstractNodeEvent {
    /**
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public NodeOnlineEvent(final Node node) {
        super(node);
    }

    @Override
    public String toString() {
        return "NodeOnlineEvent{" +
                "node=" + node +
                '}';
    }
}
