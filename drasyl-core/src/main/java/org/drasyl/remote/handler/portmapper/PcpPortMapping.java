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
import org.drasyl.util.protocol.PcpPortUtil;
import org.drasyl.util.protocol.PcpPortUtil.MappingResponseMessage;
import org.drasyl.util.protocol.PcpPortUtil.Message;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.remote.handler.portmapper.PortMapper.MAPPING_LIFETIME;
import static org.drasyl.util.protocol.PcpPortUtil.MAPPING_NONCE_LENGTH;
import static org.drasyl.util.protocol.PcpPortUtil.PCP_PORT;
import static org.drasyl.util.protocol.PcpPortUtil.PROTO_UDP;
import static org.drasyl.util.protocol.PcpPortUtil.ResultCode.SUCCESS;
import static org.drasyl.util.protocol.PcpPortUtil.ZERO_IPV4;

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

    @SuppressWarnings("java:S2384")
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

        if (!interfaces.isEmpty()) {
            nonce = new byte[MAPPING_NONCE_LENGTH];
            final byte[] publicKeyBytes = ctx.identity().getPublicKey().byteArrayValue();
            System.arraycopy(publicKeyBytes, 0, nonce, 0, nonce.length);
            mapPort(ctx);
        }
        else {
            LOG.warn("Network interfaces could not be determined.");
            fail();
        }
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
        try (final DataInputStream in = new DataInputStream(new ByteBufInputStream(msg))) {
            final Message message = PcpPortUtil.readMessage(in);

            if (message instanceof MappingResponseMessage) {
                handleMapping(ctx, (MappingResponseMessage) message);
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

        // first we have to identify our default gateway
        final InetAddress defaultGatewayAddress = defaultGatewaySupplier.get();
        if (defaultGatewayAddress == null) {
            LOG.debug("Unable to determine default gateway.");
            return;
        }
        defaultGateway = new InetSocketAddressWrapper(defaultGatewayAddress, PCP_PORT);

        for (final InetAddress clientAddress : interfaces) {
            LOG.debug("Send MAP opcode request to gateway `{}` with client address `{}`.", defaultGateway::getHostName, clientAddress::getHostAddress);
            requestMapping(ctx, MAPPING_LIFETIME, clientAddress, nonce, PROTO_UDP, port, ZERO_IPV4);
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
                requestMapping(ctx, Duration.ZERO, clientAddress, nonce, PROTO_UDP, port, ZERO_IPV4);
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
        //noinspection unchecked
        LOG.debug("Send MAP opcode request for `{}:{}/UDP` to `{}:{}/UDP` with lifetime of {}s to gateway `{}`.", externalAddress::getHostAddress, () -> port, clientAddress::getHostAddress, () -> port, lifetime::toSeconds, defaultGateway::getHostName);

        final byte[] content = PcpPortUtil.buildMappingRequestMessage(lifetime, clientAddress, nonce, protocol, port, externalAddress);
        final ByteBuf msg = Unpooled.wrappedBuffer(content);
        mappingRequested.incrementAndGet();

        ctx.passOutbound(defaultGateway, msg, new CompletableFuture<>()).exceptionally(e -> {
            LOG.warn("Unable to send mapping request message to `{}`", () -> defaultGateway, () -> e);
            return null;
        });
    }

    private synchronized void handleMapping(final HandlerContext ctx,
                                            final MappingResponseMessage message) {
        if (mappingRequested.get() > 0) {
            final int openRequests = mappingRequested.decrementAndGet();
            if (message.getResultCode() == SUCCESS) {
                timeoutGuard.dispose();
                if (message.getExternalSuggestedPort() == port) {
                    mappingRequested.set(0);
                    //noinspection unchecked
                    LOG.info("Got port mapping for `{}:{}/UDP` to `{}/UDP` with lifetime of {}s from gateway `{}`.", message.getExternalSuggestedAddress()::getHostAddress, message::getExternalSuggestedPort, message::getInternalPort, message::getLifetime, defaultGateway::getHostName);
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
                    //noinspection unchecked
                    LOG.info("Got port mapping for wrong port `{}:{}/UDP` to `{}/UDP` with lifetime of {}s from gateway `{}`.", message.getExternalSuggestedAddress()::getHostAddress, message::getExternalSuggestedPort, message::getInternalPort, message::getLifetime, defaultGateway::getHostName);
                }
            }
            else {
                LOG.debug("Mapping request failed: Gateway returned non-zero result code: {}", message.getResultCode());
            }

            if (openRequests == 0) {
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
