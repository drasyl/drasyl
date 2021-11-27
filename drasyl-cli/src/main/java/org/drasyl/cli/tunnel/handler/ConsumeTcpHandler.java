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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.cli.tunnel.message.ChannelActive;
import org.drasyl.cli.tunnel.message.Close;
import org.drasyl.cli.tunnel.message.Connect;
import org.drasyl.cli.tunnel.message.ConnectFailed;
import org.drasyl.cli.tunnel.message.Flush;
import org.drasyl.cli.tunnel.message.Write;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Future;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * TCP server connection passing all inbound/outbound traffic through drasyl from/to the exposed
 * service.
 */
class ConsumeTcpHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ConsumeTcpHandler.class);
    public static final int CHANNEL_ACTIVE_TIMEOUT = 10_000;
    private final String password;
    private final Map<String, ChannelHandlerContext> tcpClients;
    private final ChannelHandlerContext exposerCtx;
    private Future<?> channelActiveTimeout;
    private boolean closeReceived;

    public ConsumeTcpHandler(final String password,
                             final Map<String, ChannelHandlerContext> tcpClients,
                             final ChannelHandlerContext exposerCtx) {
        this.password = requireNonNull(password);
        this.tcpClients = requireNonNull(tcpClients);
        this.exposerCtx = requireNonNull(exposerCtx);
    }

    /*
     * Channel Events
     */

    @SuppressWarnings("java:S1905")
    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        LOG.trace("{}: channelActive", ctx.channel());

        // register channel at parent
        tcpClients.put(ctx.channel().id().asLongText(), ctx);
        ctx.channel().closeFuture().addListener((ChannelFutureListener) f -> tcpClients.remove(f.channel().id().asLongText()));

        // send Connect request to exposing node
        final Connect msg = new Connect(ctx.channel(), password);
        LOG.trace("{}: channelActive: Pass `{}` to `{}`.", ctx.channel(), msg, exposerCtx.channel().localAddress());
        exposerCtx.pipeline().writeAndFlush(msg).addListener(FIRE_EXCEPTION_ON_FAILURE);

        // timeout guard
        channelActiveTimeout = ctx.executor().schedule(() -> ctx.fireExceptionCaught(new ChannelActiveTimeoutException(CHANNEL_ACTIVE_TIMEOUT)), CHANNEL_ACTIVE_TIMEOUT, MILLISECONDS);

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        LOG.trace("{}: channelInactive", ctx.channel());

        if (!closeReceived) {
            // notify exposing node that our channel is closing
            final Close msg = new Close(ctx.channel());
            LOG.trace("{}: channelInactive: Pass `{}` to `{}`.", ctx.channel(), msg, exposerCtx.channel().localAddress());
            exposerCtx.pipeline().writeAndFlush(msg).addListener(FIRE_EXCEPTION_ON_FAILURE);
        }

        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        LOG.trace("{}: channelRead", ctx.channel());

        if (msg instanceof ByteBuf) {
            // pass received data from TCP client to exposing node
            final Write writeMsg = new Write(ctx.channel(), (ByteBuf) msg);
            LOG.trace("{}: channelRead: Pass `{}` to `{}`.", ctx.channel(), writeMsg, exposerCtx.channel().localAddress());
            exposerCtx.pipeline().writeAndFlush(writeMsg).addListener(FIRE_EXCEPTION_ON_FAILURE);
        }
        else {
            ctx.fireChannelRead(ctx);
        }
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        LOG.trace("{}: channelReadComplete", ctx.channel());

        // all data from TCP client received. Inform exposing node to allow him to send data.
        final Flush flushMsg = new Flush(ctx.channel());
        LOG.trace("{}: channelReadComplete: Pass `{}` to `{}`.", ctx.channel(), flushMsg, exposerCtx.channel().localAddress());
        exposerCtx.pipeline().writeAndFlush(flushMsg).addListener(FIRE_EXCEPTION_ON_FAILURE).addListener(f -> {
            if (f.isSuccess()) {
                ctx.fireChannelReadComplete().read();
            }
        });
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
        LOG.trace("{}: userEventTriggered: {}", ctx.channel(), evt);

        if (evt instanceof ConnectFailed) {
            ctx.channel().close();
        }
        else if (evt instanceof ChannelActive) {
            LOG.trace("{}: userEventTriggered: channel is active -> read", ctx.channel(), evt);
            channelActiveTimeout.cancel(false);
            ctx.read();
        }
        else if (evt instanceof Write) {
            // pass received data from exposing node to TCP client
            ctx.write(((Write) evt).getMsg()).addListener(FIRE_EXCEPTION_ON_FAILURE);
        }
        else if (evt instanceof Flush) {
            ctx.flush();
        }
        else if (evt instanceof Close) {
            closeReceived = true;
            ctx.close();
        }
        else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        LOG.trace("{}: exceptionCaught:", ctx.channel(), cause);
        ctx.close();
    }

    public static class ChannelActiveTimeoutException extends Exception {
        public ChannelActiveTimeoutException(final long timeoutMillis) {
            super("No ChannelActive event received from exposing node within " + timeoutMillis + "ms.");
        }
    }
}
