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
package org.drasyl.jtasklet.consumer.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.remote.internet.UnconfirmedAddressResolveHandler;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ProactiveDirectConnectionHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ProactiveDirectConnectionHandler.class);
    private static final long PERIOD = 30_000L;
    private static final long MAGIC_NUMBER = 8989898989L;
    private final List<IdentityPublicKey> peers;

    public ProactiveDirectConnectionHandler(final List<IdentityPublicKey> peers) {
        this.peers = peers;
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
        ctx.executor().scheduleAtFixedRate(() -> {
            for (final IdentityPublicKey peer : peers) {
                final OverlayAddressedMessage<ByteBuf> msg = new OverlayAddressedMessage<>(ctx.alloc().buffer(Long.BYTES).writeLong(MAGIC_NUMBER), peer);
                ctx.write(msg).addListener((ChannelFutureListener) channelFuture -> {
                    if (channelFuture.cause() != null) {
                        LOG.error("Failed to write ``{}: ", msg, channelFuture.cause());
                    }
                });
            }
            ctx.flush();
        }, 0, PERIOD, TimeUnit.MILLISECONDS);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof ByteBuf) {
            final ByteBuf buf = (ByteBuf) msg;

            if (buf.readableBytes() == Long.BYTES) {
                buf.markReaderIndex();
                final long number = buf.readLong();
                if (number == MAGIC_NUMBER) {
                    buf.release();
                    return;
                }
                else {
                    buf.resetReaderIndex();
                }
            }
        }

        ctx.fireChannelRead(msg);
    }
}
