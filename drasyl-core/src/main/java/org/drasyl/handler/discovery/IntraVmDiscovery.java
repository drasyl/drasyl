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
package org.drasyl.handler.discovery;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.Objects.requireNonNull;

/**
 * Uses shared memory to discover other drasyl nodes running on same JVM.
 * <p>
 * Inspired by: <a href="https://github.com/actoron/jadex/blob/10e464b230d7695dfd9bf2b36f736f93d69ee314/platform/base/src/main/java/jadex/platform/service/awareness/IntraVMAwarenessAgent.java">Jadex</a>
 */
@SuppressWarnings({ "java:S110" })
public class IntraVmDiscovery extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(IntraVmDiscovery.class);
    private static final Object path = IntraVmDiscovery.class;
    static Map<Pair<Integer, DrasylAddress>, ChannelHandlerContext> discoveries = new ConcurrentHashMap<>();
    private final ReadWriteLock lock;
    private final DrasylAddress myAddress;
    private final int myNetworkId;

    public IntraVmDiscovery(final int myNetworkId,
                            final DrasylAddress myAddress) {
        this(new ReentrantReadWriteLock(true), myAddress, myNetworkId);
    }

    IntraVmDiscovery(final ReadWriteLock lock,
                     final DrasylAddress myAddress,
                     final int myNetworkId) {
        this.lock = requireNonNull(lock);
        this.myAddress = requireNonNull(myAddress);
        this.myNetworkId = myNetworkId;
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof AddressedMessage) {
            final SocketAddress recipient = ((AddressedMessage<?, ?>) msg).address();

            final ChannelHandlerContext discoveree = discoveries.get(Pair.of(myNetworkId, recipient));

            if (discoveree == null) {
                // pass through message
                ctx.write(msg, promise);
            }
            else {
                LOG.debug("Send message `{}` via Intra VM Discovery.", ((AddressedMessage<?, ?>) msg)::message);
                discoveree.fireChannelRead(((AddressedMessage<?, ?>) msg).route(myAddress));
                promise.setSuccess();
            }
        }
        else {
            ctx.write(msg, promise);
        }
    }

    private void startDiscovery(final ChannelHandlerContext myCtx) {
        try {
            lock.writeLock().lock();
            LOG.debug("Start Intra VM Discovery...");

            // store peer information
            discoveries.forEach((key, otherCtx) -> {
                final Integer networkId = key.first();
                final DrasylAddress publicKey = key.second();
                if (myNetworkId == networkId) {
                    otherCtx.channel().pipeline().fireUserEventTriggered(AddPathEvent.of(myAddress, path));
                    myCtx.channel().pipeline().fireUserEventTriggered(AddPathEvent.of(publicKey, path));
                }
            });
            discoveries.put(
                    Pair.of(myNetworkId, myAddress),
                    myCtx
            );

            LOG.debug("Intra VM Discovery started.");
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    private void stopDiscovery(final ChannelHandlerContext myCtx) {
        try {
            lock.writeLock().lock();
            LOG.debug("Stop Intra VM Discovery...");

            // remove peer information
            discoveries.remove(Pair.of(myNetworkId, myAddress));
            discoveries.forEach((key, otherCtx) -> {
                final Integer otherNetworkId = key.first();
                final DrasylAddress publicKey = key.second();
                if (myNetworkId == otherNetworkId) {
                    otherCtx.channel().pipeline().fireUserEventTriggered(RemovePathEvent.of(myAddress, path));
                    myCtx.channel().pipeline().fireUserEventTriggered(RemovePathEvent.of(publicKey, path));
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
