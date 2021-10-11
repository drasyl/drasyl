/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.node.event;

import com.google.auto.value.AutoValue;

/**
 * This event signals that the node has established a direct connection to a peer. This means that
 * messages do not have to be relayed through a super peer.
 * <p>
 * This is an immutable object.
 *
 * @see PeerRelayEvent
 * @see NodeOnlineEvent
 * @see NodeOfflineEvent
 */
@AutoValue
@SuppressWarnings({ "java:S118", "java:S1118" })
public abstract class PeerDirectEvent implements PeerEvent {
    /**
     * @throws NullPointerException if {@code peer} is {@code null}
     */
    public static PeerDirectEvent of(final Peer peer) {
        return new AutoValue_PeerDirectEvent(peer);
    }
}
