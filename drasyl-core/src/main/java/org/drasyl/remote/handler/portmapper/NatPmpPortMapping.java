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
package org.drasyl.remote.handler.portmapper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.util.NetworkUtil;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.protocol.NatPmpUtil;
import org.drasyl.util.protocol.NatPmpUtil.ExternalAddressResponseMessage;
import org.drasyl.util.protocol.NatPmpUtil.MappingUdpResponseMessage;
import org.drasyl.util.protocol.NatPmpUtil.Message;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.remote.handler.portmapper.PortMapper.MAPPING_LIFETIME;
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
    private InetSocketAddressWrapper defaultGateway;
    private InetAddress externalAddress;
    private Disposable timeoutGuard;
    private Disposable refreshTask;
    private Runnable onFailure;
    private final Supplier<InetAddress> defaultGatewaySupplier;

    public NatPmpPortMapping(final AtomicBoolean externalAddressRequested,
                             final AtomicBoolean mappingRequested,
                             final int port,
                             final InetSocketAddressWrapper defaultGateway,
                             final InetAddress externalAddress,
                             final Disposable timeoutGuard,
                             final Disposable refreshTask,
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
    public void start(final HandlerContext ctx,
                      final NodeUpEvent event,
                      final Runnable onFailure) {
        this.onFailure = onFailure;
        port = event.getNode().getPort();
        mapPort(ctx);
    }

    @Override
    public void stop(final HandlerContext ctx) {
        unmapPort(ctx);
    }

    @Override
    public boolean acceptMessage(final InetSocketAddressWrapper sender,
                                 final ByteBuf msg) {
        return defaultGateway != null && defaultGateway.equals(sender);
    }

    @Override
    public void handleMessage(final HandlerContext ctx,
                              final InetSocketAddressWrapper sender,
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

    private synchronized void mapPort(final HandlerContext ctx) {
        timeoutGuard = ctx.independentScheduler().scheduleDirect(() -> {
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
        defaultGateway = new InetSocketAddressWrapper(defaultGatewayAddress, NAT_PMP_PORT);

        // now we can request the external address from the gateway
        requestExternalAddress(ctx);
    }

    private synchronized void unmapPort(final HandlerContext ctx) {
        if (timeoutGuard != null) {
            timeoutGuard.dispose();
        }
        if (refreshTask != null) {
            refreshTask.dispose();
            LOG.debug("Destroy mapping by creating a mapping request with zero lifetime.");
            requestMapping(ctx, Duration.ZERO);
        }
    }

    synchronized void fail() {
        if (timeoutGuard != null) {
            timeoutGuard.dispose();
            timeoutGuard = null;
        }
        if (refreshTask != null) {
            refreshTask.dispose();
            refreshTask = null;
        }
        defaultGateway = null;
        externalAddress = null;
        if (onFailure != null) {
            onFailure.run();
            onFailure = null;
        }
    }

    private void requestExternalAddress(final HandlerContext ctx) {
        LOG.debug("Request external address from gateway `{}`.", defaultGateway::getHostName);

        final byte[] content = NatPmpUtil.buildExternalAddressRequestMessage();
        final ByteBuf msg = Unpooled.wrappedBuffer(content);
        externalAddressRequested.set(true);

        ctx.passOutbound(defaultGateway, msg, new CompletableFuture<>());
    }

    private void handleExternalAddress(final HandlerContext ctx,
                                       final ExternalAddressResponseMessage message) {
        if (externalAddressRequested.compareAndSet(true, false)) {
            if (message.getResultCode() == SUCCESS) {
                externalAddress = message.getExternalAddress();
                LOG.debug("Gateway `{}` reported external address `{}`.", defaultGateway::getHostName, externalAddress::getHostAddress);

                // after ensuring that gateway has a external address, we can request a port mapping
                requestMapping(ctx, MAPPING_LIFETIME);
            }
            else {
                LOG.warn("External address request failed: Gateway returned non-zero result code: {}", message::getResultCode);
            }
        }
    }

    private void requestMapping(final HandlerContext ctx, final Duration lifetime) {
        LOG.debug("Request mapping for `{}:{}/UDP` to `{}/UDP` with lifetime of {}s from gateway `{}`.", externalAddress::getHostAddress, () -> port, () -> port, lifetime::toSeconds, defaultGateway::getHostName);

        final byte[] content = NatPmpUtil.buildMappingRequestMessage(port, port, lifetime);
        final ByteBuf msg = Unpooled.wrappedBuffer(content);
        mappingRequested.set(true);

        ctx.passOutbound(defaultGateway, msg, new CompletableFuture<>());
    }

    private void handleMapping(final HandlerContext ctx,
                               final MappingUdpResponseMessage message) {
        if (mappingRequested.compareAndSet(true, false)) {
            if (message.getResultCode() == SUCCESS) {
                timeoutGuard.dispose();
                if (message.getExternalPort() == port) {
                    LOG.info("Got port mapping for `{}:{}/UDP` to `{}/UDP` with lifetime of {}s from gateway `{}`.", externalAddress::getHostAddress, message::getExternalPort, message::getInternalPort, message::getLifetime, defaultGateway::getHostName);
                    if (message.getLifetime() > 0) {
                        final long delay = message.getLifetime() / 2;
                        LOG.debug("Schedule refresh of mapping for in {}s.", delay);
                        refreshTask = ctx.independentScheduler().scheduleDirect(() -> {
                            refreshTask = null;
                            mapPort(ctx);
                        }, delay, SECONDS);
                    }
                }
                else {
                    LOG.info("Got port mapping for wrong port `{}:{}/UDP` to `{}/UDP` with lifetime of {}s from gateway `{}`.", externalAddress::getHostAddress, message::getExternalPort, message::getInternalPort, message::getLifetime, defaultGateway::getHostName);
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
