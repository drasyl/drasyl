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
package org.drasyl.remote.handler.portmapper;

import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.protocol.AddressedByteBuf;
import org.drasyl.util.NetworkUtil;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.protocol.PcpPortUtil;
import org.drasyl.util.protocol.PcpPortUtil.MappingResponseMessage;
import org.drasyl.util.protocol.PcpPortUtil.Message;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.remote.handler.portmapper.PortMapper.MAPPING_LIFETIME;
import static org.drasyl.util.protocol.PcpPortUtil.ResultCode.SUCCESS;

/**
 * Port Forwarding on NAT-enabled routers via PCP.
 * <p>
 * This methods requires the following steps:
 * <ul>
 * <li>identify own default network gateway</li>
 * <li>send mapping request to gateway for every local network address</li>
 * <li>gateway should only create mapping for local network address it belongs to</li>
 * </ul>
 */
@SuppressWarnings({ "java:S107" })
public class PcpPortMapping implements PortMapping {
    public static final Duration TIMEOUT = ofSeconds(10);
    private static final Logger LOG = LoggerFactory.getLogger(PcpPortMapping.class);
    private final AtomicInteger mappingRequested;
    private int port;
    private Runnable onFailure;
    private byte[] nonce;
    private InetSocketAddressWrapper defaultGateway;
    private Disposable timeoutGuard;
    private Disposable refreshTask;
    private Set<InetAddress> interfaces;
    private final Supplier<InetAddress> defaultGatewaySupplier;
    private final Supplier<Set<InetAddress>> interfacesSupplier;

    PcpPortMapping(final AtomicInteger mappingRequested,
                   final int port,
                   final Runnable onFailure,
                   final byte[] nonce,
                   final InetSocketAddressWrapper defaultGateway,
                   final Disposable timeoutGuard,
                   final Disposable refreshTask,
                   final Set<InetAddress> interfaces,
                   final Supplier<InetAddress> defaultGatewaySupplier,
                   final Supplier<Set<InetAddress>> interfaceSupplier) {
        this.mappingRequested = mappingRequested;
        this.port = port;
        this.onFailure = onFailure;
        this.nonce = nonce;
        this.defaultGateway = defaultGateway;
        this.timeoutGuard = timeoutGuard;
        this.refreshTask = refreshTask;
        this.interfaces = interfaces;
        this.defaultGatewaySupplier = defaultGatewaySupplier;
        this.interfacesSupplier = interfaceSupplier;
    }

    public PcpPortMapping() {
        this(new AtomicInteger(), 0, null, new byte[]{}, null, null, null, null, NetworkUtil::getDefaultGateway, NetworkUtil::getAddresses);
    }

    @Override
    public void start(final HandlerContext ctx, final NodeUpEvent event, final Runnable onFailure) {
        this.onFailure = onFailure;
        port = event.getNode().getPort();
        interfaces = interfacesSupplier.get();
        nonce = new byte[12];
        final byte[] publicKeyBytes = ctx.identity().getPublicKey().byteArrayValue();
        System.arraycopy(publicKeyBytes, 0, nonce, 0, nonce.length);
        mapPort(ctx);
    }

    @Override
    public void stop(final HandlerContext ctx) {
        unmapPort(ctx);
    }

    @Override
    public boolean acceptMessage(final AddressedByteBuf msg) {
        return defaultGateway != null && defaultGateway.equals(msg.getSender());
    }

    @Override
    public void handleMessage(final HandlerContext ctx, final AddressedByteBuf msg) {
        try (final DataInputStream in = new DataInputStream(new ByteBufInputStream(msg.getContent()))) {
            final Message message = PcpPortUtil.readMessage(in);

            if (message instanceof MappingResponseMessage) {
                handleMapping(ctx, (MappingResponseMessage) message);
            }
            else {
                LOG.warn("Unexpected message received from `{}`. Discard it!", msg.getSender().getAddress()::getHostString);
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

        // first we have to identify our default gateway
        final InetAddress defaultGatewayAddress = defaultGatewaySupplier.get();
        if (defaultGatewayAddress == null) {
            LOG.debug("Unable to determine default gateway.");
            return;
        }
        defaultGateway = InetSocketAddressWrapper.of(new InetSocketAddress(defaultGatewayAddress, PcpPortUtil.PCP_PORT));

        for (final InetAddress clientAddress : interfaces) {
            LOG.debug("Send MAP opcode request to gateway `{}` with client address `{}`.", defaultGateway.getAddress()::getHostName, clientAddress::getHostAddress);
            requestMapping(ctx, MAPPING_LIFETIME, clientAddress, nonce, PcpPortUtil.PROTO_UDP, port, PcpPortUtil.ZERO_IPV4);
        }
    }

    private synchronized void unmapPort(final HandlerContext ctx) {
        if (timeoutGuard != null) {
            timeoutGuard.dispose();
        }
        if (refreshTask != null) {
            refreshTask.dispose();
            LOG.debug("Destroy mapping by creating a mapping request with zero lifetime.");
            for (final InetAddress clientAddress : interfaces) {
                requestMapping(ctx, Duration.ZERO, clientAddress, nonce, PcpPortUtil.PROTO_UDP, port, PcpPortUtil.ZERO_IPV4);
            }
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
        if (onFailure != null) {
            onFailure.run();
            onFailure = null;
        }
    }

    @SuppressWarnings({ "SameParameterValue", "java:S107" })
    private void requestMapping(final HandlerContext ctx,
                                final Duration lifetime,
                                final InetAddress clientAddress,
                                final byte[] nonce,
                                final int protocol,
                                final int port,
                                final InetAddress externalAddress) {
        LOG.debug("Send MAP opcode request for `{}:{}/UDP` to `{}:{}/UDP` with lifetime of {}s to gateway `{}`.", externalAddress::getHostAddress, () -> port, clientAddress::getHostAddress, () -> port, lifetime::toSeconds, defaultGateway.getAddress()::getHostName);

        final byte[] content = PcpPortUtil.buildMappingRequestMessage(lifetime, clientAddress, nonce, protocol, port, externalAddress);
        final AddressedByteBuf envelope = new AddressedByteBuf(null, defaultGateway, Unpooled.wrappedBuffer(content));
        mappingRequested.incrementAndGet();

        ctx.write(envelope.getRecipient(), envelope, new CompletableFuture<>());
    }

    private void handleMapping(final HandlerContext ctx,
                               final MappingResponseMessage message) {
        if (mappingRequested.getAndIncrement() > 0) {
            if (message.getResultCode() == SUCCESS) {
                timeoutGuard.dispose();
                if (message.getExternalSuggestedPort() == port) {
                    mappingRequested.set(0);
                    LOG.info("Got port mapping for `{}:{}/UDP` to `{}/UDP` with lifetime of {}s from gateway `{}`.", message.getExternalSuggestedAddress()::getHostAddress, message::getExternalSuggestedPort, message::getInternalPort, message::getLifetime, defaultGateway.getAddress()::getHostName);
                    if (message.getLifetime() > 0) {
                        final long delay = message.getLifetime() / 2;
                        LOG.debug("Schedule refresh of mapping for in {}s.", delay);
                        refreshTask = ctx.independentScheduler().scheduleDirect(() -> {
                            refreshTask = null;
                            mapPort(ctx);
                        }, delay, SECONDS);
                    }
                    return;
                }
                else {
                    LOG.info("Got port mapping for wrong port `{}:{}/UDP` to `{}/UDP` with lifetime of {}s from gateway `{}`.", message.getExternalSuggestedAddress()::getHostAddress, message::getExternalSuggestedPort, message::getInternalPort, message::getLifetime, defaultGateway.getAddress()::getHostName);
                }
            }
            else {
                LOG.debug("Mapping request failed: Gateway returned non-zero result code: {}", message.getResultCode());
            }

            if (mappingRequested.get() == 0) {
                LOG.warn("All mapping requests failed.");
                fail();
            }
        }
    }

    @Override
    public String toString() {
        return "PCP";
    }
}
