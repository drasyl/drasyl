/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.node.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.EncoderException;
import io.netty.util.AttributeKey;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.channel.IdentityChannel;
import org.drasyl.channel.rs.RustDrasylServerChannel;
import org.drasyl.handler.monitoring.TelemetryHandler;
import org.drasyl.handler.peers.PeersHandler;
import org.drasyl.handler.peers.PeersList;
import org.drasyl.identity.Identity;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.InboundExceptionEvent;
import org.drasyl.node.event.Node;
import org.drasyl.node.event.NodeDownEvent;
import org.drasyl.node.event.NodeNormalTerminationEvent;
import org.drasyl.node.event.NodeUnrecoverableErrorEvent;
import org.drasyl.node.event.NodeUpEvent;
import org.drasyl.node.handler.PeersManagerHandler;
import org.drasyl.node.handler.plugin.PluginsServerHandler;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Initialize the {@link RustDrasylServerChannel} used by {@link DrasylNode}.
 */
@UnstableApi
public class DrasylNodeServerChannelInitializer extends ChannelInitializer<RustDrasylServerChannel> {
    public static final AttributeKey<Supplier<PeersList>> PEERS_LIST_SUPPLIER_KEY = AttributeKey.valueOf("PEERS_LIST_SUPPLIER_KEY");
    private static final boolean TELEMETRY_ENABLED = SystemPropertyUtil.getBoolean("org.drasyl.telemetry.enabled", false);
    private static final boolean TELEMETRY_IP_ENABLED = SystemPropertyUtil.getBoolean("org.drasyl.telemetry.ip.enabled", false);
    private static final int TELEMETRY_INTERVAL_SECONDS = SystemPropertyUtil.getInt("org.drasyl.telemetry.interval", 60);
    private static final URI TELEMETRY_URI;

    static {
        final URI uri;
        try {
            uri = new URI(SystemPropertyUtil.get("org.drasyl.telemetry.uri", "https://ping.drasyl.network/"));
        }
        catch (final URISyntaxException e) {
            throw new RuntimeException(e); // NOSONARR
        }
        TELEMETRY_URI = uri;
    }

    private final DrasylConfig config;
    private final DrasylNode node;

    public DrasylNodeServerChannelInitializer(final DrasylConfig config,
                                              final DrasylNode node) {
        this.config = requireNonNull(config);
        this.node = requireNonNull(node);
    }

    @SuppressWarnings("java:S1188")
    @Override
    protected void initChannel(final RustDrasylServerChannel ch) {
        ch.pipeline().addLast(new NodeLifecycleHeadHandler());

        final PeersHandler peersHandler = new PeersHandler();
        ch.attr(PEERS_LIST_SUPPLIER_KEY).set(peersHandler::getPeers);
        ch.pipeline().addLast(peersHandler);

        ch.pipeline().addLast(new PeersManagerHandler());
        ch.pipeline().addLast(new PluginsServerHandler(config, node.identity()));

        if (TELEMETRY_ENABLED) {
            ch.pipeline().addLast(new TelemetryHandler(TELEMETRY_INTERVAL_SECONDS, TELEMETRY_URI, TELEMETRY_IP_ENABLED));
        }

        ch.pipeline().addLast(new NodeLifecycleTailHandler(node));
    }

    /**
     * Emits {@link NodeNormalTerminationEvent} on {@link #channelActive(ChannelHandlerContext)} and
     * {@link NodeUnrecoverableErrorEvent} or {@link InboundExceptionEvent} on
     * {@link #exceptionCaught(ChannelHandlerContext, Throwable)}. This handler must be placed at
     * the tail of the pipeline.
     */
    private static class NodeLifecycleTailHandler extends ChannelInboundHandlerAdapter {
        private static final Logger LOG = LoggerFactory.getLogger(NodeLifecycleTailHandler.class);
        private final DrasylNode node;
        private boolean errorOccurred;

        NodeLifecycleTailHandler(final DrasylNode node) {
            this.node = requireNonNull(node);
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            ctx.fireChannelActive();

            LOG.info("drasyl node with address `{}` has started", ctx.channel().localAddress());
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            ctx.fireChannelInactive();

            if (!errorOccurred) {
                userEventTriggered(ctx, NodeNormalTerminationEvent.of(Node.of(node.identity())));
                LOG.info("drasyl node with address `{}` has shut down", ctx.channel().localAddress());
            }
        }

        @SuppressWarnings("java:S2221")
        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx,
                                       final Object evt) {
            if (evt instanceof Event) {
                if (evt instanceof NodeUnrecoverableErrorEvent) {
                    errorOccurred = true;
                }
                else if (errorOccurred && evt instanceof NodeDownEvent) {
                    // swallow event
                    return;
                }

                node.onEvent((Event) evt);
            }
            else {
                ctx.fireUserEventTriggered(evt);
            }
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx,
                                    final Throwable e) {
            if (e instanceof EncoderException) {
                LOG.error(e);
            }
            else {
                userEventTriggered(ctx, InboundExceptionEvent.of(e));
            }
        }
    }

    /**
     * Emits {@link NodeUpEvent} on {@link #channelActive(ChannelHandlerContext)} and
     * {@link NodeDownEvent} on {@link #channelInactive(ChannelHandlerContext)}. This handler must
     * be placed at the head of the pipeline.
     */
    private static class NodeLifecycleHeadHandler extends ChannelInboundHandlerAdapter {
        private static final Logger LOG = LoggerFactory.getLogger(NodeLifecycleHeadHandler.class);
        Identity identity;

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            if (identity == null) {
                identity = ((IdentityChannel) ctx.channel()).identity();
            }

            LOG.info("Start drasyl node with address `{}`...", ctx.channel().localAddress());
            ctx.fireUserEventTriggered(NodeUpEvent.of(Node.of(identity)));
            ctx.fireChannelActive();
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            LOG.info("Shutdown drasyl node with address `{}`...", ctx.channel().localAddress());
            ctx.fireUserEventTriggered(NodeDownEvent.of(Node.of(identity)));
            ctx.fireChannelInactive();
        }
    }
}
