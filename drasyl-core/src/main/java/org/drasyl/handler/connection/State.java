package org.drasyl.handler.connection;

/**
 * States of the handshake progress
 */
enum State {
    // connection does not exist
    CLOSED, // represents no connection state at all.
    // connection non-synchronized
    LISTEN, // represents waiting for a connection request from remote peer.
    SYN_SENT, // represents waiting for a matching connection request after having sent a connection request.
    SYN_RECEIVED, // represents waiting for a confirming connection request acknowledgment after having both received and sent a connection request.
    // connection synchronized
    ESTABLISHED, // represents an open connection, data received can be delivered to the user. The normal state for the data transfer phase of the connection.
    FIN_WAIT_1, // represents waiting for a connection termination request from the remote peer, or an acknowledgment of the connection termination request previously sent.
    FIN_WAIT_2, // represents waiting for a connection termination request from the remote peer.
    CLOSING, // represents waiting for a connection termination request acknowledgment from the remote peer.
    TIME_WAIT, // represents waiting for enough time to pass to be sure the remote peer received the acknowledgment of its connection termination request.
    CLOSE_WAIT, // represents waiting for a connection termination request from the local user.
    LAST_ACK // represents waiting for an acknowledgment of the connection termination request previously sent to the remote peer (which includes an acknowledgment of its connection termination request).
}
