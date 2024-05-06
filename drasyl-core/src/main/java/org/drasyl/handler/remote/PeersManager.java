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
package org.drasyl.handler.remote;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.PathEvent;
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.InetSocketAddressUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.remote.PeersManager.PeerRole.CHILDREN;
import static org.drasyl.handler.remote.PeersManager.PeerRole.SUPER_PEER;
import static org.drasyl.util.InetSocketAddressUtil.socketAddressToString;

public class PeersManager {
    private static final Logger LOG = LoggerFactory.getLogger(PeersManager.class);
    private static final int NO_RTT = -1;
    private final LongSupplier currentTime;
    private final ReadWriteLock lock;
    private final Map<DrasylAddress, Peer> peers;
    DrasylAddress defaultPeerKey;

    PeersManager(final LongSupplier currentTime,
                 final ReadWriteLock lock,
                 final Map<DrasylAddress, Peer> peers,
                 final DrasylAddress defaultPeerKey) {
        this.peers = requireNonNull(peers);
        this.lock = requireNonNull(lock);
        this.currentTime = requireNonNull(currentTime);
        this.defaultPeerKey = defaultPeerKey;
    }

    public PeersManager() {
        this(System::currentTimeMillis, new ReentrantReadWriteLock(true), new ConcurrentHashMap<>(), null);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();

        // header
        builder.append(String.format("%-64s  %-5s  %4s  %4s  %4s  %4s  %-45s%n", "Peer", "Role", "RTT", "LastTX", "LastRX", "Prio", "Endpoint"));

        // peers
        for (final Entry<DrasylAddress, Peer> entry : peers.entrySet()) {
            final DrasylAddress peerKey = entry.getKey();
            final Peer peer = entry.getValue();

            // peer
            builder.append(String.format(
                    "%-64s  %-5s",
                    peerKey,
                    peer.role() + (peerKey.equals(defaultPeerKey) ? "*" : "")
            ));

            // paths
            PeerPath current = peer.bestPath;
            while (current != null) {
                if (current == peer.bestPath) {
                    builder.append(String.format(
                            "  %4s  %6d  %6d  %4d  %-45s%n",
                            current.rtt,
                            current.timeSincLastSent(),
                            current.timeSincLastReceived(),
                            current.id.priority(),
                            current.endpoint != null ? socketAddressToString(current.endpoint) : null
                    ));
                }
                else {
                    builder.append(String.format(
                            "%-70s  %4s  %6d  %6d  %4d  %-45s%n",
                            "",
                            current.rtt,
                            current.timeSincLastSent(),
                            current.timeSincLastReceived(),
                            current.id.priority(),
                            current.endpoint != null ? socketAddressToString(current.endpoint) : null
                    ));
                }
                current = current.next;
            }
        }

        return builder.toString();
    }

    /*
     * Peers
     */

    public boolean hasApplicationTraffic(final ChannelHandlerContext ctx,
                                         final DrasylAddress peerKey) {
        lock.readLock().lock();
        try {
            final Peer peer = peers.get(peerKey);
            if (peer != null) {
                return peer.hasApplicationTraffic(ctx);
            }
            return false;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void applicationMessageSent(final DrasylAddress peerKey) {
        lock.readLock().lock();
        try {
            final Peer peer = peers.get(peerKey);
            if (peer != null) {
                peer.applicationMessageSent();
            }
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void applicationMessageReceived(final DrasylAddress peerKey) {
        lock.readLock().lock();
        try {
            final Peer peer = peers.get(peerKey);
            if (peer != null) {
                peer.applicationMessageReceived();
            }
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public InetSocketAddress resolve(final DrasylAddress peerKey) {
        lock.readLock().lock();
        try {
            final Peer peer = peers.get(peerKey);
            if (peer != null) {
                final InetSocketAddress endpoint = peer.resolve();
                if (endpoint != null) {
                    return endpoint;
                }
                return resolveDefault();
            }
            return resolveDefault();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    private InetSocketAddress resolveDefault() {
        if (defaultPeerKey != null) {
            final Peer defaultPeer = peers.get(defaultPeerKey);
            return defaultPeer.resolve();
        }
        return null;
    }

    public boolean hasPath(final PathId id) {
        lock.readLock().lock();
        try {
            for (final Peer peer : peers.values()) {
                if (peer.hasPath(id)) {
                    return true;
                }
            }
            return false;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public boolean hasPath(final DrasylAddress peerKey) {
        lock.readLock().lock();
        try {
            Peer peer = peers.get(peerKey);
            if (peer != null) {
                return peer.hasPath();
            }
            else {
                return false;
            }
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public boolean hasPath(final DrasylAddress peerKey, final PathId id) {
        lock.readLock().lock();
        try {
            Peer peer = peers.get(peerKey);
            if (peer != null) {
                return peer.hasPath(id);
            }
            else {
                return false;
            }
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /*
     * Default peer
     */

    public boolean hasDefaultPeer() {
        lock.readLock().lock();
        try {
            return defaultPeerKey != null;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @throws NullPointerException if {@code newDefaultPeerKey} is {@code null}
     */
    public DrasylAddress setDefaultPeer(final DrasylAddress newDefaultPeerKey) {
        requireNonNull(newDefaultPeerKey);

        return conditionalWriteLock(
                () -> !newDefaultPeerKey.equals(defaultPeerKey),
                () -> {
                    final DrasylAddress previousDefaultPeer = defaultPeerKey;
                    defaultPeerKey = newDefaultPeerKey;
                    return previousDefaultPeer;
                },
                () -> defaultPeerKey
        );
    }

    public DrasylAddress setDefaultPeerIfUnset(final DrasylAddress newDefaultPeerKey) {
        requireNonNull(newDefaultPeerKey);

        return conditionalWriteLock(
                () -> defaultPeerKey == null,
                () -> {
                    final DrasylAddress previousDefaultPeer = defaultPeerKey;
                    defaultPeerKey = newDefaultPeerKey;
                    return previousDefaultPeer;
                },
                () -> defaultPeerKey
        );
    }

    public DrasylAddress unsetDefaultPeer() {
        return conditionalWriteLock(
                () -> defaultPeerKey != null,
                () -> {
                    final DrasylAddress previousDefaultPeer = defaultPeerKey;
                    defaultPeerKey = null;
                    return previousDefaultPeer;
                },
                () -> defaultPeerKey
        );
    }

    /*
     * Paths
     */
    private boolean addPath(final ChannelHandlerContext ctx,
                            final PeerRole role,
                            final DrasylAddress peerKey,
                            final PathId id,
                            final InetSocketAddress endpoint,
                            final int rtt) {
        return conditionalWriteLock(
                () -> !(peers.containsKey(peerKey) && peers.get(peerKey).hasPath(id, endpoint)),
                () -> {
                    final Peer peer = peers.computeIfAbsent(peerKey, key -> new Peer(currentTime, key, role));
                    if (peer.role() != role) {
                        throw new IllegalStateException();
                    }

                    if (peer.addPath(id, endpoint)) {
                        if (peer.pathCount() == 1) {
                            ctx.fireUserEventTriggered(peer.addPeerEvent(endpoint, id, rtt));
                        }
                        else {
                            ctx.fireUserEventTriggered(peer.addPathEvent(endpoint, id, rtt));
                        }
                        return true;
                    }
                    else if (rtt >= 0) {
                        ctx.fireUserEventTriggered(PathRttEvent.of(peerKey, endpoint, id, rtt));
                    }
                    return false;
                },
                () -> {
                    if (rtt >= 0) {
                        ctx.fireUserEventTriggered(PathRttEvent.of(peerKey, endpoint, id, rtt));
                    }
                    return false;
                }
        );
    }

    private boolean removePath(final ChannelHandlerContext ctx,
                               final PeerRole role,
                               final DrasylAddress peerKey,
                               final PathId id) {
        return conditionalWriteLock(
                () -> peers.containsKey(peerKey) && peers.get(peerKey).hasPath(id),
                () -> {
                    final Peer peer = peers.get(peerKey);
                    if (peer != null) {
                        if (peer.role() != role) {
                            throw new IllegalStateException();
                        }

                        if (peer.removePath(id)) {
                            if (peer.hasPath()) {
                                ctx.fireUserEventTriggered(peer.removePathEvent(id));
                            }
                            else {
                                peers.remove(peerKey);
                                ctx.fireUserEventTriggered(peer.removePeerEvent(id));
                            }
                            return true;
                        }
                        else {
                            return false;
                        }
                    }
                    else {
                        return false;
                    }
                },
                () -> false
        );
    }

    /*
     * Super peer paths
     */

    public boolean addSuperPeerPath(final ChannelHandlerContext ctx,
                                    final DrasylAddress peerKey,
                                    final PathId id,
                                    final InetSocketAddress endpoint,
                                    final int rtt) {
        return addPath(ctx, SUPER_PEER, peerKey, id, endpoint, rtt);
    }

    public boolean removeSuperPeerPath(final ChannelHandlerContext ctx,
                                       final DrasylAddress peerKey,
                                       final PathId id) {
        return removePath(ctx, SUPER_PEER, peerKey, id);
    }

    @SuppressWarnings("unused")
    public void removeSuperPeerPaths(final ChannelHandlerContext ctx,
                                     final PathId id) {
        final Set<DrasylAddress> peerKeys = Set.copyOf(peers.keySet());
        peerKeys.forEach(peerKey -> removeSuperPeerPath(ctx, peerKey, id));
    }

    /*
     * Children paths
     */

    public boolean addChildrenPath(final ChannelHandlerContext ctx,
                                   final DrasylAddress peerKey,
                                   final PathId id,
                                   final InetSocketAddress endpoint,
                                   final int rtt) {
        return addPath(ctx, CHILDREN, peerKey, id, endpoint, rtt);
    }

    public boolean addChildrenPath(final ChannelHandlerContext ctx,
                                   final DrasylAddress peerKey,
                                   final PathId id,
                                   final InetSocketAddress endpoint) {
        return addChildrenPath(ctx, peerKey, id, endpoint, NO_RTT);
    }

    public boolean tryAddChildrenPath(final ChannelHandlerContext ctx,
                                      final DrasylAddress peerKey,
                                      final PathId id,
                                      final InetSocketAddress endpoint) {
        try {
            return addChildrenPath(ctx, peerKey, id, endpoint, NO_RTT);
        }
        catch (final IllegalStateException e) {
            // ignore
            return false;
        }
    }

    public boolean removeChildrenPath(final ChannelHandlerContext ctx,
                                      final DrasylAddress peerKey,
                                      final PathId id) {
        return removePath(ctx, CHILDREN, peerKey, id);
    }

    public void removeChildrenPaths(final ChannelHandlerContext ctx,
                                    final PathId id) {
        final Set<DrasylAddress> peerKeys = Set.copyOf(peers.keySet());
        peerKeys.forEach(peerKey -> removeChildrenPath(ctx, peerKey, id));
    }

    /*
     * Paths
     */

    public Set<DrasylAddress> getPeers(final PathId id) {
        lock.readLock().lock();
        try {
            final Set<DrasylAddress> peerKeys = new HashSet<>();
            for (final Entry<DrasylAddress, Peer> entry : peers.entrySet()) {
                final DrasylAddress peerKey = entry.getKey();
                final Peer peer = entry.getValue();
                if (peer.paths().containsKey(id)) {
                    peerKeys.add(peerKey);
                }
            }
            return peerKeys;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public boolean isStale(final ChannelHandlerContext ctx,
                           final DrasylAddress peerKey,
                           final PathId id) {
        lock.readLock().lock();
        try {
            final Peer peer = peers.get(peerKey);
            if (peer != null) {
                return peer.isStale(ctx, id);
            }
            return false;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public boolean isReachable(final ChannelHandlerContext ctx,
                               final DrasylAddress peerKey,
                               final PathId id) {
        lock.readLock().lock();
        try {
            final Peer peer = peers.get(peerKey);
            if (peer != null) {
                return peer.isReachable(ctx, id);
            }
            return false;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void helloMessageReceived(final DrasylAddress peerKey,
                                     final PathId id) {
        lock.readLock().lock();
        try {
            final Peer peer = peers.get(peerKey);
            if (peer != null) {
                peer.helloMessageReceived(id);
            }
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void helloMessageSent(final DrasylAddress peerKey,
                                 final PathId id) {
        lock.readLock().lock();
        try {
            final Peer peer = peers.get(peerKey);
            if (peer != null) {
                peer.helloMessageSent(id);
            }
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void acknowledgementMessageReceived(final DrasylAddress peerKey,
                                               final PathId id,
                                               final int rtt) {
        lock.readLock().lock();
        try {
            final Peer peer = peers.get(peerKey);
            if (peer != null) {
                peer.acknowledgementMessageReceived(id, rtt);
            }
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void acknowledgementMessageReceived(final DrasylAddress peerKey,
                                               final PathId id) {
        acknowledgementMessageReceived(peerKey, id, NO_RTT);
    }

    public int rtt(DrasylAddress peerKey, final PathId id) {
        lock.readLock().lock();
        try {
            final Peer peer = peers.get(peerKey);
            if (peer != null) {
                return peer.rtt(id);
            }
            return NO_RTT;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public InetSocketAddress resolveInetAddress(DrasylAddress peerKey, final PathId id) {
        lock.readLock().lock();
        try {
            final Peer peer = peers.get(peerKey);
            if (peer != null) {
                return peer.resolveEndpoint(id);
            }
            return null;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * This method reduces the amount of write locks by checking if the write task is actually
     * changing the state. While there might be something false positive write locks, there are
     * never be false negatives. Always acquires read lock, but "upgrade" to a write lock if a state
     * change might happen.
     */
    private <T> T conditionalWriteLock(final BooleanSupplier writeCondition,
                                       final Supplier<T> writeTask,
                                       final Supplier<T> previousValue) {
        lock.readLock().lock();
        try {
            // check if we need to change state
            if (writeCondition.getAsBoolean()) {
                // "upgrade" to write lock
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    // change state
                    try {
                        return writeTask.get();
                    }
                    finally {
                        // downgrade to read lock
                        lock.readLock().lock();
                    }
                }
                finally {
                    // unlock write, still hold read
                    lock.writeLock().unlock();
                }
            }
            else {
                return previousValue.get();
            }
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /*
     * Inner clases
     */

    static class Peer {
        private final LongSupplier currentTime;
        private final DrasylAddress address;
        private final Map<PathId, PeerPath> paths;
        PeerPath bestPath;
        private final PeerRole role;
        private long lastApplicationMessageSentTime;
        private long lastApplicationMessageReceivedTime;

        Peer(final LongSupplier currentTime,
             final DrasylAddress address,
             final Map<PathId, PeerPath> paths,
             final PeerPath bestPath,
             final PeerRole role) {
            this.currentTime = requireNonNull(currentTime);
            this.address = requireNonNull(address);
            this.bestPath = bestPath;
            this.paths = requireNonNull(paths);
            this.role = requireNonNull(role);
        }

        Peer(final LongSupplier currentTime,
             final DrasylAddress address,
             final PeerRole role) {
            this(currentTime, address, new HashMap<>(), null, role);
        }

        Peer(final DrasylAddress address,
             final Map<PathId, PeerPath> paths,
             final PeerPath bestPath,
             final PeerRole role) {
            this(System::currentTimeMillis, address, paths, bestPath, role);
        }

        @Override
        public String toString() {
            return "Peer{" +
                    "address=" + address +
                    ", role=" + role +
                    '}';
        }

        int pathCount() {
            return paths.size();
        }

        boolean hasPath() {
            return bestPath != null;
        }

        boolean hasPath(final PathId id) {
            return paths.containsKey(id);
        }

        boolean hasPath(final PathId id, final InetSocketAddress endpoint) {
            return paths.containsKey(id) && Objects.equals(paths.get(id).endpoint, endpoint);
        }

        boolean hasDifferentPath(final PathId id, final InetSocketAddress endpoint) {
            return paths.containsKey(id) && !Objects.equals(paths.get(id).endpoint, endpoint);
        }

        Map<PathId, PeerPath> paths() {
            return paths;
        }

        InetSocketAddress resolve() {
            if (bestPath != null) {
                return bestPath.endpoint;
            }
            else {
                return null;
            }
        }

        PeerRole role() {
            return role;
        }

        void applicationMessageSent() {
            lastApplicationMessageSentTime = currentTime.getAsLong();
        }

        void applicationMessageReceived() {
            lastApplicationMessageReceivedTime = currentTime.getAsLong();
        }

        boolean hasApplicationTraffic(final ChannelHandlerContext ctx) {
            final long pathIdleTimeMillis = ((DrasylServerChannelConfig) ctx.channel().config()).getPathIdleTime().toMillis();
            return Math.max(lastApplicationMessageSentTime, lastApplicationMessageReceivedTime) >= currentTime.getAsLong() - pathIdleTimeMillis;
        }

        /*
         * Paths
         */

        boolean addPath(final PathId id,
                        final InetSocketAddress endpoint) {
            if (endpoint != null && endpoint.isUnresolved()) {
                throw new UnresolvedAddressException();
            }

            final boolean pathAdded;
            if (hasDifferentPath(id, endpoint)) {
                pathAdded = false;
                removePath(id);
            }
            else {
                pathAdded = true;
            }

            final PeerPath newPath = new PeerPath(id, endpoint);
            paths.put(id, newPath);

            final PeerPath head = bestPath;
            if (head == null || head.id.priority() > newPath.id.priority()) {
                // replace head
                newPath.next = head;
                bestPath = newPath;
            }
            else {
                PeerPath current = head;
                while (current.next != null && current.next.id.priority() <= newPath.id.priority()) {
                    current = current.next;
                }

                newPath.next = current.next;
                current.next = newPath;
            }

            return pathAdded;
        }

        boolean removePath(final PathId id) {
            if (!paths.containsKey(id)) {
                return false;
            }

            PeerPath prev = null;
            PeerPath current = bestPath;
            while (current != null) {
                if (current.id == id) {
                    if (prev != null) {
                        prev.next = current.next;
                    }
                    else if (current.next != null) {
                        bestPath = current.next;
                    }
                    else {
                        bestPath = null;
                    }

                    return true;
                }

                prev = current;
                current = current.next;
            }

            return false;
        }

        boolean isStale(final ChannelHandlerContext ctx,
                        final PathId id) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                return path.isStale(ctx);
            }
            return true;
        }

        boolean isReachable(final ChannelHandlerContext ctx,
                            final PathId id) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                return path.isReachable(ctx);
            }
            return false;
        }

        public void helloMessageSent(final PathId id) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                path.helloMessageSent();
            }
        }

        public void helloMessageReceived(final PathId id) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                path.helloMessageReceived();
            }
        }

        public void acknowledgementMessageReceived(final PathId id, final int rtt) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                path.acknowledgementMessageReceived(rtt);
            }
        }

        public int rtt(final PathId id) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                return path.rtt();
            }
            return NO_RTT;
        }

        public InetSocketAddress resolveEndpoint(final PathId id) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                return path.resolveEndpoint();
            }
            return null;
        }

        public PathEvent addPeerEvent(final InetSocketAddress endpoint,
                                      final PathId id,
                                      final int rtt) {
            if (role == SUPER_PEER) {
                return AddPathAndSuperPeerEvent.of(address, endpoint, id, rtt);
            }
            else {
                return AddPathAndChildrenEvent.of(address, endpoint, id);
            }
        }

        public PathEvent addPathEvent(final InetSocketAddress endpoint,
                                      final PathId id,
                                      final int rtt) {
            return AddPathEvent.of(address, endpoint, id, rtt);
        }

        public PathEvent removePathEvent(final PathId id) {
            return RemovePathEvent.of(address, id);
        }

        public PathEvent removePeerEvent(final PathId id) {
            return RemoveSuperPeerAndPathEvent.of(address, id);
        }
    }

    enum PeerRole {
        SUPER_PEER("S"),
        CHILDREN("C");
        private final String label;

        PeerRole(final String label) {
            this.label = requireNonNull(label);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /*
     * Path
     */

    static class PeerPath {
        private final LongSupplier currentTime;
        private final PathId id;
        private InetSocketAddress endpoint;
        private PeerPath next;
        long firstHelloMessageSentTime;
        long lastHelloMessageSentTime;
        long lastHelloMessageReceivedTime;
        long lastAcknowledgementMessageReceivedTime;
        int rtt;

        @SuppressWarnings("java:S107")
        PeerPath(final LongSupplier currentTime,
                 final PathId id,
                 final InetSocketAddress endpoint,
                 final PeerPath next,
                 final long lastHelloMessageReceivedTime,
                 final long firstHelloMessageSentTime,
                 final long lastAcknowledgementMessageReceivedTime,
                 final int rtt) {
            this.currentTime = requireNonNull(currentTime);
            this.id = requireNonNull(id);
            this.endpoint = endpoint;
            this.next = next;
            this.lastHelloMessageReceivedTime = lastHelloMessageReceivedTime;
            this.firstHelloMessageSentTime = firstHelloMessageSentTime;
            this.lastAcknowledgementMessageReceivedTime = lastAcknowledgementMessageReceivedTime;
            this.rtt = rtt;
        }

        PeerPath(final PathId id,
                 final InetSocketAddress endpoint) {
            this(System::currentTimeMillis, id, endpoint, null, 0, 0, 0, NO_RTT);
        }

        @Override
        public String toString() {
            return "PeerPath{" +
                    "id=" + id +
                    ", endpoint=" + endpoint +
                    ", next=" + next +
                    ", lastHelloMessageReceivedTime=" + lastHelloMessageReceivedTime +
                    ", firstHelloMessageSentTime=" + firstHelloMessageSentTime +
                    ", lastAcknowledgementMessageReceivedTime=" + lastAcknowledgementMessageReceivedTime +
                    ", rtt=" + rtt +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PeerPath path = (PeerPath) o;
            return lastHelloMessageReceivedTime == path.lastHelloMessageReceivedTime && Objects.equals(id, path.id) && Objects.equals(endpoint, path.endpoint) && Objects.equals(next, path.next);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, endpoint, next, lastHelloMessageReceivedTime);
        }

        void helloMessageSent() {
            if (firstHelloMessageSentTime == 0) {
                firstHelloMessageSentTime = currentTime.getAsLong();
            }
            lastHelloMessageSentTime = currentTime.getAsLong();
        }

        void helloMessageReceived() {
            lastHelloMessageReceivedTime = currentTime.getAsLong();
        }

        void acknowledgementMessageReceived(final int rtt) {
            lastAcknowledgementMessageReceivedTime = currentTime.getAsLong();
            this.rtt = rtt;
        }

        int rtt() {
            return rtt;
        }

        boolean isStale(final ChannelHandlerContext ctx) {
            return lastHelloMessageReceivedTime < currentTime.getAsLong() - ((DrasylServerChannelConfig) ctx.channel().config()).getHelloTimeout().toMillis();
        }

        boolean isReachable(final ChannelHandlerContext ctx) {
            return lastAcknowledgementMessageReceivedTime >= currentTime.getAsLong() - ((DrasylServerChannelConfig) ctx.channel().config()).getHelloTimeout().toMillis();
        }

        /**
         * Triggers a new resolve of the endpoint.
         */
        InetSocketAddress resolveEndpoint() {
            try {
                endpoint = InetSocketAddressUtil.resolve(endpoint);
            }
            catch (final UnknownHostException e) {
                // keep existing address
                LOG.warn("Unable to resolve endpoint `{}`", endpoint, e);
            }
            return endpoint;
        }

        public int timeSincLastSent() {
            final long lastSentTime = lastHelloMessageSentTime;
            if (lastSentTime == 0) {
                return -1;
            }
            return (int) (currentTime.getAsLong() - lastSentTime);
        }

        public int timeSincLastReceived() {
            final long lastReceivedTime = Math.max(lastHelloMessageReceivedTime, lastAcknowledgementMessageReceivedTime);
            if (lastReceivedTime == 0) {
                return -1;
            }
            return (int) (currentTime.getAsLong() - lastReceivedTime);
        }
    }

    public abstract static class PathId {
        public abstract short priority();
    }
}
