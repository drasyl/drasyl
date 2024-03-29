/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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

import io.netty.channel.Channel;
import org.drasyl.util.internal.UnstableApi;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.State.CLOSE_WAIT;

/**
 * Signals that the connection is closing. The closing have been either initiated locally (by a
 * preceding {@link Channel#close()} call) or by the remote peer. In the latter case, the close
 * request must be "confirmed" by calling {@link Channel#close()}.
 */
@UnstableApi
public class ConnectionClosing implements ConnectionEvent {
    private final State state;

    ConnectionClosing(final State state) {
        this.state = requireNonNull(state);
    }

    public boolean initatedByRemotePeer() {
        return state == CLOSE_WAIT;
    }

    @Override
    public String toString() {
        return "ConnectionClosing{" +
                "initatedByRemotePeer=" + initatedByRemotePeer() +
                '}';
    }
}
