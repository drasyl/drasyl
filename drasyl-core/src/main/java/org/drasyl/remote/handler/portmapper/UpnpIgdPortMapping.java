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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.event.Node;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.protocol.UpnpIgdUtil;
import org.drasyl.util.protocol.UpnpIgdUtil.DiscoveryResponseMessage;
import org.drasyl.util.protocol.UpnpIgdUtil.ExternalIpAddress;
import org.drasyl.util.protocol.UpnpIgdUtil.MappingEntry;
import org.drasyl.util.protocol.UpnpIgdUtil.Message;
import org.drasyl.util.protocol.UpnpIgdUtil.Service;
import org.drasyl.util.protocol.UpnpIgdUtil.StatusInfo;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.remote.handler.portmapper.PortMapper.MAPPING_LIFETIME;
import static org.drasyl.util.protocol.UpnpIgdUtil.SSDP_MULTICAST_ADDRESS;

/**
 * Port Forwarding on NAT-enabled routers via UPnP-IGD.
 * <p>
 * This methods requires the following steps:
 * <ul>
 * <li>SSDP: do a discovery to find internet gateway devices</li>
 * <li>UPnP: request service information from each discovered gateway</li>
 * <li>UPnP: check if device is connected and has an external ip address</li>
 * <li>UPnP: check for existing port mapping and reuse it</li>
 * <li>UPnP: if there is no port mapping, create a new one</li>
 * </ul>
 */
@SuppressWarnings({ "java:S107" })
public class UpnpIgdPortMapping implements PortMapping {
    public static final Duration TIMEOUT = ofSeconds(10);
    private static final Duration SSDP_DISCOVERY_TIMEOUT = ofSeconds(5);
    private static final Logger LOG = LoggerFactory.getLogger(UpnpIgdPortMapping.class);
    private static final int PUBLIC_KEY_DESCRIPTION_LENGTH = 10;
    private final AtomicBoolean ssdpDiscoveryActive;
    private final UpnpIgdUtil upnpIgdUtil;
    private final Set<URI> ssdpServices;
    private String description;
    private int port;
    private Disposable timeoutGuard;
    private Disposable ssdpDiscoverTask;
    private Disposable refreshTask;
    private Service upnpService;
    private Runnable onFailure;

    @SuppressWarnings("java:S2384")
    UpnpIgdPortMapping(final AtomicBoolean ssdpDiscoveryActive,
                       final UpnpIgdUtil upnpIgdUtil,
                       final Set<URI> ssdpServices,
                       final String description,
                       final int port,
                       final Disposable timeoutGuard,
                       final Disposable ssdpDiscoverTask,
                       final Disposable refreshTask,
                       final Service upnpService,
                       final Runnable onFailure) {
        this.ssdpDiscoveryActive = ssdpDiscoveryActive;
        this.upnpIgdUtil = upnpIgdUtil;
        this.ssdpServices = ssdpServices;
        this.description = description;
        this.port = port;
        this.timeoutGuard = timeoutGuard;
        this.ssdpDiscoverTask = ssdpDiscoverTask;
        this.refreshTask = refreshTask;
        this.upnpService = upnpService;
        this.onFailure = onFailure;
    }

    public UpnpIgdPortMapping() {
        this(new AtomicBoolean(), new UpnpIgdUtil(), new HashSet<>(), null, 0, null, null, null, null, null);
    }

    @Override
    public void start(final HandlerContext ctx,
                      final NodeUpEvent event,
                      final Runnable onFailure) {
        this.onFailure = onFailure;
        final Node node = event.getNode();
        port = node.getPort();
        description = "drasyl" + node.getIdentity().getIdentityPublicKey().toString().substring(0, PUBLIC_KEY_DESCRIPTION_LENGTH);
        mapPort(ctx);
    }

    @Override
    public void stop(final HandlerContext ctx) {
        unmapPort(ctx);
    }

    @Override
    public boolean acceptMessage(final InetSocketAddressWrapper sender,
                                 final ByteBuf msg) {
        return sender != null
                && sender.getPort() == SSDP_MULTICAST_ADDRESS.getPort();
    }

    @Override
    public void handleMessage(final HandlerContext ctx,
                              final InetSocketAddressWrapper sender,
                              final ByteBuf msg) {
        try {
            if (ssdpDiscoveryActive.get()) {
                final Message message = UpnpIgdUtil.readMessage(ByteBufUtil.getBytes(msg));

                if (message instanceof DiscoveryResponseMessage) {
                    final String serviceType = ((DiscoveryResponseMessage) message).getServiceType();
                    final String location = ((DiscoveryResponseMessage) message).getLocation();
                    if (serviceType.startsWith("urn:schemas-upnp-org:service:WANCommonInterfaceConfig:")) {
                        try {
                            ssdpServices.add(new URI(location));
                            //noinspection unchecked
                            LOG.debug("Got UPnP service of type `{}` with location `{}` reported from `{}`", () -> serviceType, () -> location, sender::getHostString);
                        }
                        catch (final URISyntaxException e) {
                            LOG.debug("Unable to parse received service location.", e);
                        }
                    }
                }
                else {
                    LOG.warn("Unexpected message received from `{}`. Discard it!", sender::getHostString);
                }
            }
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

        doSsdpDiscovery(ctx);
    }

    private synchronized void unmapPort(final HandlerContext ctx) {
        ctx.independentScheduler().scheduleDirect(() -> {
            if (upnpService != null) {
                try {
                    LOG.debug("Delete mapping for `{}/UDP`.", () -> port);
                    upnpIgdUtil.deletePortMapping(upnpService.getControlUrl(), upnpService.getServiceType(), port);
                    upnpService = null;
                }
                catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    void fail() {
        if (timeoutGuard != null) {
            timeoutGuard.dispose();
            timeoutGuard = null;
        }
        if (refreshTask != null) {
            refreshTask.dispose();
            refreshTask = null;
        }
        if (ssdpDiscoverTask != null) {
            ssdpDiscoverTask.dispose();
            ssdpDiscoverTask = null;
        }
        this.upnpService = null;
        if (onFailure != null) {
            onFailure.run();
            onFailure = null;
        }
    }

    private void doSsdpDiscovery(final HandlerContext ctx) {
        LOG.debug("Send SSDP discovery message to broadcast address `{}`.", SSDP_MULTICAST_ADDRESS);
        final byte[] content = UpnpIgdUtil.buildDiscoveryMessage();
        final ByteBuf msg = Unpooled.wrappedBuffer(content);
        ssdpServices.clear();
        ssdpDiscoveryActive.set(true);
        ssdpDiscoverTask = ctx.independentScheduler().scheduleDirect(() -> {
            ssdpDiscoveryActive.set(false);
            final Set<URI> serviceLocations = this.ssdpServices;
            LOG.debug("Stop SSDP discovery. Found {} service(s).", serviceLocations.size());
            if (serviceLocations.isEmpty()) {
                LOG.debug("No internet gateway devices discovered.");
                fail();
            }
            else {
                try {
                    for (final URI serviceLocation : serviceLocations) {
                        if (!createMappingAtService(serviceLocation)) {
                            // failed
                            continue;
                        }

                        final long delay = MAPPING_LIFETIME.dividedBy(2).toSeconds();
                        LOG.debug("Schedule refresh of mapping for in {}s.", delay);
                        refreshTask = ctx.independentScheduler().scheduleDirect(() -> {
                            refreshTask = null;
                            mapPort(ctx);
                        }, delay, SECONDS);
                        return;
                    }

                    LOG.debug("Unable to create mapping.");
                    fail();
                }
                catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, SSDP_DISCOVERY_TIMEOUT.toMillis(), MILLISECONDS);
        ctx.passOutbound(SSDP_MULTICAST_ADDRESS, msg, new CompletableFuture<>()).exceptionally(e -> {
            LOG.warn("Unable to send ssdp discovery message to `{}`", () -> SSDP_MULTICAST_ADDRESS, () -> e);
            return null;
        });
    }

    @SuppressWarnings({ "java:S1142", "java:S1541" })
    private boolean createMappingAtService(final URI serviceLocation) throws InterruptedException {
        final Service service = upnpIgdUtil.getUpnpService(serviceLocation);
        if (service == null) {
            LOG.debug("Unable to get service information from `{}`.", serviceLocation);
            return false;
        }

        // check that gateway is connected
        final StatusInfo statusInfo = upnpIgdUtil.getStatusInfo(service.getControlUrl(), service.getServiceType());
        if (statusInfo == null || !statusInfo.isConnected()) {
            LOG.debug("Service at location `{}` is not connected.", serviceLocation);
            return false;
        }

        // check that gateway has external address
        final ExternalIpAddress externalIpAddress = upnpIgdUtil.getExternalIpAddress(service.getControlUrl(), service.getServiceType());
        if (externalIpAddress == null || externalIpAddress.getNewExternalIpAddress() == null) {
            LOG.debug("Service at location `{}` has no external address.", serviceLocation);
            return false;
        }

        // check if there is already an existing port mapping. If so, reuse it...
        final MappingEntry mappingEntry = upnpIgdUtil.getSpecificPortMappingEntry(service.getControlUrl(), service.getServiceType(), port);
        if (mappingEntry != null && mappingEntry.getErrorCode() == 0 && description.equals(mappingEntry.getDescription())) {
            //noinspection unchecked
            LOG.debug("Reuse existing port mapping with description `{}` for `{}:{}/UDP` to `{}:{}/UDP`.", mappingEntry::getDescription, externalIpAddress.getNewExternalIpAddress()::getHostAddress, () -> port, mappingEntry.getInternalClient()::getHostAddress, mappingEntry::getInternalPort);
            this.upnpService = service;
            return true;
        }

        // ...otherwise create a new mapping
        final UpnpIgdUtil.PortMapping mapping = upnpIgdUtil.addPortMapping(service.getControlUrl(), service.getServiceType(), port, service.getLocalAddress(), description);
        if (mapping != null && mapping.getErrorCode() == 0) {
            this.upnpService = service;
            //noinspection unchecked
            LOG.info("Port mapping created with description `{}` for `{}:{}/UDP` to `{}/UDP`.", () -> description, externalIpAddress.getNewExternalIpAddress()::getHostAddress, () -> port, () -> port);
            return true;
        }
        else {
            LOG.debug("Unable to create mapping.");
            return false;
        }
    }

    @Override
    public String toString() {
        return "UPnP-IGD";
    }
}
