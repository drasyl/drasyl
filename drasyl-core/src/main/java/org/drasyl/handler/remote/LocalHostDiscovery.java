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
package org.drasyl.handler.remote;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.handler.remote.UdpServer.UdpServerBound;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.SetUtil;
import org.drasyl.util.ThrowingBiConsumer;
import org.drasyl.util.ThrowingFunction;
import org.drasyl.util.internal.UnstableApi;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.RandomUtil.randomLong;

/**
 * Uses the file system to discover other drasyl nodes running on the local computer.
 * <p>
 * To do this, all nodes regularly write their {@link UdpServer} address(es) to the file system. At
 * the same time the file system is monitored to detect other nodes. If the file system does not
 * support monitoring ({@link WatchService}), a fallback to polling is used.
 * <p>
 * Inspired by: <a href="https://github.com/actoron/jadex/blob/10e464b230d7695dfd9bf2b36f736f93d69ee314/platform/base/src/main/java/jadex/platform/service/awareness/LocalHostAwarenessAgent.java">Jadex</a>
 */
@UnstableApi
@SuppressWarnings("java:S1192")
public class LocalHostDiscovery extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(LocalHostDiscovery.class);
    static final Class<?> PATH_ID = LocalHostDiscovery.class;
    static final short PATH_PRIORITY = 80;
    public static final Duration REFRESH_INTERVAL_SAFETY_MARGIN = ofSeconds(5);
    public static final Duration WATCH_SERVICE_POLL_INTERVAL = ofSeconds(5);
    public static final String FILE_SUFFIX = ".txt";
    private final ThrowingFunction<File, Set<InetSocketAddress>, IOException> fileReader;
    private final ThrowingBiConsumer<File, Set<InetSocketAddress>, IOException> fileWriter;
    private final boolean watchEnabled;
    private final Duration leaseTime;
    private final Path path;
    private Future<?> watchDisposable;
    private Future<?> postDisposable;
    private WatchService watchService; // NOSONAR

    @SuppressWarnings({ "java:S107" })
    LocalHostDiscovery(final ThrowingFunction<File, Set<InetSocketAddress>, IOException> fileReader,
                       final ThrowingBiConsumer<File, Set<InetSocketAddress>, IOException> fileWriter,
                       final boolean watchEnabled,
                       final Duration leaseTime,
                       final Path path,
                       final Future<?> watchDisposable,
                       final Future<?> postDisposable) {
        this.fileReader = requireNonNull(fileReader);
        this.fileWriter = requireNonNull(fileWriter);
        this.watchEnabled = watchEnabled;
        this.leaseTime = leaseTime;
        this.path = path;
        this.watchDisposable = watchDisposable;
        this.postDisposable = postDisposable;
    }

    public LocalHostDiscovery(final boolean watchEnabled,
                              final Duration leaseTime,
                              final Path path) {
        this(
                file -> LocalHostPeerInformation.of(file).addresses(),
                (file, addresses) -> LocalHostPeerInformation.of(addresses).writeTo(file),
                watchEnabled,
                leaseTime,
                path,
                null,
                null
        );
    }

    private void startDiscovery(final ChannelHandlerContext ctx,
                                final InetSocketAddress bindAddress) {
        LOG.debug("Start Local Host Discovery...");
        final Path discoveryPath = discoveryPath(ctx);
        final File directory = discoveryPath.toFile();

        if (!directory.mkdirs() && !directory.exists()) {
            LOG.warn("Discovery directory `{}` could not be created.", discoveryPath::toAbsolutePath);
        }
        else if (!(directory.isDirectory() && directory.canRead() && directory.canWrite())) {
            LOG.warn("Discovery directory `{}` not accessible.", discoveryPath::toAbsolutePath);
        }
        else {
            if (watchEnabled) {
                tryWatchDirectory(ctx, discoveryPath);
            }
            ctx.executor().execute(() -> scan(ctx));
            keepOwnInformationUpToDate(ctx, discoveryPath.resolve(ctx.channel().localAddress().toString() + FILE_SUFFIX), bindAddress);
        }
        LOG.debug("Local Host Discovery started.");
    }

    private void stopDiscovery(final ChannelHandlerContext ctx) {
        LOG.debug("Stop Local Host Discovery...");

        if (watchDisposable != null) {
            watchDisposable.cancel(false);
        }
        if (postDisposable != null) {
            postDisposable.cancel(false);
        }

        if (watchService != null) {
            try {
                watchService.close();
            }
            catch (final IOException e) {
                LOG.warn("Unable to close the watch service:", e);
            }
        }

        final Path filePath = discoveryPath(ctx).resolve(ctx.channel().localAddress().toString() + FILE_SUFFIX);
        try {
            Files.deleteIfExists(filePath);
        }
        catch (final IOException e) {
            LOG.debug("Unable to delete `{}`", filePath, e);
        }

        config(ctx).getPeersManager().removeClientPaths(ctx, PATH_ID);

        LOG.debug("Local Host Discovery stopped.");
    }

    /**
     * Tries to monitor {@code discoveryPath} so that any changes are automatically reported. If
     * this is not possible, we have to fall back to periodical polling.
     */
    private void tryWatchDirectory(final ChannelHandlerContext ctx, final Path discoveryPath) {
        try {
            final File directory = discoveryPath.toFile();
            final FileSystem fileSystem = discoveryPath.getFileSystem();
            watchService = fileSystem.newWatchService();
            discoveryPath.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
            LOG.debug("Watch service for directory `{}` registered", directory);
            final long pollInterval = WATCH_SERVICE_POLL_INTERVAL.toMillis();
            // directory has been changed
            watchDisposable = ctx.executor().scheduleWithFixedDelay(() -> {
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
    private void keepOwnInformationUpToDate(final ChannelHandlerContext ctx,
                                            final Path filePath,
                                            final InetSocketAddress bindAddress) {
        // get own address(es)
        final Set<InetAddress> addresses;
        if (bindAddress.getAddress().isAnyLocalAddress()) {
            // use all available addresses
            addresses = NetworkUtil.getAddresses();
        }
        else {
            // use given host
            addresses = Set.of(bindAddress.getAddress());
        }
        final Set<InetSocketAddress> socketAddresses = addresses.stream().map(a -> new InetSocketAddress(a, bindAddress.getPort())).collect(Collectors.toSet());

        final Duration refreshInterval;
        if (leaseTime.compareTo(REFRESH_INTERVAL_SAFETY_MARGIN) > 0) {
            refreshInterval = leaseTime.minus(REFRESH_INTERVAL_SAFETY_MARGIN);
        }
        else {
            refreshInterval = ofSeconds(1);
        }
        // only scan in polling mode when watchService does not work
        postDisposable = ctx.executor().scheduleWithFixedDelay(() -> {
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
    synchronized void scan(final ChannelHandlerContext ctx) {
        final Path discoveryPath = discoveryPath(ctx);
        LOG.debug("Scan directory {} for new peers.", discoveryPath);
        final String ownPublicKeyString = ctx.channel().localAddress().toString();
        final long maxAge = System.currentTimeMillis() - leaseTime.toMillis();
        final File[] files = discoveryPath.toFile().listFiles();
        if (files != null) {
            final Map<IdentityPublicKey, InetSocketAddress> newRoutes = new HashMap<>();
            for (final File file : files) {
                try {
                    final String fileName = file.getName();
                    if (file.lastModified() >= maxAge && fileName.length() == IdentityPublicKey.KEY_LENGTH_AS_STRING + FILE_SUFFIX.length() && fileName.endsWith(FILE_SUFFIX) && !fileName.startsWith(ownPublicKeyString)) {
                        final IdentityPublicKey publicKey = IdentityPublicKey.of(fileName.replace(FILE_SUFFIX, ""));
                        final Set<InetSocketAddress> addresses = fileReader.apply(file);
                        if (!addresses.isEmpty()) {
                            LOG.trace("Addresses `{}` for peer `{}` discovered by file `{}`", addresses, publicKey, fileName);
                            final InetSocketAddress firstAddress = SetUtil.firstElement(addresses);
                            newRoutes.put(publicKey, firstAddress);
                        }
                    }
                }
                catch (final IllegalArgumentException | IOException e) {
                    LOG.warn("Unable to read peer information from `{}`: ", file.getAbsolutePath(), e);
                }
            }

            updateRoutes(ctx, newRoutes);
        }
    }

    private void updateRoutes(final ChannelHandlerContext ctx,
                              final Map<IdentityPublicKey, InetSocketAddress> newRoutes) {
        // remove outdated routes
        final PeersManager peersManager = config(ctx).getPeersManager();
        Set<DrasylAddress> peers = peersManager.getPeers(PATH_ID);
        for (final DrasylAddress publicKey : peers) {
            if (!newRoutes.containsKey(publicKey)) {
                LOG.trace("Addresses for peer `{}` are outdated. Remove peer from routing table.", publicKey);
                peersManager.removeClientPath(ctx, publicKey, PATH_ID);
            }
        }

        // add new routes
        newRoutes.forEach(((publicKey, address) -> {
            LOG.trace("Add new address `{}` for peer `{}`.", address, publicKey);
            peersManager.addClientPath(ctx, publicKey, PATH_ID, address, PATH_PRIORITY);
        }));
    }

    /**
     * Posts own port to {@code filePath}.
     */
    @SuppressWarnings("java:S2308")
    private void postInformation(final Path filePath,
                                 final Set<InetSocketAddress> addresses) {
        LOG.trace("Post own addresses `{}` to file `{}`", addresses, filePath);
        final File file = filePath.toFile();
        try {
            if (!file.setLastModified(System.currentTimeMillis())) {
                fileWriter.accept(file, addresses);
                file.deleteOnExit();
            }
        }
        catch (final IOException e) {
            LOG.warn("Unable to write peer information to `{}`: {}", filePath::toAbsolutePath, e::getMessage);
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        stopDiscovery(ctx);

        ctx.fireChannelInactive();
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (evt instanceof UdpServerBound) {
            startDiscovery(ctx, ((UdpServerBound) evt).getBindAddress());
        }

        ctx.fireUserEventTriggered(evt);
    }

    private Path discoveryPath(final ChannelHandlerContext ctx) {
        return path.resolve(String.valueOf(config(ctx).getNetworkId()));
    }

    private static DrasylServerChannelConfig config(final ChannelHandlerContext ctx) {
        return (DrasylServerChannelConfig) ctx.channel().config();
    }
}
