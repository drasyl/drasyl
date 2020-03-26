package org.drasyl.node;

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
        PEER_RELAY
    }

    public class Peer {
        private final Object address;

        public Peer(Object address) {
            this.address = address;
        }

        public Object getAddress() {
            return address;
        }
    }

    public static class Node {
        private final Object address;

        public Node(Object address) {
            this.address = address;
        }

        public Object getAddress() {
            return address;
        }
    }
}
