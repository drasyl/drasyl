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

package org.drasyl.core.node.models;

import org.drasyl.core.crypto.CompressedPublicKey;

public class Event {
    private final Code code;
    private final Node node;
    private final Peer peer;

    public Event(Code code, Node node, Peer peer) {
        this.code = code;
        this.node = node;
        this.peer = peer;
    }

    public Code getCode() {
        return code;
    }

    public Node getNode() {
        return node;
    }

    public Peer getPeer() {
        return peer;
    }

    public enum Code {
        /**
         * This event signals that the node has successfully registered with the super peer.
         */
        NODE_ONLINE,

        /**
         * This event signals that the node is currently not connected to a super peer.
         */
        NODE_OFFLINE,

        /**
         * This event signals that the node has established a direct connection to a peer.
         */
        PEER_P2P,

        /**
         * This event signals that the node has established a connection via a relay to a peer.
         */
        PEER_RELAY,

        /**
         * This event signals that the node can't establish a connection to the super peer.
         */
        PEER_UNREACHABLE,

        /**
         * This event signals that the address of the node is already taken.
         */
        NODE_IDENTITY_COLLISION,

        /**
         * This event signals that the node was terminated in a normal manner.
         */
        NODE_NORMAL_TERMINATION,

        /**
         * This event signals that the node encountered a non fixable error.
         */
        NODE_UNRECOVERABLE_ERROR,

    }

    public class Peer {
        private final CompressedPublicKey address;

        public Peer(CompressedPublicKey address) {
            this.address = address;
        }

        public CompressedPublicKey getAddress() {
            return address;
        }
    }

    public static class Node {
        private final CompressedPublicKey address;

        public Node(CompressedPublicKey address) {
            this.address = address;
        }

        public CompressedPublicKey getAddress() {
            return address;
        }
    }
}
