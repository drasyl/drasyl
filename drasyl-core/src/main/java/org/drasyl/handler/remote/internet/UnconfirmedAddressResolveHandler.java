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
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Re-uses address from messages with unconfirmed peers as last-resort.
 */
public class UnconfirmedAddressResolveHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UnconfirmedAddressResolveHandler.class);
    private final Map<DrasylAddress, Pair<InetSocketAddress, Long>> addressCache;
    private final int maximumCacheSize;
    private final long expireCacheAfter;
    private long now;

    public UnconfirmedAddressResolveHandler() {
        this(100, 60_000L);
    }

    public UnconfirmedAddressResolveHandler(final int maximumCacheSize,
                                            final long expireCacheAfter) {
        this.maximumCacheSize = requirePositive(maximumCacheSize);
        this.expireCacheAfter = requirePositive(expireCacheAfter);
        this.addressCache = new HashMap<>();
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isActive()) {
            scheduleHousekeepingTask(ctx);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
        scheduleHousekeepingTask(ctx);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof InetAddressedMessage<?> && ((InetAddressedMessage<?>) msg).content() instanceof RemoteMessage && addressCache.size() < maximumCacheSize) {
            addressCache.put(((RemoteMessage) ((InetAddressedMessage<?>) msg).content()).getSender(), Pair.of(((InetAddressedMessage<?>) msg).sender(), now));
        }

        // pass through
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof OverlayAddressedMessage) {
            final Pair<InetSocketAddress, Long> pair = addressCache.get(((OverlayAddressedMessage<?>) msg).recipient());

            // route to the unconfirmed address
            if (pair != null) {
                final InetSocketAddress address = pair.first();
                LOG.trace("Message `{}` was resolved to unconfirmed address `{}`.", () -> msg, () -> address);
                ctx.write(((OverlayAddressedMessage<?>) msg).resolve(address), promise);
                return;
            }
        }

        // pass through
        ctx.write(msg, promise);
    }

    private void scheduleHousekeepingTask(final ChannelHandlerContext ctx) {
        // requesting the time triggers a system call and is therefore considered to be expensive.
        // This is why we cache the current time
        now = System.currentTimeMillis();

        ctx.executor().schedule(() -> {
            // remove all entries from cache where we do not have received a message since "expireAfter"
            final Iterator<Entry<DrasylAddress, Pair<InetSocketAddress, Long>>> iterator = addressCache.entrySet().iterator();
            while (iterator.hasNext()) {
                final Entry<DrasylAddress, Pair<InetSocketAddress, Long>> entry = iterator.next();
                final long lastTime = entry.getValue().second();
                if (lastTime < now) {
                    iterator.remove();
                }
            }

            if (ctx.channel().isActive()) {
                scheduleHousekeepingTask(ctx);
            }
        }, expireCacheAfter, MILLISECONDS);
    }
}
