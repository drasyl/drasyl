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
 * Signals that the handshake was complete succesful.
 */
public class ConnectionHandshakeComplete implements ConnectionHandshakeEvent {
    private final int snd_nxt;
    private final int rcv_nxt;

    public ConnectionHandshakeComplete(final int snd_nxt, final int rcv_nxt) {
        this.snd_nxt = snd_nxt;
        this.rcv_nxt = rcv_nxt;
    }

    /**
     * Returns the state that has been shared with the remote peer.
     *
     * @return state that has been shared with the remote peer
     */
    public int snd_nxt() {
        return snd_nxt;
    }

    /**
     * Returns the state that has been received from the remote peer.
     *
     * @return state that has been received from the remote peer
     */
    public int rcv_nxt() {
        return rcv_nxt;
    }

    @Override
    public String toString() {
        return "ConnectionHandshakeComplete{" +
                "snd_nxt=" + snd_nxt +
                ", rcv_nxt=" + rcv_nxt +
                '}';
    }
}
