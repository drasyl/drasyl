/*
 * Copyright (c) 2020-2024.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.tcp.TcpServer.TcpServerBound;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.DrasylNodeSharedEventLoopGroupHolder;
import org.drasyl.node.channel.DrasylNodeServerChannelInitializer;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.InboundExceptionEvent;
import org.drasyl.node.event.NodeNormalTerminationEvent;
import org.drasyl.node.event.NodeUpEvent;
import org.drasyl.util.internal.NonNull;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.Queue;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

public class EmbeddedNode extends DrasylNode implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedNode.class);
    private final Queue<Event> events;
    private int port;
    private int tcpFallbackPort;
    private boolean started;

    private EmbeddedNode(final DrasylConfig config,
                         final Queue<Event> events) throws DrasylException {
        super(config);
        this.events = requireNonNull(events);
    }

    public EmbeddedNode(final DrasylConfig config) throws DrasylException {
        this(config, new ArrayDeque<>());

        bootstrap.handler(new DrasylNodeServerChannelInitializer(config, identity, this, DrasylNodeSharedEventLoopGroupHolder.getNetworkGroup()) {
            @Override
            protected void initChannel(final DrasylServerChannel ch) {
                super.initChannel(ch);

                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(final ChannelHandlerContext ctx,
                                                   final Object evt) {
                        if (evt instanceof UdpServer.UdpServerBound) {
                            port = ((UdpServer.UdpServerBound) evt).getBindAddress().getPort();
                        }
                        else if (evt instanceof TcpServerBound) {
                            tcpFallbackPort = ((TcpServerBound) evt).getPort();
                        }
                        else {
                            ctx.fireUserEventTriggered(evt);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onEvent(@NonNull final Event event) {
        if (event instanceof UdpServer.UdpServerBound) {
            port = ((UdpServer.UdpServerBound) event).getBindAddress().getPort();
        }
        else if (event instanceof TcpServerBound) {
            tcpFallbackPort = ((TcpServerBound) event).getPort();
        }
        else if (event instanceof InboundExceptionEvent) {
            LOG.warn("{}", event, ((InboundExceptionEvent) event).getError());
        }

        events.add(event);
    }

    public EmbeddedNode awaitStarted() {
        if (!started) {
            started = true;
            start();
            await("NodeUpEvent").untilAsserted(() -> assertThat(readEvent(), instanceOf(NodeUpEvent.class)));
        }
        return this;
    }

    @Override
    public void close() {
        if (started) {
            started = false;
            shutdown().toCompletableFuture().join();
            // shutdown() future is completed before channelInactive has passed the pipeline...
            await("NodeNormalTerminationEvent").untilAsserted(() -> assertThat(readEvent(), instanceOf(NodeNormalTerminationEvent.class)));
        }
    }

    public int getPort() {
        await("port != 0").atMost(ofSeconds(5_000)).until(() -> port != 0);
        return port;
    }

    public int getTcpFallbackPort() {
        if (tcpFallbackPort == 0) {
            throw new IllegalStateException("TCP Fallback Port not set. You have to start the node running in super peer mode with TCP fallback enabled first.");
        }
        return tcpFallbackPort;
    }

    /**
     * Return received user events from this {@link Channel}
     */
    @SuppressWarnings("unchecked")
    public <T> T readEvent() {
        final T event = (T) events.poll();
        if (event != null) {
            ReferenceCountUtil.touch(event, "Caller of readEvent() will handle the user event from this point");
        }
        return event;
    }

    @Override
    public String toString() {
        return identity.getAddress().toString();
    }
}
