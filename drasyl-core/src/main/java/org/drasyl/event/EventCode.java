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

/**
 * Is used by {@link Event} to define the type of an Event.
 */
public enum EventCode {
    /**
     * This event signals that the node has been started.
     */
    EVENT_NODE_UP,

    /**
     * This event signals that the node is shut down.
     */
    EVENT_NODE_DOWN,

    /**
     * This event signals that the node has successfully registered with the super peer. If a node
     * has been configured with no super peer (e.g. if it is a root node), the event is immediately
     * emitted.
     */
    EVENT_NODE_ONLINE,

    /**
     * This event signals that the node is currently not connected to a super peer.
     */
    EVENT_NODE_OFFLINE,

    /**
     * This events signals that the identity is already being used by another node on the network.
     */
    EVENT_NODE_IDENTITY_COLLISION,

    /**
     * This events signals that the node has terminated normally.
     */
    EVENT_NODE_NORMAL_TERMINATION,

    /**
     * This events signals that the node encountered an unrecoverable error.
     */
    EVENT_NODE_UNRECOVERABLE_ERROR,

    /**
     * This event signals that the node has established a direct connection to a peer.
     */
    EVENT_PEER_P2P,

    /**
     * This event signals that the node has established a connection via a relay to a peer.
     */
    EVENT_PEER_RELAY,

    /**
     * This event signals that the node has received a message addressed to it.
     */
    EVENT_MESSAGE;

    /**
     * Returns <code>true</code> if this code refers to a node. Otherwise <code>false</code> is
     * returned.
     *
     * @return
     */
    public boolean isNodeEvent() {
        return this.name().startsWith("EVENT_NODE_");
    }

    /**
     * Returns <code>true</code> if this code refers to a peer. Otherwise <code>false</code> is
     * returned.
     *
     * @return
     */
    public boolean isPeerEvent() {
        return this.name().startsWith("EVENT_PEER_");
    }

    /**
     * Returns <code>true</code> if this code refers to a message. Otherwise <code>false</code> is
     * returned.
     *
     * @return
     */
    public boolean isMessageEvent() {
        return this.name().equals("EVENT_MESSAGE");
    }
}
