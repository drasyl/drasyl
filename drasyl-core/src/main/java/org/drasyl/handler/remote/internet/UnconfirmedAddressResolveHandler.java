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
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Iterator;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Re-uses address from messages with unconfirmed peers as last-resort.
 */
@UnstableApi
public class UnconfirmedAddressResolveHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UnconfirmedAddressResolveHandler.class);
    static final Class<?> PATH_ID = UnconfirmedAddressResolveHandler.class;
    static final short PATH_PRIORITY = 110;
    private final long expireCacheAfter;
    private final PeersManager peersManager;

    public UnconfirmedAddressResolveHandler(final long expireCacheAfter,
                                            final PeersManager peersManager) {
        this.expireCacheAfter = requirePositive(expireCacheAfter);
        this.peersManager = requireNonNull(peersManager);
    }

    public UnconfirmedAddressResolveHandler(final PeersManager peersManager) {
        this(60_000L, peersManager);
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
        if (msg instanceof InetAddressedMessage<?> && ((InetAddressedMessage<?>) msg).content() instanceof RemoteMessage) {
            final DrasylAddress peer = ((RemoteMessage) ((InetAddressedMessage<?>) msg).content()).getSender();
            final InetSocketAddress endpoint = ((InetAddressedMessage<?>) msg).sender();
            peersManager.addPath(peer, PATH_ID, endpoint, PATH_PRIORITY);
            // FIXME: wird zukünftig mit application nachrichten nicht mehr funktionieren, wenn dies nicht mehr hier vorbei geht
            peersManager.helloMessageReceived(peer, PATH_ID); // consider every message as hello. this is fine here
        }

        // pass through
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof OverlayAddressedMessage) {
            final DrasylAddress peer = ((OverlayAddressedMessage<?>) msg).recipient();
            final InetSocketAddress endpoint = peersManager.getEndpoint(peer, PATH_ID);

            // route to the unconfirmed address
            if (endpoint != null) {
                LOG.trace("Message `{}` was resolved to unconfirmed address `{}`.", () -> msg, () -> endpoint);
                ctx.write(((OverlayAddressedMessage<?>) msg).resolve(endpoint), promise);
                return;
            }
        }

        // pass through
        ctx.write(msg, promise);
    }

    private void scheduleHousekeepingTask(final ChannelHandlerContext ctx) {
        // requesting the time triggers a system call and is therefore considered to be expensive.
        // This is why we cache the current time

        ctx.executor().schedule(() -> {
            // remove all entries from cache where we do not have received a message since "expireAfter"
            final Iterator<DrasylAddress> iterator = peersManager.getPeers(PATH_ID).iterator();
            while (iterator.hasNext()) {
                final DrasylAddress publicKey = iterator.next();
                final boolean stale = peersManager.isStale(publicKey, PATH_ID);

                if (stale) {
                    final long lastInboundHelloTime = peersManager.lastHelloMessageReceivedTime(publicKey, PATH_ID);
                    LOG.debug("Last contact from {} is {}ms ago. Remove peer.", () -> publicKey, () -> System.currentTimeMillis() - lastInboundHelloTime);
                    peersManager.removePath(publicKey, PATH_ID);
                }
            }

            if (ctx.channel().isActive()) {
                scheduleHousekeepingTask(ctx);
            }
        }, expireCacheAfter, MILLISECONDS);
    }
}
