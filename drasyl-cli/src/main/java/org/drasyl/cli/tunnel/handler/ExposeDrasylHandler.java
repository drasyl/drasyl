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
package org.drasyl.cli.tunnel.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.drasyl.cli.tunnel.message.Connect;
import org.drasyl.cli.tunnel.message.ConnectFailed;
import org.drasyl.cli.tunnel.message.TunnelMessage;
import org.drasyl.handler.codec.MaxLengthFrameDecoder;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static io.netty.channel.ChannelOption.AUTO_READ;
import static java.util.Objects.requireNonNull;

/**
 * Creates new TCP client connecting to {@link #service} once a {@link Connect} message with correct
 * password has been received.
 */
public class ExposeDrasylHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ExposeDrasylHandler.class);
    public static final int MAX_FRAME_LENGTH = 1200;
    private final String password;
    private final InetSocketAddress service;
    private final EventLoopGroup group;
    private final Map<String, ChannelHandlerContext> tcpClients = new HashMap<>();
    private final Bootstrap bootstrap;

    /**
     * @param group the {@link EventLoopGroup} the underlying tcp client should run on
     */
    public ExposeDrasylHandler(final String password,
                               final InetSocketAddress service,
                               final EventLoopGroup group) {
        this.password = requireNonNull(password);
        this.service = requireNonNull(service);
        this.group = requireNonNull(group);
        bootstrap = new Bootstrap();
    }

    /*
     * Channel Events
     */

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isActive()) {
            completeBootstrap(ctx);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        completeBootstrap(ctx);
        ctx.fireChannelActive();
    }

    private void completeBootstrap(final ChannelHandlerContext ctx) {
        // prepare TCP clients for connecting to exposing service
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                // important to synchronize frontend and backend channels
                .option(AUTO_READ, false);
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        // ensure TCP clients are shut down
        for (final Iterator<ChannelHandlerContext> iter = tcpClients.values().iterator();
             iter.hasNext(); ) {
            final ChannelHandlerContext clientCtx = iter.next();
            clientCtx.channel().close();
        }

        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof TunnelMessage) {
            channelRead0(ctx, (TunnelMessage) msg);
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    protected void channelRead0(final ChannelHandlerContext ctx,
                                final TunnelMessage msg) {
        LOG.trace("{}: channelRead: `{}`", ctx.channel(), msg);

        if (msg instanceof Connect) {
            if (!password.equals(((Connect) msg).getPassword())) {
                LOG.trace("{}: channelRead: Password wrong!", ctx.channel());
            }
            else if (!tcpClients.containsKey(msg.getChannelId())) {
                LOG.trace("{}: channelRead: New tunnel `{}` requested. Establish TCP connection...", ctx.channel(), msg.getChannelId());
                bootstrap.handler(new ExposingDrasylHandlerChannelInitializer(ctx, (Connect) msg))
                        .connect(service)
                        .addListener(new ExposingDrasylHandlerConnectListener(ctx, msg.getChannelId()));
            }
        }
        else {
            // forward message to corresponding client (as event)
            final ChannelHandlerContext clientCtx = tcpClients.get(msg.getChannelId());
            if (clientCtx != null) {
                clientCtx.pipeline().fireUserEventTriggered(msg);
            }
        }
    }

    /*
     * Signals
     */

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        LOG.trace("{}: write: `{}`.", ctx.channel(), msg);
        ctx.write(msg, promise);
    }

    /*
     * Inner Classes
     */

    private class ExposingDrasylHandlerChannelInitializer extends ChannelInitializer<Channel> {
        private final ChannelHandlerContext ctx;
        private final Connect msg;

        public ExposingDrasylHandlerChannelInitializer(final ChannelHandlerContext ctx,
                                                       final Connect msg) {
            this.ctx = requireNonNull(ctx);
            this.msg = requireNonNull(msg);
        }

        @Override
        protected void initChannel(final Channel ch) throws Exception {
            final ChannelPipeline p = ch.pipeline();
            p.addLast(new MaxLengthFrameDecoder(MAX_FRAME_LENGTH));
            p.addLast(new ExposeTcpHandler(tcpClients, ctx, msg.getChannelId()));
            p.addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void exceptionCaught(final ChannelHandlerContext ctx,
                                            final Throwable cause) {
                    cause.printStackTrace();
                    ctx.close();
                }
            });
        }
    }

    private static class ExposingDrasylHandlerConnectListener implements ChannelFutureListener {
        private final ChannelHandlerContext ctx;
        private final String channelId;

        public ExposingDrasylHandlerConnectListener(final ChannelHandlerContext ctx,
                                                    final String channelId) {
            this.ctx = requireNonNull(ctx);
            this.channelId = requireNonNull(channelId);
        }

        @Override
        public void operationComplete(final ChannelFuture future) {
            if (!future.isSuccess()) {
                LOG.trace("Unable to connect:", future.cause());
                ctx.pipeline().writeAndFlush(new ConnectFailed(channelId)).addListener(FIRE_EXCEPTION_ON_FAILURE);
            }
        }
    }
}
