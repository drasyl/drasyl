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
package org.drasyl.handler.oldconnection;

/**
 * Signals that the handshake was completed successfully.
 */
public class ConnectionHandshakeCompleted implements ConnectionHandshakeEvent {
    private final long sndNxt;
    private final long rcvNxt;

    public ConnectionHandshakeCompleted(final long sndNxt, final long rcvNxt) {
        this.sndNxt = sndNxt;
        this.rcvNxt = rcvNxt;
    }

    /**
     * Returns the state that has been shared with the remote peer.
     *
     * @return state that has been shared with the remote peer
     */
    public long sndNxt() {
        return sndNxt;
    }

    /**
     * Returns the state that has been received from the remote peer.
     *
     * @return state that has been received from the remote peer
     */
    public long rcvNxt() {
        return rcvNxt;
    }

    @Override
    public String toString() {
        return "ConnectionHandshakeCompleted{" +
                "sndNxt=" + sndNxt +
                ", rcvNxt=" + rcvNxt +
                '}';
    }
}
