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
package org.drasyl.event;

/**
 * This event signals that the node is currently connected to a super peer. This means that it can
 * be contacted by other peers connected to the same super peer. In addition, the super peer can
 * assist in establishing direct connections to other peers.
 * <p>
 * If the node has been configured with no super peer, this event will be never emitted.
 * <p>
 * This is an immutable object.
 *
 * @see NodeOfflineEvent
 * @see PeerDirectEvent
 * @see PeerRelayEvent
 */
public class NodeOnlineEvent extends AbstractNodeEvent {
    /**
     * @throws NullPointerException if {@code node} is {@code null}
     * @deprecated Use {@link #of(Node)} instead.
     */
    // make method private on next release
    @Deprecated(since = "0.4.0", forRemoval = true)
    public NodeOnlineEvent(final Node node) {
        super(node);
    }

    @Override
    public String toString() {
        return "NodeOnlineEvent{" +
                "node=" + node +
                '}';
    }

    /**
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public static NodeOnlineEvent of(final Node node) {
        return new NodeOnlineEvent(node);
    }
}
