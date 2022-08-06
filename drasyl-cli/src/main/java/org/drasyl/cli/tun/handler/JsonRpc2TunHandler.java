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
package org.drasyl.cli.tun.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.cli.node.message.JsonRpc2Error;
import org.drasyl.cli.node.message.JsonRpc2Request;
import org.drasyl.cli.node.message.JsonRpc2Response;
import org.drasyl.cli.rc.handler.JsonRpc2RequestHandler;
import org.drasyl.cli.tun.TunCommand;
import org.drasyl.cli.tun.TunCommand.AddRoute;
import org.drasyl.cli.tun.TunCommand.RemoveRoute;
import org.drasyl.cli.tun.TunRoute;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.network.Subnet;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.stream.Collectors;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.lang.System.out;
import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.node.message.JsonRpc2Error.INVALID_PARAMS;

/**
 * Allow remote controlling of a TUN interface via JSON-RPC 2.0.
 */
public class JsonRpc2TunHandler extends JsonRpc2RequestHandler {
    private static final Logger LOG = LoggerFactory.getLogger(JsonRpc2TunHandler.class);
    private final Map<InetAddress, DrasylAddress> routes;
    private final Identity identity;
    private final Subnet subnet;
    private final Channel channel;
    private final InetAddress myAddress;

    public JsonRpc2TunHandler(final Map<InetAddress, DrasylAddress> routes,
                              final Identity identity,
                              final Subnet subnet,
                              final Channel channel,
                              final InetAddress myAddress) {
        this.routes = requireNonNull(routes);
        this.identity = requireNonNull(identity);
        this.subnet = requireNonNull(subnet);
        this.channel = requireNonNull(channel);
        this.myAddress = requireNonNull(myAddress);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final JsonRpc2Request request) throws Exception {
        LOG.trace("Got request `{}`.", request);

        switch (request.getMethod()) {
            case "routes":
                routes(ctx, request);
                break;
            case "addRoute":
                addRoute(ctx, request);
                break;
            case "removeRoute":
                removeRoute(ctx, request);
                break;
            case "identity":
                identity(ctx, request);
                break;
            default:
                requestMethodNotFound(ctx, request, request.getMethod());
        }
    }

    private void routes(final ChannelHandlerContext ctx, final JsonRpc2Request request) {
        LOG.trace("Got routes request.");

        final Object requestId = request.getId();
        if (requestId != null) {
            final Map<String, String> responseRoutes = routes.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getHostAddress(), e -> e.getValue().toString()));
            final JsonRpc2Response response = new JsonRpc2Response(responseRoutes, requestId);
            LOG.trace("Send response `{}`.", response);
            ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
        }
        else {
            LOG.trace("Drop routes request as it was sent as notification.");
        }
    }

    private void addRoute(final ChannelHandlerContext ctx, final JsonRpc2Request request) {
        final Object requestId = request.getId();
        final String publicKeyParam = request.getParam("publicKey", "");
        final String addressParam = request.getParam("address", "");
        try {
            final IdentityPublicKey publicKey = IdentityPublicKey.of(publicKeyParam);

            if (identity.getIdentityPublicKey().equals(publicKey)) {
                final JsonRpc2Error error = new JsonRpc2Error(INVALID_PARAMS, "Cannot add route to self.");
                final JsonRpc2Response response = new JsonRpc2Response(error, requestId);
                LOG.trace("Send response `{}`.", response);
                ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
                return;
            }

            final InetAddress address;
            if (addressParam != null && !addressParam.isEmpty()) {
                address = InetAddress.getByName(addressParam);
            }
            else {
                address = TunRoute.deriveInetAddressFromOverlayAddress(subnet, publicKey);
            }

            LOG.trace("Add route {} <-> {}", address, publicKey);
            //noinspection StatementWithEmptyBody
            while (routes.values().remove(publicKey)) ;
            final DrasylAddress previousKey = routes.put(address, publicKey);

            if (!publicKey.equals(previousKey)) {
                channel.pipeline().fireUserEventTriggered(new AddRoute(publicKey));
            }

            TunCommand.printRoutingTable(out, identity, myAddress, routes);

            // return current routing table
            if (requestId != null) {
                final Map<String, String> responseRoutes = routes.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getHostAddress(), e -> e.getValue().toString()));
                final JsonRpc2Response response = new JsonRpc2Response(responseRoutes, requestId);
                LOG.trace("Send response `{}`.", response);
                ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
            }
        }
        catch (final NullPointerException | IllegalArgumentException | UnknownHostException e) {
            if (requestId != null) {
                final JsonRpc2Error error = new JsonRpc2Error(1, e.getMessage());
                final JsonRpc2Response response = new JsonRpc2Response(error, requestId);
                LOG.trace("Send response `{}`.", response);
                ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
            }
        }
    }

    @SuppressWarnings({ "java:S3776", "StatementWithEmptyBody" })
    private void removeRoute(final ChannelHandlerContext ctx, final JsonRpc2Request request) {
        final Object requestId = request.getId();
        final String publicKeyParam = request.getParam("publicKey");
        final IdentityPublicKey publicKey;
        if (publicKeyParam != null && !publicKeyParam.isEmpty()) {
            try {
                publicKey = IdentityPublicKey.of(publicKeyParam);
            }
            catch (final IllegalArgumentException e) {
                // publicKey invalid
                if (requestId != null) {
                    final JsonRpc2Error error = new JsonRpc2Error(1, e.getMessage());
                    final JsonRpc2Response response = new JsonRpc2Response(error, requestId);
                    LOG.trace("Send response `{}`.", response);
                    ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
                }
                return;
            }
        }
        else {
            publicKey = null;
        }
        final String addressParam = request.getParam("address");
        final InetAddress address;
        if (addressParam != null && !addressParam.isEmpty()) {
            try {
                address = InetAddress.getByName(addressParam);
            }
            catch (final UnknownHostException e) {
                // address invalid
                if (requestId != null) {
                    final JsonRpc2Error error = new JsonRpc2Error(1, e.getMessage());
                    final JsonRpc2Response response = new JsonRpc2Response(error, requestId);
                    LOG.trace("Send response `{}`.", response);
                    ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
                }
                return;
            }
        }
        else {
            address = null;
        }

        if (publicKey == null && address == null) {
            // both parameters must not be empty
            if (requestId != null) {
                final JsonRpc2Error error = new JsonRpc2Error(INVALID_PARAMS, "Parameters publicKey or address must not be empty.");
                final JsonRpc2Response response = new JsonRpc2Response(error, requestId);
                LOG.trace("Send response `{}`.", response);
                ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
            }
            return;
        }

        if (publicKey != null) {
            if (address != null) {
                LOG.trace("Remove route {} <-> {}", address, publicKey);
                if (routes.remove(address, publicKey)) {
                    channel.pipeline().fireUserEventTriggered(new RemoveRoute(publicKey));
                }
            }
            else {
                LOG.trace("Remove route * <-> {}", publicKey);
                while (routes.values().remove(publicKey)) {
                    channel.pipeline().fireUserEventTriggered(new RemoveRoute(publicKey));
                }
            }
        }
        else {
            LOG.trace("Remove route {} <-> *", address);
            final DrasylAddress removedPublicKey = routes.remove(address);
            if (removedPublicKey != null) {
                channel.pipeline().fireUserEventTriggered(new RemoveRoute((IdentityPublicKey) removedPublicKey));
            }
        }

        TunCommand.printRoutingTable(out, identity, myAddress, routes);

        // return current routing table
        if (requestId != null) {
            final Map<String, String> responseRoutes = routes.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getHostAddress(), e -> e.getValue().toString()));
            final JsonRpc2Response response = new JsonRpc2Response(responseRoutes, requestId);
            LOG.trace("Send response `{}`.", response);
            ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
        }
    }

    private void identity(final ChannelHandlerContext ctx, final JsonRpc2Request request) {
        LOG.trace("Got identity request.");

        final Object requestId = request.getId();
        if (requestId != null) {
            final Map<String, Object> result = identityMap(identity);
            final JsonRpc2Response response = new JsonRpc2Response(result, requestId);
            LOG.trace("Send response `{}`.", response);
            ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
        }
        else {
            LOG.trace("Drop identity request as it was sent as notification.");
        }
    }
}
