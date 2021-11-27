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

import io.netty.bootstrap.ServerBootstrap;
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
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.drasyl.cli.tunnel.message.TunnelMessage;
import org.drasyl.handler.codec.MaxLengthFrameDecoder;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import static io.netty.channel.ChannelOption.AUTO_READ;
import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.tunnel.handler.ExposeDrasylHandler.MAX_FRAME_LENGTH;
import static org.drasyl.util.Preconditions.requireNonNegative;

/**
 * Listening on {@code 0.0.0.0}:{@link #port} for new TCP connections and redirects all channel
 * traffic/events to the exposing drasyl node.
 */
public class ConsumeDrasylHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ConsumeDrasylHandler.class);
    private final PrintStream out;
    private final int port;
    private final IdentityPublicKey exposer;
    private final String password;
    private final Map<String, ChannelHandlerContext> tcpClients = new HashMap<>();
    private final ServerBootstrap bootstrap;
    private Channel channel;

    public ConsumeDrasylHandler(final PrintStream out,
                                final int port,
                                final IdentityPublicKey exposer,
                                final String password) {
        this.out = requireNonNull(out);
        this.port = requireNonNegative(port);
        this.exposer = requireNonNull(exposer);
        this.password = requireNonNull(password);
        this.bootstrap = new ServerBootstrap();
    }

    /*
     * Channel Events
     */

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        // prepare TCP server for binding TCP clients
        bootstrap.group((EventLoopGroup) ctx.executor().parent())
                .channel(NioServerSocketChannel.class)
                .childHandler(new BindingDrasylHandlerChannelInitializer(ctx))
                // important to synchronize frontend and backend channels
                .childOption(AUTO_READ, false)
                .bind(port)
                .addListener(new BindingDrasylHandlerBindListener(port, ctx));

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        // ensure TCP server is shut down
        if (channel != null) {
            channel.close();
        }

        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        LOG.trace("{}: channelRead: `{}`", ctx.channel(), msg);

        if (msg instanceof TunnelMessage) {
            // forward message to corresponding client (as event)
            final ChannelHandlerContext clientCtx = tcpClients.get(((TunnelMessage) msg).getChannelId());
            if (clientCtx != null) {
                clientCtx.pipeline().fireUserEventTriggered(msg);
            }
        }
        else {
            // pass through all other messages
            ctx.fireChannelRead(msg);
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

    private class BindingDrasylHandlerChannelInitializer extends ChannelInitializer<Channel> {
        private final ChannelHandlerContext ctx;

        public BindingDrasylHandlerChannelInitializer(final ChannelHandlerContext ctx) {
            this.ctx = requireNonNull(ctx);
        }

        @Override
        protected void initChannel(final Channel ch) throws Exception {
            final ChannelPipeline p = ch.pipeline();
            p.addLast(new MaxLengthFrameDecoder(MAX_FRAME_LENGTH));
            p.addLast(new ConsumeTcpHandler(password, tcpClients, ctx));
            p.addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void exceptionCaught(final ChannelHandlerContext ctx,
                                            final Throwable cause) throws Exception {
                    cause.printStackTrace();
                    ctx.close();
                }
            });
        }
    }

    private class BindingDrasylHandlerBindListener implements ChannelFutureListener {
        private final int port;
        private final ChannelHandlerContext ctx;

        public BindingDrasylHandlerBindListener(final int port, final ChannelHandlerContext ctx) {
            this.port = requireNonNegative(port);
            this.ctx = requireNonNull(ctx);
        }

        @Override
        public void operationComplete(final ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                final Channel myChannel = future.channel();
                out.println("Service exposed by " + exposer + " can now be accessed via local server listening at " + myChannel.localAddress());
                ConsumeDrasylHandler.this.channel = myChannel;
            }
            else {
                ctx.fireExceptionCaught(new BindFailedException("Unable to bind server to port " + port + ".", future.cause()));
            }
        }
    }

    public static class BindFailedException extends Exception {
        public BindFailedException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
