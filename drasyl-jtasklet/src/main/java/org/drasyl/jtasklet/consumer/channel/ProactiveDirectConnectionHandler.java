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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.channel.NoopDiscardHandler;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ProactiveDirectConnectionHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ProactiveDirectConnectionHandler.class);
    private static final long PERIOD = 1_000L;
    private final List<IdentityPublicKey> peers;
    private final Map<IdentityPublicKey, DrasylChannel> channelMap;

    public ProactiveDirectConnectionHandler(final List<IdentityPublicKey> peers) {
        this.peers = peers;
        this.channelMap = new HashMap<>();
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
        ctx.executor().scheduleAtFixedRate(() -> {
            for (final IdentityPublicKey peer : peers) {
                final ByteBuf msg = ctx.alloc().buffer(Long.BYTES).writeLong(NoopDiscardHandler.MAGIC_NUMBER);
                final OverlayAddressedMessage<ByteBuf> addressedMessage = new OverlayAddressedMessage<>(msg, peer, (DrasylAddress) ctx.channel().localAddress());

                /*
                DrasylChannel channel = channelMap.get(peer);

                if (channel == null || !channel.isOpen()) {
                    channel = new DrasylChannel((DrasylServerChannel) ctx.channel(), peer);

                    channelMap.put(peer, channel);
                    ctx.pipeline().fireChannelRead(channel);
                }*/

                ctx.writeAndFlush(addressedMessage).addListener((f) -> {
                    if(!f.isSuccess()) {
                        LOG.error("ERRRORRRR!! ", f.cause());
                    }
                });
            }
        }, 0, PERIOD, TimeUnit.MILLISECONDS);
    }
}
