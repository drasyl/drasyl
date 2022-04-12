/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.remote.internet;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.ExpiringMap;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * Re-uses address from messages with unconfirmed peers as last-resort.
 */
public class UnconfirmedAddressResolveHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UnconfirmedAddressResolveHandler.class);
    private final ExpiringMap<DrasylAddress, InetSocketAddress> addressCache;

    public UnconfirmedAddressResolveHandler() {
        this(100, Duration.ofSeconds(60));
    }

    public UnconfirmedAddressResolveHandler(final long maximumSize, final Duration expireAfter) {
        this.addressCache = new ExpiringMap<>(maximumSize, -1, expireAfter.toMillis());
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof InetAddressedMessage<?> && ((InetAddressedMessage<?>) msg).content() instanceof RemoteMessage) {
            addressCache.put(((RemoteMessage) ((InetAddressedMessage<?>) msg).content()).getSender(), ((InetAddressedMessage<?>) msg).sender());
        }

        // pass through
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof OverlayAddressedMessage) {
            final InetSocketAddress address = addressCache.get(((OverlayAddressedMessage<?>) msg).recipient());

            // route to the unconfirmed address
            if (address != null) {
                LOG.trace("Message `{}` was resolved to unconfirmed address `{}`.", () -> msg, () -> address);
                ctx.write(((OverlayAddressedMessage<?>) msg).resolve(address), promise);
                return;
            }
        }

        // pass through
        ctx.write(msg, promise);
    }
}
