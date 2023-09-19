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
package org.drasyl.node;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import org.drasyl.identity.Identity;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.InboundExceptionEvent;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.event.NodeEvent;
import org.drasyl.node.event.PeerEvent;

/**
 * An implementation of {@link DrasylNode} that provides distinct methods for handling various
 * {@link Event} types. These include:
 * <ul>
 * <li>{@link #onInboundException(InboundExceptionEvent)}</li>
 * <li>{@link #onNodeEvent(NodeEvent)}</li>
 * <li>{@link #onPeerEvent(PeerEvent)}</li>
 * <li>{@link #onMessage(MessageEvent)}</li>
 * <li>{@link #onAnyOtherEvent(Event)}</li>
 * </ul>
 */
public abstract class EventTypeDrasylNode extends DrasylNode {
    protected EventTypeDrasylNode(final Identity identity,
                                  final ServerBootstrap bootstrap,
                                  final ChannelFuture channelFuture) {
        super(identity, bootstrap, channelFuture);
    }

    protected EventTypeDrasylNode(final DrasylConfig config) throws DrasylException {
        super(config);
    }

    protected EventTypeDrasylNode() throws DrasylException {
        super();
    }

    @Override
    public void onEvent(final Event event) {
        if (event instanceof InboundExceptionEvent) {
            onInboundException((InboundExceptionEvent) event);
        }
        else if (event instanceof NodeEvent) {
            onNodeEvent((NodeEvent) event);
        }
        else if (event instanceof PeerEvent) {
            onPeerEvent((PeerEvent) event);
        }
        else if (event instanceof MessageEvent) {
            onMessage((MessageEvent) event);
        }
        else {
            onAnyOtherEvent(event);
        }
    }

    protected void onInboundException(final InboundExceptionEvent event) {
        // do nothing
    }

    protected void onNodeEvent(final NodeEvent event) {
        // do nothing
    }

    protected void onPeerEvent(final PeerEvent event) {
        // do nothing
    }

    protected void onMessage(final MessageEvent event) {
        // do nothing
    }

    protected void onAnyOtherEvent(final Event event) {
        // do nothing
    }
}
