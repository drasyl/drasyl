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
package org.drasyl.localhost;

import com.fasterxml.jackson.core.type.TypeReference;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.util.NetworkUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.drasyl.util.scheduler.DrasylSchedulerUtil;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;

/**
 * Uses the file system to discover other drasyl nodes running on the local computer.
 * <p>
 * To do this, all nodes regularly write their current endpoints to the file system. At the same
 * time the file system is monitored to detect new nodes. If the file system does not support
 * monitoring, a fallback to polling is used.
 * <p>
 * The discovery directory is scanned whenever communication with another peer occur.
 * <p>
 * This discovery mechanism does not itself establish connections to other peers. Only ip addresses
 * and ports are discovered. These information can then be used to establish direct peer
 * connections.
 * <p>
 * Inspired by: https://github.com/actoron/jadex/blob/10e464b230d7695dfd9bf2b36f736f93d69ee314/platform/base/src/main/java/jadex/platform/service/awareness/LocalHostAwarenessAgent.java
 */
@SuppressWarnings("java:S1192")
public class LocalHostDiscovery extends SimpleOutboundHandler<Object, Address> {
    private static final Logger LOG = LoggerFactory.getLogger(LocalHostDiscovery.class);
    public static final String LOCAL_HOST_DISCOVERY = "LOCAL_HOST_DISCOVERY";
    private final Path discoveryPath;
    private final Duration leaseTime;
    private final CompressedPublicKey ownPublicKey;
    private final AtomicBoolean doScan;
    private final DrasylScheduler scheduler;
    private Disposable watchDisposable;
    private Disposable postDisposable;
    private WatchService watchService; // NOSONAR

    public LocalHostDiscovery(final DrasylConfig config,
                              final CompressedPublicKey ownPublicKey) {
        this(
                config.getLocalHostDiscoveryPath().resolve(String.valueOf(config.getNetworkId())),
                config.getLocalHostDiscoveryLeaseTime(),
                ownPublicKey,
                new AtomicBoolean(),
                DrasylSchedulerUtil.getInstanceLight(),
                null,
                null
        );
    }

    @SuppressWarnings({ "java:S107" })
    LocalHostDiscovery(final Path discoveryPath,
                       final Duration leaseTime,
                       final CompressedPublicKey ownPublicKey,
                       final AtomicBoolean doScan,
                       final DrasylScheduler scheduler,
                       final Disposable watchDisposable,
                       final Disposable postDisposable) {
        this.discoveryPath = discoveryPath;
        this.leaseTime = leaseTime;
        this.ownPublicKey = ownPublicKey;
        this.doScan = doScan;
        this.scheduler = scheduler;
        this.watchDisposable = watchDisposable;
        this.postDisposable = postDisposable;
    }

    @Override
    public void eventTriggered(final HandlerContext ctx,
                               final Event event,
                               final CompletableFuture<Void> future) {
        if (event instanceof NodeUpEvent) {
            startDiscovery(ctx, ((NodeUpEvent) event).getNode().getPort());
        }
        else if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            stopDiscovery();
        }

        // passthrough event
        ctx.fireEventTriggered(event, future);
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final Address recipient,
                                final Object msg,
                                final CompletableFuture<Void> future) {
        // A scan only happens if a change in the directory was monitored or the time of last poll is too old.
        if (doScan.compareAndSet(true, false)) {
            scheduler.scheduleDirect(() -> LocalHostDiscovery.this.scan(ctx));
        }
        ctx.write(recipient, msg, future);
    }

    private synchronized void startDiscovery(final HandlerContext ctx, final int port) {
        LOG.debug("Start Local Host Discovery...");
        final File directory = discoveryPath.toFile();
        if (!directory.exists() && !directory.mkdirs()) {
            LOG.warn("Discovery directory '{}' could not be created.", discoveryPath.toAbsolutePath());
        }
        else if (!(directory.isDirectory() && directory.canRead() && directory.canWrite())) {
            LOG.warn("Discovery directory '{}' not accessible.", discoveryPath.toAbsolutePath());
        }
        else {
            tryWatchDirectory();
            scan(ctx);
            keepOwnInformationUpToDate(ctx, port);
        }
        LOG.debug("Local Host Discovery started.");
    }

    private synchronized void stopDiscovery() {
        LOG.debug("Stop Local Host Discovery...");
        if (watchDisposable != null) {
            watchDisposable.dispose();
        }
        if (postDisposable != null) {
            postDisposable.dispose();
        }
        LOG.debug("Local Host Discovery stopped.");
    }

    /**
     * Tries to monitor {@link #discoveryPath} so that any changes are automatically reported. If
     * this is not possible, we have to fall back to periodical polling.
     */
    private void tryWatchDirectory() {
        try {
            final File directory = discoveryPath.toFile();
            final FileSystem fileSystem = discoveryPath.getFileSystem();
            watchService = fileSystem.newWatchService();
            discoveryPath.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
            LOG.debug("Watch service for directory '{}' registered", directory);
            watchDisposable = scheduler.schedulePeriodicallyDirect(() -> {
                if (watchService.poll() != null) {
                    // directory has been changed
                    doScan.set(true);
                }
            }, 0, 5, SECONDS);
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
    private void keepOwnInformationUpToDate(final HandlerContext ctx, final int port) {
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
        if (leaseTime.toSeconds() > 5) {
            refreshInterval = leaseTime.minus(ofSeconds(5));
        }
        else {
            refreshInterval = ofSeconds(1);
        }
        postDisposable = scheduler.schedulePeriodicallyDirect(() -> {
            // only scan in polling mode when watchService does not work
            if (watchService == null) {
                doScan.set(true);
            }
            postInformation(socketAddresses);
        }, 0, refreshInterval.toMillis(), MILLISECONDS);
    }

    /**
     * Scans in {@link #discoveryPath} for ports of other drasyl nodes.
     *
     * @param ctx handler's context
     */
    void scan(final HandlerContext ctx) {
        LOG.debug("Scan directory {} for new peers.", discoveryPath);
        final String ownPublicKeyString = this.ownPublicKey.toString();
        final long maxAge = System.currentTimeMillis() - leaseTime.toMillis();
        final File[] files = discoveryPath.toFile().listFiles();
        if (files != null) {
            for (final File file : files) {
                try {
                    final String fileName = file.getName();
                    if (file.lastModified() >= maxAge && fileName.length() == 71 && fileName.endsWith(".json") && !fileName.startsWith(ownPublicKeyString)) {
                        final CompressedPublicKey publicKey = CompressedPublicKey.of(fileName.replace(".json", ""));
                        final TypeReference<Set<InetSocketAddress>> typeRef = new TypeReference<>() {
                        };
                        final Set<InetSocketAddress> addresses = JACKSON_READER.forType(typeRef).readValue(file);
                        LOG.trace("Addresses '{}' for peer '{}' discovered by file '{}'", addresses, publicKey, fileName);
                    }
                }
                catch (final CryptoException | IOException e) {
                    LOG.warn("Unable to read peer information from '{}': ", file.getAbsolutePath(), e);
                }
            }
        }
    }

    /**
     * Posts own port to {@link #discoveryPath}.
     */
    private void postInformation(final Set<InetSocketAddress> addresses) {
        final Path filePath = discoveryPath.resolve(ownPublicKey.toString() + ".json");
        LOG.trace("Post own Peer Information to {}", filePath);
        final File file = filePath.toFile();
        try {
            if (!file.setLastModified(System.currentTimeMillis())) {
                JACKSON_WRITER.writeValue(file, addresses);
                file.deleteOnExit();
            }
        }
        catch (final IOException e) {
            LOG.warn("Unable to write peer information to '{}': {}", filePath::toAbsolutePath, e::getMessage);
        }
    }
}