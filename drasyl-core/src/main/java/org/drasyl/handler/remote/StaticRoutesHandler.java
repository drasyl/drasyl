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
package org.drasyl.handler.remote;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.internal.UnstableApi;

import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * This handler uses preconfigured static routes to deliver messages.
 */
@UnstableApi
public final class StaticRoutesHandler extends ChannelDuplexHandler {
    static final PeersManager.PathId PATH_ID = new PeersManager.PathId() {
        @Override
        public short priority() {
            return 70;
        }
    };
    private final Map<DrasylAddress, InetSocketAddress> staticRoutes;

    public StaticRoutesHandler(final Map<DrasylAddress, InetSocketAddress> staticRoutes) {
        this.staticRoutes = requireNonNull(staticRoutes);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        populateRoutes(ctx);

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        clearRoutes(ctx);

        ctx.fireChannelInactive();
    }

    private void populateRoutes(final ChannelHandlerContext ctx) {
        staticRoutes.forEach((peer, endpoint) -> {
            config(ctx).getPeersManager().addChildrenPath(ctx, peer, PATH_ID, endpoint, PATH_ID.priority());
        });
    }

    private void clearRoutes(final ChannelHandlerContext ctx) {
        config(ctx).getPeersManager().removeChildrenPaths(ctx, PATH_ID);
    }

    private static DrasylServerChannelConfig config(final ChannelHandlerContext ctx) {
        return (DrasylServerChannelConfig) ctx.channel().config();
    }
}
