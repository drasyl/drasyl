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
import org.drasyl.cli.tunnel.message.Flush;
import org.drasyl.cli.tunnel.message.Write;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Map;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;

/**
 * TCP client connection to the exposed local service.
 */
public class ExposeTcpHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ExposeTcpHandler.class);
    private final Map<String, ChannelHandlerContext> tcpClients;
    private final ChannelHandlerContext consumerCtx;
    private final String id;
    private boolean closeReceived;

    public ExposeTcpHandler(final Map<String, ChannelHandlerContext> tcpClients,
                            final ChannelHandlerContext consumerCtx,
                            final String id) {
        this.tcpClients = requireNonNull(tcpClients);
        this.consumerCtx = requireNonNull(consumerCtx);
        this.id = requireNonNull(id);
    }

    @SuppressWarnings("java:S1905")
    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        LOG.trace("{}: channelActive", ctx.channel());

        // register channel at parent
        tcpClients.put(id, ctx);
        ctx.channel().closeFuture().addListener((ChannelFutureListener) f -> tcpClients.remove(id));

        // send ChannelActive event to binding node
        final ChannelActive channelActive = new ChannelActive(id);
        LOG.trace("{}: channelActive: Pass `{}` to `{}`.", ctx.channel(), channelActive, consumerCtx.channel().localAddress());
        consumerCtx.pipeline().writeAndFlush(channelActive).addListener(FIRE_EXCEPTION_ON_FAILURE);

        ctx.fireChannelActive().read();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        LOG.trace("{}: channelInactive", ctx.channel());

        if (!closeReceived) {
            // notify binding node that our channel is closing
            final Close msg = new Close(id);
            LOG.trace("{}: channelInactive: Pass `{}` to `{}`.", ctx.channel(), msg, consumerCtx.channel().localAddress());
            consumerCtx.pipeline().writeAndFlush(msg).addListener(FIRE_EXCEPTION_ON_FAILURE);
        }

        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        LOG.trace("{}: channelRead", ctx.channel());

        if (msg instanceof ByteBuf) {
            // pass received data from TCP server to consuming node
            final Write writeMsg = new Write(id, (ByteBuf) msg);
            LOG.trace("{}: channelRead: Pass `{}` to `{}`.", ctx.channel(), writeMsg, consumerCtx.channel().localAddress());
            consumerCtx.pipeline().writeAndFlush(writeMsg).addListener(FIRE_EXCEPTION_ON_FAILURE);
        }
        else {
            ctx.fireChannelRead(ctx);
        }
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        LOG.trace("{}: channelReadComplete", ctx.channel());

        // all data from TCP server received. Inform binding node to allow him to send data.
        final Flush flushMsg = new Flush(id);
        LOG.trace("{}: channelReadComplete: Pass `{}` to `{}`.", ctx.channel(), flushMsg, consumerCtx.channel().localAddress());
        consumerCtx.pipeline().writeAndFlush(flushMsg).addListener(FIRE_EXCEPTION_ON_FAILURE).addListener(f -> {
            if (f.isSuccess()) {
                ctx.fireChannelReadComplete().read();
            }
        });
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
        LOG.trace("{}: userEventTriggered: {}", ctx.channel(), evt);

        if (evt instanceof Write) {
            // pass received data from consuming node to TCP server
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
            ctx.fireUserEventTriggered(ctx);
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        LOG.trace("{}: exceptionCaught:", ctx.channel(), cause);
        ctx.close();
    }
}
