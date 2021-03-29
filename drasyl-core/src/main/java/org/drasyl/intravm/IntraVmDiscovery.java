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

import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Uses shared memory to discover other drasyl nodes running on same JVM.
 * <p>
 * Inspired by: https://github.com/actoron/jadex/blob/10e464b230d7695dfd9bf2b36f736f93d69ee314/platform/base/src/main/java/jadex/platform/service/awareness/IntraVMAwarenessAgent.java
 */
@SuppressWarnings({ "java:S110" })
public class IntraVmDiscovery extends SimpleOutboundHandler<Object, Address> {
    public static final IntraVmDiscovery INSTANCE = new IntraVmDiscovery();
    private static final Logger LOG = LoggerFactory.getLogger(IntraVmDiscovery.class);
    private static final Object path = IntraVmDiscovery.class;
    private final Map<Pair<Integer, CompressedPublicKey>, HandlerContext> discoveries;
    private final ReadWriteLock lock;

    IntraVmDiscovery() {
        this(new HashMap<>(), new ReentrantReadWriteLock(true));
    }

    IntraVmDiscovery(final Map<Pair<Integer, CompressedPublicKey>, HandlerContext> discoveries,
                     final ReadWriteLock lock) {
        this.discoveries = discoveries;
        this.lock = lock;
    }

    @Override
    public void onEvent(final HandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        if (event instanceof NodeUpEvent) {
            startDiscovery(ctx);
        }
        else if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            stopDiscovery(ctx);
        }

        // passthrough event
        ctx.passEvent(event, future);
    }

    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final Address recipient,
                                   final Object msg,
                                   final CompletableFuture<Void> future) {
        final HandlerContext discoveree = discoveries.get(Pair.of(ctx.config().getNetworkId(), recipient));

        if (discoveree == null) {
            // passthrough message
            ctx.passOutbound(recipient, msg, future);
        }
        else {
            discoveree.passInbound(ctx.identity().getPublicKey(), msg, future);
        }
    }

    private synchronized void startDiscovery(final HandlerContext myCtx) {
        try {
            lock.writeLock().lock();
            LOG.debug("Start Intra VM Discovery...");

            // store peer information
            discoveries.forEach((key, otherCtx) -> {
                final Integer networkId = key.first();
                final CompressedPublicKey publicKey = key.second();
                if (myCtx.config().getNetworkId() == networkId) {
                    otherCtx.peersManager().addPath(myCtx.identity().getPublicKey(), path);
                    myCtx.peersManager().addPath(publicKey, path);
                }
            });
            discoveries.put(
                    Pair.of(myCtx.config().getNetworkId(), myCtx.identity().getPublicKey()),
                    myCtx
            );

            LOG.debug("Intra VM Discovery started.");
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    private synchronized void stopDiscovery(final HandlerContext ctx) {
        try {
            lock.writeLock().lock();
            LOG.debug("Stop Intra VM Discovery...");

            // remove peer information
            discoveries.remove(Pair.of(ctx.config().getNetworkId(), ctx.identity().getPublicKey()));
            discoveries.forEach((key, otherCtx) -> {
                final Integer networkId = key.first();
                final CompressedPublicKey publicKey = key.second();
                if (ctx.config().getNetworkId() == networkId) {
                    otherCtx.peersManager().removePath(publicKey, path);
                    ctx.peersManager().removePath(publicKey, path);
                }
            });

            LOG.debug("Intra VM Discovery stopped.");
        }
        finally {
            lock.writeLock().unlock();
        }
    }
}
