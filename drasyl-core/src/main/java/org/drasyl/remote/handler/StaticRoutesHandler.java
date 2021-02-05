/*
 * Copyright (c) 2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.remote.handler;

import com.typesafe.config.Config;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.serialization.SerializedApplicationMessage;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.remote.protocol.AddressedIntermediateEnvelope;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

/**
 * This handler uses preconfigured static routes ({@link org.drasyl.DrasylConfig#getStaticRoutes(Config,
 * String)}) to deliver messages.
 */
public class StaticRoutesHandler extends SimpleOutboundHandler<SerializedApplicationMessage, CompressedPublicKey> {
    public static final String STATIC_ROUTES_HANDLER = "STATIC_ROUTES_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(StaticRoutesHandler.class);
    private static final Object path = StaticRoutesHandler.class;
    private final Map<CompressedPublicKey, InetSocketAddressWrapper> routes;

    StaticRoutesHandler(final Map<CompressedPublicKey, InetSocketAddressWrapper> routes) {
        this.routes = requireNonNull(routes);
    }

    public StaticRoutesHandler() {
        this(new HashMap<>());
    }

    @Override
    public void eventTriggered(final HandlerContext ctx,
                               final Event event,
                               final CompletableFuture<Void> future) {
        if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            clearRoutes(ctx);
        }

        // passthrough event
        ctx.fireEventTriggered(event, future).whenComplete((result, e) -> {
            if (event instanceof NodeUpEvent) {
                populateRoutes(ctx);
            }
        });
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final CompressedPublicKey recipient,
                                final SerializedApplicationMessage msg,
                                final CompletableFuture<Void> future) {
        final InetSocketAddressWrapper staticAddress = routes.get(msg.getRecipient());
        if (staticAddress != null) {
            final IntermediateEnvelope<Application> envelope = IntermediateEnvelope.application(ctx.config().getNetworkId(), ctx.identity().getPublicKey(), ctx.identity().getProofOfWork(), msg.getRecipient(), msg.getType(), msg.getContent());
            LOG.trace("Send message `{}` via static route {}.", () -> msg, staticAddress::getAddress);
            ctx.write(staticAddress, new AddressedIntermediateEnvelope<>(null, staticAddress, envelope), future);
        }
        else {
            // passthrough message
            ctx.write(recipient, msg, future);
        }
    }

    private synchronized void populateRoutes(final HandlerContext ctx) {
        ctx.config().getRemoteStaticRoutes().forEach(((publicKey, address) -> {
            routes.put(publicKey, InetSocketAddressWrapper.of(address));
            ctx.peersManager().addPath(publicKey, path);
        }));
    }

    private synchronized void clearRoutes(final HandlerContext ctx) {
        routes.keySet().forEach(publicKey -> ctx.peersManager().removeChildrenAndPath(publicKey, path));
        routes.clear();
    }
}
