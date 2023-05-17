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
 * Connection states.
 */
public enum State {
    // RFC 9293: represents waiting for a connection request from any remote TCP peer and port.
    LISTEN,
    // RFC 9293: represents waiting for a matching connection request after having sent a connection
    // RFC 9293: request.
    SYN_SENT,
    // RFC 9293: represents waiting for a confirming connection request acknowledgment after having
    // RFC 9293: both received and sent a connection request.
    SYN_RECEIVED,
    // RFC 9293: represents an open connection, data received can be delivered to the user. The
    // RFC 9293: normal state for the data transfer phase of the connection.
    ESTABLISHED,
    // RFC 9293: represents waiting for a connection termination request from the remote TCP peer,
    // RFC 9293: or an acknowledgment of the connection termination request previously sent.
    FIN_WAIT_1,
    // RFC 9293: represents waiting for a connection termination request from the remote TCP peer.
    FIN_WAIT_2,
    // RFC 9293: represents waiting for a connection termination request from the local user.
    CLOSE_WAIT,
    // RFC 9293: represents waiting for a connection termination request acknowledgment from the
    // RFC 9293: remote TCP peer.
    CLOSING,
    // RFC 9293: represents waiting for an acknowledgment of the connection termination request
    // RFC 9293: previously sent to the remote TCP peer (this termination request sent to the remote
    // RFC 9293: TCP peer already included an acknowledgment of the termination request sent from
    // RFC 9293: the remote TCP peer).
    LAST_ACK,
    // RFC 9293: represents waiting for enough time to pass to be sure the remote TCP peer received
    // RFC 9293: the acknowledgment of its connection termination request and to avoid new
    // RFC 9293: connections being impacted by delayed segments from previous connections.
    TIME_WAIT,
    // RFC 9293: represents no connection state at all.
    CLOSED;

    /**
     * Is connection in a synchronized state?
     */
    boolean synchronizedConnection() {
        return this == ESTABLISHED || this == FIN_WAIT_1 || this == FIN_WAIT_2 ||
                this == CLOSE_WAIT || this == CLOSING || this == LAST_ACK || this == TIME_WAIT;
    }
}
