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
package org.drasyl.localhost;

import com.fasterxml.jackson.core.type.TypeReference;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.SetUtil;
import org.drasyl.util.ThrowingBiConsumer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.network.NetworkUtil;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.drasyl.util.RandomUtil.randomLong;

/**
 * Uses the file system to discover other drasyl nodes running on the local computer.
 * <p>
 * To do this, all nodes regularly write their {@link org.drasyl.remote.handler.UdpServer}
 * address(es) to the file system. At the same time the file system is monitored to detect other
 * nodes. If the file system does not support monitoring ({@link WatchService}), a fallback to
 * polling is used.
 * <p>
 * Inspired by: <a href="https://github.com/actoron/jadex/blob/10e464b230d7695dfd9bf2b36f736f93d69ee314/platform/base/src/main/java/jadex/platform/service/awareness/LocalHostAwarenessAgent.java">Jadex</a>
 */
@SuppressWarnings("java:S1192")
public class LocalHostDiscovery extends SimpleOutboundHandler<RemoteEnvelope<Application>, IdentityPublicKey> {
    private static final Logger LOG = LoggerFactory.getLogger(LocalHostDiscovery.class);
    private static final Object path = LocalHostDiscovery.class;
    public static final Duration REFRESH_INTERVAL_SAFETY_MARGIN = ofSeconds(5);
    public static final Duration WATCH_SERVICE_POLL_INTERVAL = ofSeconds(5);
    public static final String FILE_SUFFIX = ".json";
    private final ThrowingBiConsumer<File, Object, IOException> jacksonWriter;
    private final Map<IdentityPublicKey, InetSocketAddressWrapper> routes;
    private Disposable watchDisposable;
    private Disposable postDisposable;
    private WatchService watchService; // NOSONAR

    public LocalHostDiscovery() {
        this(
                JACKSON_WRITER::writeValue,
                new HashMap<>(),
                null,
                null
        );
    }

    @SuppressWarnings({ "java:S107" })
    LocalHostDiscovery(final ThrowingBiConsumer<File, Object, IOException> jacksonWriter,
                       final Map<IdentityPublicKey, InetSocketAddressWrapper> routes,
                       final Disposable watchDisposable,
                       final Disposable postDisposable) {
        this.jacksonWriter = requireNonNull(jacksonWriter);
        this.routes = requireNonNull(routes);
        this.watchDisposable = watchDisposable;
        this.postDisposable = postDisposable;
    }

    @Override
    public void onEvent(final HandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        final FutureCombiner combiner = FutureCombiner.getInstance();

        if (event instanceof NodeUpEvent) {
            combiner.add(startDiscovery(ctx, ((NodeUpEvent) event).getNode().getPort()));
        }
        else if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            combiner.add(stopDiscovery(ctx));
        }

        // passthrough event
        combiner.add(ctx.passEvent(event, new CompletableFuture<>()))
                .combine(future);
    }

    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final IdentityPublicKey recipient,
                                   final RemoteEnvelope<Application> envelope,
                                   final CompletableFuture<Void> future) {
        final InetSocketAddressWrapper localAddress = routes.get(recipient);
        if (localAddress != null) {
            LOG.trace("Send message `{}` via local route {}.", () -> envelope, () -> localAddress);
            ctx.passOutbound(localAddress, envelope, future);
        }
        else {
            // passthrough message
            ctx.passOutbound(recipient, envelope, future);
        }
    }

    private synchronized CompletableFuture<Void> startDiscovery(final HandlerContext ctx,
                                                                final int port) {
        LOG.debug("Start Local Host Discovery...");
        final Path discoveryPath = discoveryPath(ctx);
        final File directory = discoveryPath.toFile();

        if (!directory.mkdirs() && !directory.exists()) {
            LOG.warn("Discovery directory '{}' could not be created.", discoveryPath::toAbsolutePath);
        }
        else if (!(directory.isDirectory() && directory.canRead() && directory.canWrite())) {
            LOG.warn("Discovery directory '{}' not accessible.", discoveryPath::toAbsolutePath);
        }
        else {
            if (ctx.config().isRemoteLocalHostDiscoveryWatchEnabled()) {
                tryWatchDirectory(ctx, discoveryPath);
            }
            ctx.dependentScheduler().scheduleDirect(() -> scan(ctx));
            keepOwnInformationUpToDate(ctx, discoveryPath.resolve(ctx.identity().getIdentityPublicKey().toString() + ".json"), port);
        }
        LOG.debug("Local Host Discovery started.");

        return completedFuture(null);
    }

    private synchronized CompletableFuture<Void> stopDiscovery(final HandlerContext ctx) {
        LOG.debug("Stop Local Host Discovery...");

        if (watchDisposable != null) {
            watchDisposable.dispose();
        }
        if (postDisposable != null) {
            postDisposable.dispose();
        }

        final Path filePath = discoveryPath(ctx).resolve(ctx.identity().getIdentityPublicKey().toString() + ".json");
        if (filePath.toFile().exists()) {
            try {
                Files.delete(filePath);
            }
            catch (final IOException e) {
                LOG.debug("Unable to delete `{}`", filePath, e);
            }
        }

        routes.keySet().forEach(publicKey -> ctx.peersManager().removePath(publicKey, LocalHostDiscovery.path));
        routes.clear();

        LOG.debug("Local Host Discovery stopped.");

        return completedFuture(null);
    }

    /**
     * Tries to monitor {@code discoveryPath} so that any changes are automatically reported. If
     * this is not possible, we have to fall back to periodical polling.
     */
    private void tryWatchDirectory(final HandlerContext ctx, final Path discoveryPath) {
        try {
            final File directory = discoveryPath.toFile();
            final FileSystem fileSystem = discoveryPath.getFileSystem();
            watchService = fileSystem.newWatchService();
            discoveryPath.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
            LOG.debug("Watch service for directory '{}' registered", directory);
            final long pollInterval = WATCH_SERVICE_POLL_INTERVAL.toMillis();
            watchDisposable = ctx.dependentScheduler().schedulePeriodicallyDirect(() -> {
                if (watchService.poll() != null) {
                    // directory has been changed
                    scan(ctx);
                }
            }, randomLong(pollInterval), pollInterval, MILLISECONDS);
        }
        catch (final IOException e) {
            LOG.debug("Unable to register watch service. Use polling as fallback: ", e);

            // use polling as fallback
            watchService = null;
        }
    }

    /**
     * Writes periodically the actual own information to {@link #discoveryPath}.
     */
    private void keepOwnInformationUpToDate(final HandlerContext ctx,
                                            final Path filePath,
                                            final int port) {
        // get own address(es)
        final Set<InetAddress> addresses;
        if (ctx.config().getRemoteBindHost().isAnyLocalAddress()) {
            // use all available addresses
            addresses = NetworkUtil.getAddresses();
        }
        else {
            // use given host
            addresses = Set.of(ctx.config().getRemoteBindHost());
        }
        final Set<InetSocketAddress> socketAddresses = addresses.stream().map(a -> new InetSocketAddress(a, port)).collect(Collectors.toSet());

        final Duration refreshInterval;
        if (ctx.config().getRemoteLocalHostDiscoveryLeaseTime().compareTo(REFRESH_INTERVAL_SAFETY_MARGIN) > 0) {
            refreshInterval = ctx.config().getRemoteLocalHostDiscoveryLeaseTime().minus(REFRESH_INTERVAL_SAFETY_MARGIN);
        }
        else {
            refreshInterval = ofSeconds(1);
        }
        postDisposable = ctx.dependentScheduler().schedulePeriodicallyDirect(() -> {
            // only scan in polling mode when watchService does not work
            if (watchService == null) {
                scan(ctx);
            }
            postInformation(filePath, socketAddresses);
        }, randomLong(refreshInterval.toMillis()), refreshInterval.toMillis(), MILLISECONDS);
    }

    /**
     * Scans in {@link #discoveryPath} for ports of other drasyl nodes.
     *
     * @param ctx handler's context
     */
    @SuppressWarnings("java:S134")
    synchronized void scan(final HandlerContext ctx) {
        final Path discoveryPath = discoveryPath(ctx);
        LOG.debug("Scan directory {} for new peers.", discoveryPath);
        final String ownPublicKeyString = ctx.identity().getIdentityPublicKey().toString();
        final long maxAge = System.currentTimeMillis() - ctx.config().getRemoteLocalHostDiscoveryLeaseTime().toMillis();
        final File[] files = discoveryPath.toFile().listFiles();
        if (files != null) {
            final Map<IdentityPublicKey, InetSocketAddress> newRoutes = new HashMap<>();
            for (final File file : files) {
                try {
                    final String fileName = file.getName();
                    if (file.lastModified() >= maxAge && fileName.length() == IdentityPublicKey.KEY_LENGTH_AS_STRING + FILE_SUFFIX.length() && fileName.endsWith(FILE_SUFFIX) && !fileName.startsWith(ownPublicKeyString)) {
                        final IdentityPublicKey publicKey = IdentityPublicKey.of(fileName.replace(".json", ""));
                        final TypeReference<Set<InetSocketAddress>> typeRef = new TypeReference<>() {
                        };
                        final Set<InetSocketAddress> addresses = JACKSON_READER.forType(typeRef).readValue(file);
                        if (!addresses.isEmpty()) {
                            LOG.trace("Addresses '{}' for peer '{}' discovered by file '{}'", addresses, publicKey, fileName);
                            final InetSocketAddress firstAddress = SetUtil.firstElement(addresses);
                            newRoutes.put(publicKey, firstAddress);
                        }
                    }
                }
                catch (final IllegalArgumentException | IOException e) {
                    LOG.warn("Unable to read peer information from '{}': ", file.getAbsolutePath(), e);
                }
            }

            updateRoutes(ctx, newRoutes);
        }
    }

    private void updateRoutes(final HandlerContext ctx,
                              final Map<IdentityPublicKey, InetSocketAddress> newRoutes) {
        // remove outdated routes
        for (final Iterator<IdentityPublicKey> i = routes.keySet().iterator();
             i.hasNext(); ) {
            final IdentityPublicKey publicKey = i.next();

            if (!newRoutes.containsKey(publicKey)) {
                LOG.trace("Addresses for peer '{}' are outdated. Remove peer from routing table.", publicKey);
                ctx.peersManager().removePath(publicKey, path);
                i.remove();
            }
        }

        // add new routes
        newRoutes.forEach(((publicKey, address) -> {
            if (!routes.containsKey(publicKey)) {
                routes.put(publicKey, new InetSocketAddressWrapper(address));
                ctx.peersManager().addPath(publicKey, path);
            }
        }));
    }

    /**
     * Posts own port to {@code filePath}.
     */
    @SuppressWarnings("java:S2308")
    private void postInformation(final Path filePath,
                                 final Set<InetSocketAddress> addresses) {
        LOG.trace("Post own addresses '{}' to file '{}'", addresses, filePath);
        final File file = filePath.toFile();
        try {
            if (!file.setLastModified(System.currentTimeMillis())) {
                jacksonWriter.accept(file, addresses);
                file.deleteOnExit();
            }
        }
        catch (final IOException e) {
            LOG.warn("Unable to write peer information to '{}': {}", filePath::toAbsolutePath, e::getMessage);
        }
    }

    private static Path discoveryPath(final HandlerContext ctx) {
        return ctx.config().getRemoteLocalHostDiscoveryPath().resolve(String.valueOf(ctx.config().getNetworkId()));
    }
}
