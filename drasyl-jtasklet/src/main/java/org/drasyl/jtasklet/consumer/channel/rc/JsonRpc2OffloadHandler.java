/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.jtasklet.consumer.channel.rc;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.node.message.JsonRpc2Error;
import org.drasyl.cli.node.message.JsonRpc2Request;
import org.drasyl.cli.node.message.JsonRpc2Response;
import org.drasyl.cli.rc.handler.JsonRpc2RequestHandler;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.channel.ChildChannelInitializer;
import org.drasyl.jtasklet.consumer.handler.PersistentConsumerHandler;
import org.drasyl.node.DrasylNodeSharedEventLoopGroupHolder;
import org.drasyl.node.identity.IdentityManager;
import org.drasyl.util.*;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.channel.ChannelFutureListener.CLOSE;
import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static org.drasyl.cli.ChannelOptions.MIN_DERIVED_PORT;
import static org.drasyl.util.network.NetworkUtil.MAX_PORT_NUMBER;

public class JsonRpc2OffloadHandler extends JsonRpc2RequestHandler {
    private static final Logger LOG = LoggerFactory.getLogger(JsonRpc2OffloadHandler.class);
    private final EventLoopGroup group;
    private final Worm<ServerBootstrap> serverBootstrap;
    private final Worm<Identity> identity;
    private final Worm<Channel> channel;
    private final AtomicBoolean offloadInProgress;

    public JsonRpc2OffloadHandler() {
        group = EventLoopGroupUtil.getBestEventLoopGroup(1);
        serverBootstrap = Worm.of();
        identity = Worm.of();
        channel = Worm.of();
        offloadInProgress = new AtomicBoolean();
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final JsonRpc2Request request) {
        LOG.trace("Got request `{}`.", request);

        switch (request.getMethod()) {
            case "start":
                start(ctx, request);
                break;
            case "shutdown":
                shutdown(ctx, request);
                break;
            case "offload":
                offload(ctx, request);
                break;
            default:
                requestMethodNotFound(ctx, request, request.getMethod());
                break;
        }
    }

    private void start(final ChannelHandlerContext ctx, final JsonRpc2Request request) {
        final File identityFile = new File(request.getParam("identityPath", "drasyl.identity"));
        final Object requestId = request.getId();
        JsonRpc2Response response;

        if (serverBootstrap.isPresent()) {
            final JsonRpc2Error error = new JsonRpc2Error(1, "Already started.");
            response = new JsonRpc2Response(error, requestId);
        }
        else {
            try {
                if (!identityFile.exists()) {
                    LOG.debug("Identity not found. Generate a new one. This may take a while...");
                    IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
                    LOG.debug("Identity generated!");
                }

                identity.trySet(IdentityManager.readIdentityFile(identityFile.toPath()));

                // derive bind port from identity
                final long identityHash = UnsignedInteger.of(Murmur3.murmur3_x86_32BytesLE(identity.get().getAddress().toByteArray())).getValue();
                final int identityPort = (int) (MIN_DERIVED_PORT + identityHash % (MAX_PORT_NUMBER - MIN_DERIVED_PORT));
                final InetSocketAddress bindAddress = new InetSocketAddress("0.0.0.0", identityPort);

                serverBootstrap.set(new ServerBootstrap()
                        .group(DrasylNodeSharedEventLoopGroupHolder.getParentGroup(), DrasylNodeSharedEventLoopGroupHolder.getChildGroup())
                        .channel(DrasylServerChannel.class)
                        .handler(getHandler(request, bindAddress))
                        .childHandler(getChildHandler()));
                channel.set(serverBootstrap.get().bind(identity.get().getAddress()).syncUninterruptibly().channel());

                response = new JsonRpc2Response("", requestId);
            }
            catch (final IOException e) {
                final JsonRpc2Error error = new JsonRpc2Error(1, e.getMessage());
                response = new JsonRpc2Response(error, requestId);
            }
        }

        LOG.trace("Send response `{}`.", response);
        ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
    }

    private void shutdown(final ChannelHandlerContext ctx, final JsonRpc2Request request) {
        final Object requestId = request.getId();
        final JsonRpc2Response response;

        if (serverBootstrap.isPresent() && channel.isPresent()) {
            channel.get().close();

            response = new JsonRpc2Response("", requestId);
        }
        else {
            final JsonRpc2Error error = new JsonRpc2Error(1, "Node is not running.");
            response = new JsonRpc2Response(error, requestId);
        }

        LOG.trace("Send response `{}`.", response);
        ctx.writeAndFlush(response).addListener(CLOSE);
    }

    private void offload(final ChannelHandlerContext ctx, final JsonRpc2Request request) {
        final Object requestId = request.getId();

        if (offloadInProgress.compareAndSet(false, true)) {
            if (channel.isPresent()) {
                final IdentityPublicKey broker = IdentityPublicKey.of(request.getParam("broker", ""));
                final String source = request.getParam("source");
                final List<Object> input = request.getParam("inputParams", new ArrayList<>());
                final List<String> tags = request.getParam("tags", new ArrayList<>());
                final int priority = request.getParam("priority", 0);
                final int cycles = request.getParam("cycles", 1);
                final int retryInterval = request.getParam("retryInterval", 1_000);
                final CompletableFuture<Object[]> result = new CompletableFuture<>();

                channel.get().pipeline().addLast(new PersistentConsumerHandler(result, System.out, identity.get().getAddress(), broker, source, input.toArray(), cycles, tags, priority, retryInterval));

                result.whenComplete((r, e) -> {
                    final JsonRpc2Response response;

                    offloadInProgress.set(false);

                    if (e != null) {
                        final JsonRpc2Error error = new JsonRpc2Error(1, e.getMessage());
                        response = new JsonRpc2Response(error, requestId);
                    }
                    else {
                        response = new JsonRpc2Response(r, requestId);
                    }

                    LOG.trace("Send response `{}`.", response);
                    ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
                });
            }
            else {
                final JsonRpc2Error error = new JsonRpc2Error(1, "Node is not running.");
                final JsonRpc2Response response = new JsonRpc2Response(error, requestId);

                LOG.trace("Send response `{}`.", response);
                ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
            }
        }
        else {
            final JsonRpc2Error error = new JsonRpc2Error(1, "Node is processing another task.");
            final JsonRpc2Response response = new JsonRpc2Response(error, requestId);

            LOG.trace("Send response `{}`.", response);
            ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
        }
    }

    protected ChannelHandler getHandler(final JsonRpc2Request request,
                                        final InetSocketAddress bindAddress) {
        final boolean relayOnly = request.getParam("relayOnly", Boolean.FALSE);
        final int networkId = request.getParam("networkId", 1);
        final int onlineTimeoutMillis = request.getParam("onlineTimout", 10_000);
        final Map<String, String> superPeers = request.getParam("superPeers");
        final boolean protocolArmDisabled = request.getParam("protocolArmDisabled", false);

        /*
        if (relayOnly) {
            return new RelayOnlyConsumerJsonRpcChannelInitializer(identity.get(), group, bindAddress, networkId, onlineTimeoutMillis, parsePeersMap(superPeers), !protocolArmDisabled);
        }
        */

        final List<String> peers = request.getParam("peers");

        return new ConsumerJsonRpcChannelInitializer(identity.get(), group, bindAddress, networkId, onlineTimeoutMillis, parsePeersMap(superPeers), !protocolArmDisabled, parsePeersList(peers), relayOnly);
    }

    protected ChannelHandler getChildHandler() {
        return new ChildChannelInitializer(System.out, true);
    }

    protected static List<IdentityPublicKey> parsePeersList(final List<String> peers) {
        final List<IdentityPublicKey> rtn = new LinkedList<>();

        for (final String peer : peers) {
            rtn.add(IdentityPublicKey.of(peer));
        }

        return rtn;
    }

    protected static Map<IdentityPublicKey, InetSocketAddress> parsePeersMap(final Map<String, String> peers) {
        final Map<IdentityPublicKey, InetSocketAddress> rtn = new LinkedHashMap<>();

        for (final Map.Entry<String, String> entry : peers.entrySet()) {
            rtn.put(IdentityPublicKey.of(entry.getKey()), InetSocketAddressUtil.socketAddressFromString(entry.getValue()));
        }

        return rtn;
    }
}
