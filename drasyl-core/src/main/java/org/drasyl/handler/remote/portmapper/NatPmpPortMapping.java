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
package org.drasyl.handler.remote.portmapper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.network.NetworkUtil;
import org.drasyl.util.protocol.NatPmpUtil;
import org.drasyl.util.protocol.NatPmpUtil.ExternalAddressResponseMessage;
import org.drasyl.util.protocol.NatPmpUtil.MappingUdpResponseMessage;
import org.drasyl.util.protocol.NatPmpUtil.Message;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.handler.remote.portmapper.PortMapper.MAPPING_LIFETIME;
import static org.drasyl.util.protocol.NatPmpUtil.NAT_PMP_PORT;
import static org.drasyl.util.protocol.NatPmpUtil.ResultCode.SUCCESS;

/**
 * Port Forwarding on NAT-enabled routers via NAT-PMP.
 * <p>
 * This methods requires the following steps:
 * <ul>
 * <li>identify own default network gateway</li>
 * <li>request external address from gateway to ensure it is connected to a WAN</li>
 * <li>request port mapping from gateway</li>
 * </ul>
 */
@SuppressWarnings({ "java:S107" })
public class NatPmpPortMapping implements PortMapping {
    public static final Duration TIMEOUT = ofSeconds(10);
    private static final Logger LOG = LoggerFactory.getLogger(NatPmpPortMapping.class);
    private final AtomicBoolean externalAddressRequested;
    private final AtomicBoolean mappingRequested;
    private int port;
    private InetSocketAddress defaultGateway;
    private InetAddress externalAddress;
    private Future<?> timeoutGuard;
    private Future<?> refreshTask;
    private Runnable onFailure;
    private final Supplier<InetAddress> defaultGatewaySupplier;

    public NatPmpPortMapping(final AtomicBoolean externalAddressRequested,
                             final AtomicBoolean mappingRequested,
                             final int port,
                             final InetSocketAddress defaultGateway,
                             final InetAddress externalAddress,
                             final Future<?> timeoutGuard,
                             final Future<?> refreshTask,
                             final Runnable onFailure,
                             final Supplier<InetAddress> defaultGatewaySupplier) {
        this.externalAddressRequested = externalAddressRequested;
        this.mappingRequested = mappingRequested;
        this.port = port;
        this.defaultGateway = defaultGateway;
        this.externalAddress = externalAddress;
        this.timeoutGuard = timeoutGuard;
        this.refreshTask = refreshTask;
        this.onFailure = onFailure;
        this.defaultGatewaySupplier = defaultGatewaySupplier;
    }

    public NatPmpPortMapping() {
        this(new AtomicBoolean(), new AtomicBoolean(), 0, null, null, null, null, null, NetworkUtil::getDefaultGateway);
    }

    @Override
    public void start(final ChannelHandlerContext ctx,
                      final int port,
                      final Runnable onFailure) {
        this.onFailure = onFailure;
        this.port = port;
        mapPort(ctx);
    }

    @Override
    public void stop(final ChannelHandlerContext ctx) {
        unmapPort(ctx);
    }

    @Override
    public boolean acceptMessage(final InetSocketAddress sender,
                                 final ByteBuf msg) {
        return defaultGateway != null && defaultGateway.equals(sender);
    }

    @Override
    public void handleMessage(final ChannelHandlerContext ctx,
                              final InetSocketAddress sender,
                              final ByteBuf msg) {
        try (final InputStream in = new DataInputStream(new ByteBufInputStream(msg))) {
            final Message message = NatPmpUtil.readMessage(in);

            if (message instanceof ExternalAddressResponseMessage) {
                handleExternalAddress(ctx, (ExternalAddressResponseMessage) message);
            }
            else if (message instanceof MappingUdpResponseMessage) {
                handleMapping(ctx, (MappingUdpResponseMessage) message);
            }
            else {
                LOG.warn("Unexpected message received from `{}`. Discard it!", sender::getHostString);
            }
        }
        catch (final IOException e) {
            LOG.warn("Unable to read NAT-PMP packet: ", e);
        }
        finally {
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    private void mapPort(final ChannelHandlerContext ctx) {
        timeoutGuard = ctx.executor().schedule(() -> {
            timeoutGuard = null;
            if (refreshTask == null) {
                LOG.debug("Unable to create mapping within {}s.", TIMEOUT::toSeconds);
                fail();
            }
        }, TIMEOUT.toMillis(), MILLISECONDS);

        // first we have to identify our default network gateway
        final InetAddress defaultGatewayAddress = defaultGatewaySupplier.get();
        if (defaultGatewayAddress == null) {
            LOG.debug("Unable to determine default gateway.");
            return;
        }
        defaultGateway = new InetSocketAddress(defaultGatewayAddress, NAT_PMP_PORT);

        // now we can request the external address from the gateway
        requestExternalAddress(ctx);
    }

    private void unmapPort(final ChannelHandlerContext ctx) {
        if (timeoutGuard != null) {
            timeoutGuard.cancel(false);
        }
        if (refreshTask != null) {
            refreshTask.cancel(false);
            LOG.debug("Destroy mapping by creating a mapping request with zero lifetime.");
            requestMapping(ctx, Duration.ZERO);
        }
    }

    void fail() {
        if (timeoutGuard != null) {
            timeoutGuard.cancel(false);
            timeoutGuard = null;
        }
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        defaultGateway = null;
        externalAddress = null;
        if (onFailure != null) {
            onFailure.run();
            onFailure = null;
        }
    }

    private void requestExternalAddress(final ChannelHandlerContext ctx) {
        LOG.debug("Request external address from gateway `{}`.", defaultGateway::getHostString);

        final byte[] content = NatPmpUtil.buildExternalAddressRequestMessage();
        final ByteBuf msg = Unpooled.wrappedBuffer(content);
        externalAddressRequested.set(true);

        ctx.writeAndFlush(new InetAddressedMessage<>(msg, defaultGateway)).addListener(future -> {
            if (!future.isSuccess()) {
                LOG.warn("Unable to send external address request message to `{}`", () -> defaultGateway, future::cause);
            }
        });
    }

    private void handleExternalAddress(final ChannelHandlerContext ctx,
                                       final ExternalAddressResponseMessage message) {
        if (externalAddressRequested.compareAndSet(true, false)) {
            if (message.getResultCode() == SUCCESS) {
                externalAddress = message.getExternalAddress();
                LOG.debug("Gateway `{}` reported external address `{}`.", defaultGateway::getHostString, externalAddress::getHostAddress);

                // after ensuring that gateway has a external address, we can request a port mapping
                requestMapping(ctx, MAPPING_LIFETIME);
            }
            else {
                LOG.warn("External address request failed: Gateway returned non-zero result code: {}", message::getResultCode);
            }
        }
    }

    private void requestMapping(final ChannelHandlerContext ctx, final Duration lifetime) {
        //noinspection unchecked
        LOG.debug("Request mapping for `{}:{}/UDP` to `{}/UDP` with lifetime of {}s from gateway `{}`.", externalAddress::getHostAddress, () -> port, () -> port, lifetime::toSeconds, defaultGateway::getHostString);

        final byte[] content = NatPmpUtil.buildMappingRequestMessage(port, port, lifetime);
        final ByteBuf msg = Unpooled.wrappedBuffer(content);
        mappingRequested.set(true);

        ctx.writeAndFlush(new InetAddressedMessage<>(msg, defaultGateway)).addListener(future -> {
            if (!future.isSuccess()) {
                LOG.warn("Unable to send mapping request message to `{}`", () -> defaultGateway, future::cause);
            }
        });
    }

    private void handleMapping(final ChannelHandlerContext ctx,
                               final MappingUdpResponseMessage message) {
        if (mappingRequested.compareAndSet(true, false)) {
            if (message.getResultCode() == SUCCESS) {
                timeoutGuard.cancel(false);
                if (message.getExternalPort() == port) {
                    //noinspection unchecked
                    LOG.info("Got port mapping for `{}:{}/UDP` to `{}/UDP` with lifetime of {}s from gateway `{}`.", externalAddress::getHostAddress, message::getExternalPort, message::getInternalPort, message::getLifetime, defaultGateway::getHostString);
                    if (message.getLifetime() > 0) {
                        final long delay = message.getLifetime() / 2;
                        LOG.debug("Schedule refresh of mapping for in {}s.", delay);
                        refreshTask = ctx.executor().schedule(() -> {
                            refreshTask = null;
                            mapPort(ctx);
                        }, delay, SECONDS);
                    }
                }
                else {
                    //noinspection unchecked
                    LOG.info("Got port mapping for wrong port `{}:{}/UDP` to `{}/UDP` with lifetime of {}s from gateway `{}`.", externalAddress::getHostAddress, message::getExternalPort, message::getInternalPort, message::getLifetime, defaultGateway::getHostString);
                    fail();
                }
            }
            else {
                LOG.warn("Mapping request failed: Gateway returned non-zero result code: {}", message::getResultCode);
                fail();
            }
        }
    }

    @Override
    public String toString() {
        return "NAT-PMP";
    }
}
