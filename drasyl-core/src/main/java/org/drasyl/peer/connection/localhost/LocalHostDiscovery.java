/*
 * Copyright (c) 2020.
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
package org.drasyl.peer.connection.localhost;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNodeComponent;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.RelayableMessage;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.SimpleOutboundHandler;
import org.drasyl.util.DrasylScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.peer.connection.pipeline.LoopbackMessageSinkHandler.LOOPBACK_MESSAGE_SINK_HANDLER;
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
 * This discovery mechanism does not itself establish connections to other peers. Only {@link
 * PeerInformation} are discovered and passed to the {@link PeersManager}. These information can
 * then be used by the {@link org.drasyl.peer.connection.direct.DirectConnectionsManager} to
 * establish connections.
 * <p>
 * Inspired by: https://github.com/actoron/jadex/blob/10e464b230d7695dfd9bf2b36f736f93d69ee314/platform/base/src/main/java/jadex/platform/service/awareness/LocalHostAwarenessAgent.java
 */
@SuppressWarnings("java:S1192")
public class LocalHostDiscovery implements DrasylNodeComponent {
    private static final Logger LOG = LoggerFactory.getLogger(LocalHostDiscovery.class);
    static final String LOCAL_HOST_DISCOVERY_COMMUNICATION_OCCURRED = "LOCAL_HOST_DISCOVERY_COMMUNICATION_OCCURRED";
    private final Path discoveryPath;
    private final Duration leaseTime;
    private final CompressedPublicKey ownPublicKey;
    private final PeersManager peersManager;
    private final Set<Endpoint> endpoints;
    private final AtomicBoolean opened;
    private final AtomicBoolean doScan;
    private final Scheduler scheduler;
    private Disposable watchDisposable;
    private Disposable postDisposable;
    private WatchService watchService; // NOSONAR
    private final Pipeline pipeline;
    private PeerInformation postedPeerInformation;

    public LocalHostDiscovery(final DrasylConfig config,
                              final CompressedPublicKey ownPublicKey,
                              final PeersManager peersManager,
                              final Set<Endpoint> endpoints,
                              final Pipeline pipeline) {
        this(
                config.getLocalHostDiscoveryPath(),
                config.getLocalHostDiscoveryLeaseTime(),
                ownPublicKey,
                peersManager,
                endpoints,
                new AtomicBoolean(),
                new AtomicBoolean(),
                DrasylScheduler.getInstanceLight(),
                null,
                null,
                pipeline);
    }

    @SuppressWarnings({ "java:S107" })
    LocalHostDiscovery(final Path discoveryPath,
                       final Duration leaseTime,
                       final CompressedPublicKey ownPublicKey,
                       final PeersManager peersManager,
                       final Set<Endpoint> endpoints,
                       final AtomicBoolean opened,
                       final AtomicBoolean doScan,
                       final Scheduler scheduler,
                       final Disposable watchDisposable,
                       final Disposable postDisposable,
                       final Pipeline pipeline) {
        this.discoveryPath = discoveryPath;
        this.leaseTime = leaseTime;
        this.ownPublicKey = ownPublicKey;
        this.peersManager = peersManager;
        this.endpoints = endpoints;
        this.opened = opened;
        this.doScan = doScan;
        this.scheduler = scheduler;
        this.watchDisposable = watchDisposable;
        this.postDisposable = postDisposable;
        this.pipeline = pipeline;
    }

    @Override
    @SuppressWarnings({ "java:S3776" })
    public void open() {
        if (opened.compareAndSet(false, true)) {
            LOG.debug("Start Local Host Discovery...");
            final File directory = discoveryPath.toFile();
            if (!directory.exists() && !directory.mkdir()) {
                LOG.warn("Discovery directory '{}' could not be created.", discoveryPath.toAbsolutePath());
            }
            else if (!(directory.isDirectory() && directory.canRead() && directory.canWrite())) {
                LOG.warn("Discovery directory '{}' not accessible.", discoveryPath.toAbsolutePath());
            }
            else {
                tryWatchDirectory();
                scan();
                keepOwnInformationUpToDate();
                pipeline.addAfter(LOOPBACK_MESSAGE_SINK_HANDLER, LOCAL_HOST_DISCOVERY_COMMUNICATION_OCCURRED, new SimpleOutboundHandler<RelayableMessage, CompressedPublicKey>() {
                    @Override
                    protected void matchedWrite(final HandlerContext ctx,
                                                final CompressedPublicKey recipient,
                                                final RelayableMessage msg,
                                                final CompletableFuture<Void> future) {
                        // A scan only happens if a change in the directory was monitored or the time of last poll is too old.
                        if (doScan.compareAndSet(true, false)) {
                            scheduler.scheduleDirect(LocalHostDiscovery.this::scan);
                        }
                        ctx.write(recipient, msg, future);
                    }
                });
            }
            LOG.debug("Local Host Discovery started.");
        }
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
    private void keepOwnInformationUpToDate() {
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
            postInformation();
        }, 0, refreshInterval.toMillis(), MILLISECONDS);
    }

    /**
     * Scans in {@link #discoveryPath} for peer information of other drasyl nodes.
     */
    void scan() {
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
                        final PeerInformation peerInformation = JACKSON_READER.readValue(file, PeerInformation.class);
                        LOG.trace("Information for peer {} discovered by file '{}'", publicKey, fileName);
                        peersManager.setPeerInformation(publicKey, peerInformation);
                    }
                }
                catch (final CryptoException | IOException e) {
                    LOG.warn("Unable to read peer information from '{}': ", file.getAbsolutePath(), e);
                }
            }
        }
    }

    /**
     * Posts own {@link PeerInformation} to {@link #discoveryPath}.
     */
    private void postInformation() {
        final PeerInformation peerInformation = PeerInformation.of(endpoints);

        final Path filePath = discoveryPath.resolve(ownPublicKey.toString() + ".json");
        LOG.trace("Post own Peer Information to {}", filePath);
        final File file = filePath.toFile();
        try {
            // (re)write file on information change. otherwise just touch
            if (!(peerInformation.equals(postedPeerInformation) && file.setLastModified(System.currentTimeMillis()))) {
                JACKSON_WRITER.writeValue(file, peerInformation);
                file.deleteOnExit();
            }

            postedPeerInformation = peerInformation;
        }
        catch (final IOException e) {
            LOG.warn("Unable to write peer information to '{}': {}", filePath.toAbsolutePath(), e.getMessage());
        }
    }

    @Override
    public void close() {
        if (opened.compareAndSet(true, false)) {
            LOG.debug("Stop Local Host Discovery...");

            // remove handler that has been added in {@link #open()}
            pipeline.remove(LOCAL_HOST_DISCOVERY_COMMUNICATION_OCCURRED);

            if (watchDisposable != null) {
                watchDisposable.dispose();
            }
            if (postDisposable != null) {
                postDisposable.dispose();
            }
            LOG.debug("Local Host Discovery stopped.");
        }
    }
}