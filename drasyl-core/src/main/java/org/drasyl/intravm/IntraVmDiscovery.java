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
package org.drasyl.intravm;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.drasyl.channel.DefaultDrasylServerChannel.CONFIG_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.PEERS_MANAGER_ATTR_KEY;

/**
 * Uses shared memory to discover other drasyl nodes running on same JVM.
 * <p>
 * Inspired by: https://github.com/actoron/jadex/blob/10e464b230d7695dfd9bf2b36f736f93d69ee314/platform/base/src/main/java/jadex/platform/service/awareness/IntraVMAwarenessAgent.java
 */
@ChannelHandler.Sharable
@SuppressWarnings({ "java:S110" })
public class IntraVmDiscovery extends ChannelDuplexHandler {
    public static final IntraVmDiscovery INSTANCE = new IntraVmDiscovery();
    private static final Logger LOG = LoggerFactory.getLogger(IntraVmDiscovery.class);
    private static final Object path = IntraVmDiscovery.class;
    private final Map<Pair<Integer, IdentityPublicKey>, ChannelHandlerContext> discoveries;
    private final ReadWriteLock lock;

    IntraVmDiscovery() {
        this(new HashMap<>(), new ReentrantReadWriteLock(true));
    }

    IntraVmDiscovery(final Map<Pair<Integer, IdentityPublicKey>, ChannelHandlerContext> discoveries,
                     final ReadWriteLock lock) {
        this.discoveries = discoveries;
        this.lock = lock;
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) throws Exception {
        if (msg instanceof AddressedMessage) {
            final SocketAddress recipient = ((AddressedMessage<?, ?>) msg).address();

            final ChannelHandlerContext discoveree = discoveries.get(Pair.of(ctx.channel().attr(CONFIG_ATTR_KEY).get().getNetworkId(), recipient));

            if (discoveree == null) {
                // passthrough message
                ctx.write(msg, promise);
            }
            else {
                discoveree.fireChannelRead(new AddressedMessage<>(((AddressedMessage<?, ?>) msg).message(), ctx.channel().attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey()));
            }
        }
        else {
            super.write(ctx, msg, promise);
        }
    }

    private void startDiscovery(final ChannelHandlerContext myCtx) {
        try {
            lock.writeLock().lock();
            LOG.debug("Start Intra VM Discovery...");

            // store peer information
            discoveries.forEach((key, otherCtx) -> {
                final Integer networkId = key.first();
                final IdentityPublicKey publicKey = key.second();
                if (myCtx.channel().attr(CONFIG_ATTR_KEY).get().getNetworkId() == networkId) {
                    otherCtx.channel().attr(PEERS_MANAGER_ATTR_KEY).get().addPath(otherCtx, myCtx.channel().attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey(), path);
                    myCtx.channel().attr(PEERS_MANAGER_ATTR_KEY).get().addPath(myCtx, publicKey, path);
                }
            });
            discoveries.put(
                    Pair.of(myCtx.channel().attr(CONFIG_ATTR_KEY).get().getNetworkId(), myCtx.channel().attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey()),
                    myCtx
            );

            LOG.debug("Intra VM Discovery started.");
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    private void stopDiscovery(final ChannelHandlerContext ctx) {
        try {
            lock.writeLock().lock();
            LOG.debug("Stop Intra VM Discovery...");

            // remove peer information
            discoveries.remove(Pair.of(ctx.channel().attr(CONFIG_ATTR_KEY).get().getNetworkId(), ctx.channel().attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey()));
            discoveries.forEach((key, otherCtx) -> {
                final Integer networkId = key.first();
                final IdentityPublicKey publicKey = key.second();
                if (ctx.channel().attr(CONFIG_ATTR_KEY).get().getNetworkId() == networkId) {
                    otherCtx.channel().attr(PEERS_MANAGER_ATTR_KEY).get().removePath(ctx, publicKey, path);
                    ctx.channel().attr(PEERS_MANAGER_ATTR_KEY).get().removePath(ctx, publicKey, path);
                }
            });

            LOG.debug("Intra VM Discovery stopped.");
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        startDiscovery(ctx);

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        stopDiscovery(ctx);

        super.channelInactive(ctx);
    }
}
