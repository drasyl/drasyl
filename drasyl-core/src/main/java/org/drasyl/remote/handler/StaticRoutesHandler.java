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
package org.drasyl.remote.handler;

import com.typesafe.config.Config;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * This handler uses preconfigured static routes ({@link org.drasyl.DrasylConfig#getStaticRoutes(Config,
 * String)}) to deliver messages.
 */
@Stateless
public final class StaticRoutesHandler extends SimpleOutboundHandler<ApplicationMessage, IdentityPublicKey> {
    public static final StaticRoutesHandler INSTANCE = new StaticRoutesHandler();
    private static final Logger LOG = LoggerFactory.getLogger(StaticRoutesHandler.class);
    private static final Object path = StaticRoutesHandler.class;

    private StaticRoutesHandler() {
        // singleton
    }

    @Override
    public void onEvent(final HandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            clearRoutes(ctx);
        }

        if (event instanceof NodeUpEvent) {
            populateRoutes(ctx);
        }

        // passthrough event
        ctx.passEvent(event, future);
    }

    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final IdentityPublicKey recipient,
                                   final ApplicationMessage envelope,
                                   final CompletableFuture<Void> future) {
        final InetSocketAddressWrapper staticAddress = ctx.config().getRemoteStaticRoutes().get(recipient);
        if (staticAddress != null) {
            LOG.trace("Send message `{}` via static route {}.", () -> envelope, () -> staticAddress);
            ctx.passOutbound(staticAddress, envelope, future);
        }
        else {
            // passthrough message
            ctx.passOutbound(recipient, envelope, future);
        }
    }

    private static synchronized void populateRoutes(final HandlerContext ctx) {
        ctx.config().getRemoteStaticRoutes().forEach(((publicKey, address) -> ctx.peersManager().addPath(ctx, publicKey, path)));
    }

    private static synchronized void clearRoutes(final HandlerContext ctx) {
        ctx.config().getRemoteStaticRoutes().keySet().forEach(publicKey -> ctx.peersManager().removePath(ctx, publicKey, path));
    }
}
