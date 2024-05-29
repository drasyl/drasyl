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
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.remote.PeersManager.PathId;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.Pair;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * Uses shared memory to discover other drasyl nodes running on same JVM.
 * <p>
 * Inspired by: <a href="https://github.com/actoron/jadex/blob/10e464b230d7695dfd9bf2b36f736f93d69ee314/platform/base/src/main/java/jadex/platform/service/awareness/IntraVMAwarenessAgent.java">Jadex</a>
 */
@UnstableApi
@SuppressWarnings({ "java:S110" })
public class IntraVmDiscovery extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(IntraVmDiscovery.class);
    public static final PathId PATH_ID = new PathId() {
        @Override
        public short priority() {
            return 20;
        }
    };
    static Map<Pair<Integer, DrasylAddress>, ChannelHandlerContext> discoveries = new ConcurrentHashMap<>();
    private boolean initialized;

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof OverlayAddressedMessage) {
            final DrasylAddress recipient = ((OverlayAddressedMessage<?>) msg).recipient();
            final ChannelHandlerContext discoveree = discoveries.get(Pair.of(config(ctx).getNetworkId(), recipient));

            if (discoveree == null) {
                // pass through message
                ctx.write(msg, promise);
            }
            else {
                LOG.debug("Send message `{}` via Intra VM Discovery.", ((OverlayAddressedMessage<?>) msg)::content);
                discoveree.fireChannelRead(msg);
                discoveree.fireChannelReadComplete();
                promise.setSuccess();
            }
        }
        else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive() && !initialized) {
            initialized = true;
            startDiscovery(ctx);
        }
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        if (initialized) {
            initialized = false;
            stopDiscovery(ctx);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        if (!initialized) {
            initialized = true;
            startDiscovery(ctx);
        }

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        if (initialized) {
            initialized = false;
            stopDiscovery(ctx);
        }

        ctx.fireChannelInactive();
    }

    private void startDiscovery(final ChannelHandlerContext myCtx) {
        LOG.debug("Start Intra VM Discovery...");

        // store peer information
        discoveries.forEach((key, otherCtx) -> {
            final Integer networkId = key.first();
            final DrasylAddress publicKey = key.second();
            if (config(myCtx).getNetworkId() == networkId) {
                final PeersManager otherPeersManager = config(otherCtx).getPeersManager();
                final DrasylAddress peerKey = (DrasylAddress) myCtx.channel().localAddress();
                otherPeersManager.addChildrenPath(otherCtx, peerKey, PATH_ID, null, PATH_ID.priority());
                final PeersManager peersManager = config(myCtx).getPeersManager();
                final DrasylAddress otherPeerKey = (DrasylAddress) otherCtx.channel().localAddress();
                peersManager.addChildrenPath(myCtx, otherPeerKey, PATH_ID, null, PATH_ID.priority());
            }
        });
        discoveries.put(
                Pair.of(config(myCtx).getNetworkId(), requireNonNull((DrasylAddress) myCtx.channel().localAddress())),
                myCtx
        );

        LOG.debug("Intra VM Discovery started.");
    }

    private void stopDiscovery(final ChannelHandlerContext myCtx) {
        LOG.debug("Stop Intra VM Discovery...");

        // remove peer information
        discoveries.remove(Pair.of(config(myCtx).getNetworkId(), myCtx.channel().localAddress()));
        discoveries.forEach((key, otherCtx) -> {
            final Integer otherNetworkId = key.first();
            final DrasylAddress publicKey = key.second();
            if (config(myCtx).getNetworkId() == otherNetworkId) {
                final PeersManager otherPeersManager = config(otherCtx).getPeersManager();
                final DrasylAddress peerKey = (DrasylAddress) myCtx.channel().localAddress();
                otherPeersManager.removeChildrenPath(otherCtx, peerKey, PATH_ID);
                final PeersManager peersManager = config(myCtx).getPeersManager();
                final DrasylAddress otherPeerKey = (DrasylAddress) otherCtx.channel().localAddress();
                peersManager.removeChildrenPath(myCtx, otherPeerKey, PATH_ID);
            }
        });

        LOG.debug("Intra VM Discovery stopped.");
    }

    private static DrasylServerChannelConfig config(final ChannelHandlerContext ctx) {
        return (DrasylServerChannelConfig) ctx.channel().config();
    }
}
