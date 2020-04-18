package org.drasyl.core.models;

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
     * This events signals that the identity is already being used by another node on the
     * network.
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
    PEER_RELAY,

    /**
     * This event signals that the node has received a message addressed to it.
     */
    MESSAGE
}
