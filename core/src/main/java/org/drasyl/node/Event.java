package org.drasyl.node;

import java.util.Objects;

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

    @Override
    public int hashCode() {
        return Objects.hash(code, node, peer);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Event event = (Event) o;
        return code == event.code &&
                Objects.equals(node, event.node) &&
                Objects.equals(peer, event.peer);
    }

    @Override
    public String toString() {
        return "Event{" +
                "code=" + code +
                ", node=" + node +
                ", peer=" + peer +
                '}';
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
         * This events signals that the identity is already being used by another node on the network.
         */
        NODE_IDENTITY_COLLISION,

        /**
         * This events signals that the node has terminated normally.
         */
        NODE_NORMAL_TERMINATION,

        /**
         * This events signals that the node was not able to deregistrer from super peer.
         */
        NODE_DEREGISTER_FAILED,

        /**
         * This events signals that the node encountered an unrecoverable error.
         */
        NODE_UNRECOVERABLE_ERROR,

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

    public static class Node {
        private final Object address;

        public Node(Object address) {
            this.address = address;
        }

        public Object getAddress() {
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
            Node node = (Node) o;
            return Objects.equals(address, node.address);
        }

        @Override
        public String toString() {
            return "Node{" +
                    "address=" + address +
                    '}';
        }
    }
}
