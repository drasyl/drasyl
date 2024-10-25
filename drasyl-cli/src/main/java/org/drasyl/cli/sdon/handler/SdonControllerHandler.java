/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.sdon.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.sdon.config.*;
import org.drasyl.cli.sdon.event.SdonMessageReceived;
import org.drasyl.cli.sdon.message.ControllerHello;
import org.drasyl.cli.sdon.message.DeviceHello;
import org.drasyl.cli.sdon.message.SdonMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.luaj.vm2.LuaString;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.cli.sdon.handler.SdonControllerHandler.State.INITIALIZED;

public class SdonControllerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(SdonControllerHandler.class);
    private final NetworkConfig config;
    private State state = null;
    private ScheduledFuture<?> notifyListenerPromise;

    public SdonControllerHandler(final NetworkConfig config) {
        this.config = requireNonNull(config);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            ensureHandlerInitialized(ctx);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ensureHandlerInitialized(ctx);
        ctx.fireChannelActive();
    }

    private void ensureHandlerInitialized(final ChannelHandlerContext ctx) {
        if (state == null) {
            state = INITIALIZED;
            ctx.pipeline().addLast(new NetworkConfigHandler(config));

            System.out.println("------------------------------------------------------------------------------------------------");
            System.out.println("Controller listening on address " + ctx.channel().localAddress());
            System.out.println("------------------------------------------------------------------------------------------------");

            ctx.executor().scheduleAtFixedRate(() -> {
                try {
                    final Network network = config.network();

                    // call callback
                    network.callCallback();

                    // do matchmaking
                    final Set<Device> assignedDevices = new HashSet<>();
                    final Map<LuaString, NetworkNode> nodes = network.getNodes();
                    for (final Entry<LuaString, NetworkNode> entry : nodes.entrySet()) {
                        final LuaString name = entry.getKey();
                        final NetworkNode node = entry.getValue();

                        Device bestMatch = null;
                        int minDistance = Integer.MAX_VALUE;
                        for (final Device device : network.getDevices()) {
                            if (!assignedDevices.contains(device)) {
                                final int distance = node.getDistance(device);
                                if (distance < minDistance) {
                                    minDistance = distance;
                                    bestMatch = device;
                                }
                            }
                        }

                        if (bestMatch != null) {
                            assignedDevices.add(bestMatch);
                            node.setDevice(bestMatch);
                        }
                    }

                    // disseminate policies
                    for (final Device device : network.getDevices()) {
                        LuaString name = null;
                        NetworkNode node = null;
                        for (final Entry<LuaString, NetworkNode> entry : nodes.entrySet()) {
                            if (Objects.equals(entry.getValue().device(), device.address())) {
                                name = entry.getKey();
                                node = entry.getValue();
                            }
                        }
                        final Set<Policy> policies = node.createPolicies();

                        final ControllerHello controllerHello = new ControllerHello(policies);
                        LOG.debug("Send {} to {}.", controllerHello, device.address());
                        final DrasylChannel channel = ((DrasylServerChannel) ctx.channel()).getChannels().get(device.address());
                        channel.writeAndFlush(controllerHello).addListener(FIRE_EXCEPTION_ON_FAILURE);
                    }
                }
                catch (final Exception e) {
                    ctx.fireExceptionCaught(e);
                }
            }, 1_000, 5_000, MILLISECONDS);
        }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) throws IOException {
        if (evt instanceof SdonMessageReceived) {
            final DrasylAddress sender = ((SdonMessageReceived) evt).node();
            final SdonMessage msg = ((SdonMessageReceived) evt).msg();
            LOG.trace("Received from `{}`: {}`", sender, msg);

            if (msg instanceof DeviceHello) {
                final DeviceHello deviceHello = (DeviceHello) msg;

                // add devices
                final Network network = config.network();
                final Device device = network.getOrCreateDevice(sender, deviceHello.tags());

                final DrasylChannel channel = ((DrasylServerChannel) ctx.channel()).getChannels().get(sender);
                if (device.isOffline()) {
                    channel.closeFuture().addListener((ChannelFutureListener) future -> {
                        device.setOffline();
                        LOG.info("`{}` now offline.", sender);
                    });

                    device.setOnline();
                    LOG.info("`{}` now online.", sender);

                    final ControllerHello controllerHello = new ControllerHello();
                    LOG.debug("Send {} to {}.", controllerHello, sender);
                    channel.writeAndFlush(controllerHello).addListener(FIRE_EXCEPTION_ON_FAILURE);
                }

                //
//                final NodeTable networkNode = network.getNode(sender);
//                if (channel == null) {
//                    LOG.error("Got {} from node `{}`. But have no channel for it. Discard!?", StringUtil.simpleClassName(msg), sender);
//                }
//                else if (networkNode != null) {
//                    if (networkNode.state().isOffline()) {
//                        channel.closeFuture().addListener((ChannelFutureListener) future -> {
//                            networkNode.state().setOffline();
//                            LOG.info("`{}` left network.", sender);
//                            //scheduleNotifyListener(ctx);
//                        });
//
//                        networkNode.state().setOnline();
//                        LOG.info("`{}` joined network.", sender);
//
//                        final Map<DrasylAddress, Peer> peers = ((NodeHello) msg).peersList().peers();
//                        for (final DrasylAddress address : Set.copyOf(peers.keySet())) {
//                            if (network.getNode(address) == null) {
//                                peers.remove(address);
//                            }
//                        }
//                        networkNode.state().setState(((NodeHello) msg).policies(), peers, ((NodeHello) msg).store());
//                        //scheduleNotifyListener(ctx);
//
//                        //final ControllerHello controllerHello = new ControllerHello(networkNode.policies());
//                        //LOG.debug("Send {} to {}.", controllerHello, sender);
//                        //channel.writeAndFlush(controllerHello).addListener(FIRE_EXCEPTION_ON_FAILURE);
//                    }
//                    else {
//                        final Map<DrasylAddress, Peer> peers = ((NodeHello) msg).peersList().peers();
//                        for (final DrasylAddress address : Set.copyOf(peers.keySet())) {
//                            if (network.getNode(address) == null) {
//                                peers.remove(address);
//                            }
//                        }
//                        networkNode.state().setState(((NodeHello) msg).policies(), peers, ((NodeHello) msg).store());
//                        //scheduleNotifyListener(ctx);
//                    }
//                }
//                else {
//                    LOG.error("Got {} from non-network node `{}`. Deny.", StringUtil.simpleClassName(msg), sender);
//                    channel.writeAndFlush(new AccessDenied());
//                }
            }
        }
        else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    private void scheduleNotifyListener(final ChannelHandlerContext ctx) {
        if (notifyListenerPromise == null) {
            notifyListenerPromise = ctx.executor().schedule(() -> {
                final Network network = config.network();
                final long startTime = System.currentTimeMillis();
                try {
//                    LOG.error("scheduleNotifyListener start");
                    network.notifyListener(ctx);
                }
                catch (final IOException e) {
                    ctx.fireExceptionCaught(e);
                }
                finally {
                    final long endTime = System.currentTimeMillis();
//                    LOG.error("scheduleNotifyListener stop: {}ms", endTime - startTime);
                    notifyListenerPromise = null;
                }
            }, 1000, MILLISECONDS);
        }
    }

    enum State {
        INITIALIZED,
    }
}
