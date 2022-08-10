/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
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
    LAST_ACK // represents waiting for an acknowledgment of the connection termination request previously sent to the remote peer (which includes an acknowledgment of its connection termination request).
}
