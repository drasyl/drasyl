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
package org.drasyl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.annotation.NonNull;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.tcp.TcpServer;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.channel.DrasylNodeServerChannelInitializer;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.InboundExceptionEvent;
import org.drasyl.node.event.NodeNormalTerminationEvent;
import org.drasyl.node.event.NodeUpEvent;
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

        bootstrap.handler(new DrasylNodeServerChannelInitializer(config, identity, this) {
            @Override
            protected void initChannel(final DrasylServerChannel ch) {
                super.initChannel(ch);

                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(final ChannelHandlerContext ctx,
                                                   final Object evt) throws Exception {
                        if (evt instanceof UdpServer.Port) {
                            port = ((UdpServer.Port) evt).getPort();
                        }
                        else if (evt instanceof TcpServer.Port) {
                            tcpFallbackPort = ((TcpServer.Port) evt).getPort();
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
        if (event instanceof UdpServer.Port) {
            port = ((UdpServer.Port) event).getPort();
        }
        else if (event instanceof TcpServer.Port) {
            tcpFallbackPort = ((TcpServer.Port) event).getPort();
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
            await().untilAsserted(() -> assertThat(readEvent(), instanceOf(NodeUpEvent.class)));
        }
        return this;
    }

    @Override
    public void close() {
        if (started) {
            started = false;
            shutdown().toCompletableFuture().join();
            // shutdown() future is completed before channelInactive has passed the pipeline...
            await().untilAsserted(() -> assertThat(readEvent(), instanceOf(NodeNormalTerminationEvent.class)));
        }
    }

    public int getPort() {
        await().atMost(ofSeconds(5_000)).until(() -> port != 0);
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
