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
import java.util.function.LongSupplier;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.remote.PeersManager.PeerRole.CLIENT;
import static org.drasyl.handler.remote.PeersManager.PeerRole.SUPER;
import static org.drasyl.handler.remote.PeersManager.PeerRole.UNDEFINED;

public class PeersManager {
    private static final Logger LOG = LoggerFactory.getLogger(PeersManager.class);
    private static final int NO_RTT = -1;
    private final LongSupplier currentTime;
    private final Map<DrasylAddress, Peer> peers;
    DrasylAddress defaultPeerKey;

    PeersManager(final LongSupplier currentTime,
                 final Map<DrasylAddress, Peer> peers,
                 final DrasylAddress defaultPeerKey) {
        this.peers = requireNonNull(peers);
        this.currentTime = requireNonNull(currentTime);
        this.defaultPeerKey = defaultPeerKey;
    }

    PeersManager(final Map<DrasylAddress, Peer> peers,
                 final DrasylAddress defaultPeerKey) {
        this(System::currentTimeMillis, peers, defaultPeerKey);
    }

    PeersManager(final Map<DrasylAddress, Peer> peers) {
        this(peers, null);
    }

    public PeersManager() {
        this(System::currentTimeMillis, new HashMap<>(), null);
    }

    /*
     * Peers
     */

    public boolean hasApplicationTraffic(final ChannelHandlerContext ctx,
                                         final DrasylAddress peerKey) {
        final Peer peer = peers.get(peerKey);
        if (peer != null) {
            return peer.hasApplicationTraffic(ctx);
        }
        return false;
    }

    public void applicationMessageSentOrReceived(final DrasylAddress peerKey) {
        final Peer peer = peers.get(peerKey);
        if (peer != null) {
            peer.applicationMessageSentOrReceived();
        }
    }

    public InetSocketAddress resolve(final DrasylAddress peerKey) {
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

    private InetSocketAddress resolveDefault() {
        if (defaultPeerKey != null) {
            final Peer defaultPeer = peers.get(defaultPeerKey);
            return defaultPeer.resolve();
        }
        return null;
    }

    public boolean hasPath(final DrasylAddress peerKey) {
        Peer peer = peers.get(peerKey);
        if (peer != null) {
            return peer.hasPath();
        }
        else {
            return false;
        }
    }

    /*
     * Default peer
     */

    public boolean hasDefaultPeer() {
        return defaultPeerKey != null;
    }

    public DrasylAddress setDefaultPeer(final DrasylAddress defaultPeerKey) {
        final DrasylAddress previousDefaultPeer = this.defaultPeerKey;
        this.defaultPeerKey = requireNonNull(defaultPeerKey);
        return previousDefaultPeer;
    }

    public DrasylAddress unsetDefaultPeer() {
        final DrasylAddress previousDefaultPeer = defaultPeerKey;
        defaultPeerKey = null;
        return previousDefaultPeer;
    }

    /*
     * Paths
     */
    private boolean addPath(final ChannelHandlerContext ctx,
                            final PeerRole role,
                            final DrasylAddress peerKey,
                            final Class<?> id,
                            final InetSocketAddress endpoint,
                            final short priority,
                            final int rtt) {
        final Peer peer = peers.computeIfAbsent(peerKey, key -> new Peer(currentTime, key, role));
        if (peer.role() != UNDEFINED && peer.role() != role) {
            throw new IllegalStateException();
        }
        if (role != UNDEFINED) {
            peer.role = role;
        }

        if (peer.addPath(id, endpoint, priority)) {
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
    }

    private boolean removePath(final ChannelHandlerContext ctx,
                               final PeerRole role,
                               final DrasylAddress peerKey,
                               final Class<?> id) {
        final Peer peer = peers.get(peerKey);
        if (peer != null) {
            if (peer.role() != UNDEFINED && peer.role() != role) {
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
    }

    /*
     * Super peer paths
     */

    public boolean addSuperPeerPath(final ChannelHandlerContext ctx,
                                    final DrasylAddress peerKey,
                                    final Class<?> id,
                                    final InetSocketAddress endpoint,
                                    final short priority,
                                    final int rtt) {
        return addPath(ctx, SUPER, peerKey, id, endpoint, priority, rtt);
    }

    public boolean addSuperPeerPath(final ChannelHandlerContext ctx,
                                    final DrasylAddress peerKey,
                                    final Class<?> id,
                                    final InetSocketAddress endpoint,
                                    final short priority) {
        return addSuperPeerPath(ctx, peerKey, id, endpoint, priority, NO_RTT);
    }

    public boolean removeSuperPeerPath(final ChannelHandlerContext ctx,
                                       final DrasylAddress peerKey,
                                       final Class<?> id) {
        return removePath(ctx, SUPER, peerKey, id);
    }

    public void removeSuperPeerPaths(final ChannelHandlerContext ctx,
                                     final Class<?> id) {
        final Set<DrasylAddress> peerKeys = Set.copyOf(peers.keySet());
        peerKeys.forEach(peerKey -> removeSuperPeerPath(ctx, peerKey, id));
    }

    /*
     * Client paths
     */

    public boolean addClientPath(final ChannelHandlerContext ctx,
                                 final DrasylAddress peerKey,
                                 final Class<?> id,
                                 final InetSocketAddress endpoint,
                                 final short priority,
                                 final int rtt) {
        return addPath(ctx, CLIENT, peerKey, id, endpoint, priority, rtt);
    }

    public boolean addClientPath(final ChannelHandlerContext ctx,
                                 final DrasylAddress peerKey,
                                 final Class<?> id,
                                 final InetSocketAddress endpoint,
                                 final short priority) {
        return addClientPath(ctx, peerKey, id, endpoint, priority, NO_RTT);
    }

    public boolean removeClientPath(final ChannelHandlerContext ctx,
                                    final DrasylAddress peerKey,
                                    final Class<?> id) {
        return removePath(ctx, CLIENT, peerKey, id);
    }

    public void removeClientPaths(final ChannelHandlerContext ctx,
                                  final Class<?> id) {
        final Set<DrasylAddress> peerKeys = Set.copyOf(peers.keySet());
        peerKeys.forEach(peerKey -> removeClientPath(ctx, peerKey, id));
    }

    public boolean addUndefinedPath(final ChannelHandlerContext ctx,
                                    final DrasylAddress peerKey,
                                    final Class<?> id,
                                    final InetSocketAddress endpoint,
                                    final short priority) {
        return addPath(ctx, UNDEFINED, peerKey, id, endpoint, priority, NO_RTT);
    }

    /*
     * Paths
     */

    public Set<DrasylAddress> getPeers(final Class<?> id) {
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

    public boolean isStale(final ChannelHandlerContext ctx,
                           final DrasylAddress peerKey,
                           final Class<?> id) {
        final Peer peer = peers.get(peerKey);
        if (peer != null) {
            return peer.isStale(ctx, id);
        }
        return false;
    }

    public boolean isReachable(final ChannelHandlerContext ctx,
                               final DrasylAddress peerKey,
                               final Class<?> id) {
        final Peer peer = peers.get(peerKey);
        if (peer != null) {
            return peer.isReachable(ctx, id);
        }
        return false;
    }

    public boolean isNew(final ChannelHandlerContext ctx,
                         final DrasylAddress peerKey,
                         final Class<?> id) {
        final Peer peer = peers.get(peerKey);
        if (peer != null) {
            return peer.isNew(ctx, id);
        }
        return false;
    }

    public void helloMessageReceived(final DrasylAddress peerKey,
                                     final Class<?> id) {
        final Peer peer = peers.get(peerKey);
        if (peer != null) {
            peer.helloMessageReceived(id);
        }
    }

    public void helloMessageSent(final DrasylAddress peerKey,
                                 final Class<?> id) {
        final Peer peer = peers.get(peerKey);
        if (peer != null) {
            peer.helloMessageSent(id);
        }
    }

    public void acknowledgementMessageReceived(final DrasylAddress peerKey,
                                               final Class<?> id,
                                               final int rtt) {
        final Peer peer = peers.get(peerKey);
        if (peer != null) {
            peer.acknowledgementMessageReceived(id, rtt);
        }
    }

    public void acknowledgementMessageReceived(final DrasylAddress peerKey,
                                               final Class<?> id) {
        acknowledgementMessageReceived(peerKey, id, NO_RTT);
    }

    public int rtt(DrasylAddress peerKey, final Class<?> id) {
        final Peer peer = peers.get(peerKey);
        if (peer != null) {
            return peer.rtt(id);
        }
        return NO_RTT;
    }

    public InetSocketAddress resolveInetAddress(DrasylAddress peerKey, final Class<?> id) {
        final Peer peer = peers.get(peerKey);
        if (peer != null) {
            return peer.resolveEndpoint(id);
        }
        return null;
    }

    public Set<InetSocketAddress> getEndpoints(final Class<?> id) {
        final Set<InetSocketAddress> endpoints = new HashSet<>();
        for (final Peer peer : peers.values()) {
            final InetSocketAddress endpoint = peer.getEndpoint(id);
            if (endpoint != null) {
                endpoints.add(endpoint);
            }
        }
        return endpoints;
    }

    /*
     * Inner clases
     */

    static class Peer {
        private final LongSupplier currentTime;
        private final DrasylAddress address;
        private final Map<Class<?>, PeerPath> paths;
        PeerPath firstPath;
        private PeerRole role;
        private long lastApplicationMessageSentOrReceivedTime;

        Peer(final LongSupplier currentTime,
             final DrasylAddress address,
             final Map<Class<?>, PeerPath> paths,
             final PeerPath firstPath,
             final PeerRole role,
             final long lastApplicationMessageSentOrReceivedTime) {
            this.currentTime = requireNonNull(currentTime);
            this.address = requireNonNull(address);
            this.firstPath = firstPath;
            this.paths = requireNonNull(paths);
            this.role = requireNonNull(role);
            this.lastApplicationMessageSentOrReceivedTime = lastApplicationMessageSentOrReceivedTime;
        }

        Peer(final LongSupplier currentTime,
             final DrasylAddress address,
             final Map<Class<?>, PeerPath> paths,
             final PeerPath firstPath,
             final PeerRole role) {
            this(currentTime, address, paths, firstPath, role, 0);
        }

        Peer(final LongSupplier currentTime,
             final DrasylAddress address,
             final PeerRole role) {
            this(currentTime, address, new HashMap<>(), null, role);
        }

        Peer(final DrasylAddress address,
             final Map<Class<?>, PeerPath> paths,
             final PeerPath firstPath,
             final PeerRole role) {
            this(System::currentTimeMillis, address, paths, firstPath, role, 0);
        }

        int pathCount() {
            return paths.size();
        }

        boolean hasPath() {
            return firstPath != null;
        }

        Map<Class<?>, PeerPath> paths() {
            return paths;
        }

        InetSocketAddress resolve() {
            if (firstPath != null) {
                return firstPath.endpoint;
            }
            else {
                return null;
            }
        }

        public PeerRole role() {
            return role;
        }

        void applicationMessageSentOrReceived() {
            lastApplicationMessageSentOrReceivedTime = currentTime.getAsLong();
        }

        boolean hasApplicationTraffic(final ChannelHandlerContext ctx) {
            final long pathIdleTimeMillis = ((DrasylServerChannelConfig) ctx.channel().config()).getPathIdleTime().toMillis();
            return lastApplicationMessageSentOrReceivedTime >= currentTime.getAsLong() - pathIdleTimeMillis;
        }

        /*
         * Paths
         */

        boolean addPath(final Class<?> id,
                        final InetSocketAddress endpoint,
                        final short priority) {
            if (endpoint != null && endpoint.isUnresolved()) {
                throw new UnresolvedAddressException();
            }

            if (paths.containsKey(id)) {
                // update endpoint?
                PeerPath current = firstPath;
                while (current != null) {
                    if (current.id == id) {
                        // FIXME: change only attribute or replace whole path? and return true?
                        current.endpoint = endpoint;
                        break;
                    }
                    current = current.next;
                }

                return false;
            }

            final PeerPath newPath = new PeerPath(id, endpoint, priority);
            paths.put(id, newPath);

            final PeerPath head = firstPath;
            if (head == null || head.priority > newPath.priority) {
                // replace head
                newPath.next = head;
                firstPath = newPath;
            }
            else {
                PeerPath current = head;
                while (current.next != null && current.next.priority <= newPath.priority) {
                    current = current.next;
                }

                newPath.next = current.next;
                current.next = newPath;
            }

            return true;
        }

        boolean removePath(final Class<?> id) {
            if (!paths.containsKey(id)) {
                return false;
            }

            PeerPath prev = null;
            PeerPath current = firstPath;
            while (current != null) {
                if (current.id == id) {
                    if (prev != null) {
                        prev.next = current.next;
                    }
                    else if (current.next != null) {
                        firstPath = current.next;
                    }
                    else {
                        firstPath = null;
                    }

                    return true;
                }

                prev = current;
                current = current.next;
            }

            return false;
        }

        boolean isStale(final ChannelHandlerContext ctx,
                        final Class<?> id) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                return path.isStale(ctx);
            }
            return true;
        }

        boolean isReachable(final ChannelHandlerContext ctx,
                            final Class<?> id) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                return path.isReachable(ctx);
            }
            return false;
        }

        public boolean isNew(final ChannelHandlerContext ctx, final Class<?> id) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                return path.isNew(ctx);
            }
            return false;
        }

        public void helloMessageSent(final Class<?> id) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                path.helloMessageSent();
            }
        }

        public void helloMessageReceived(final Class<?> id) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                path.helloMessageReceived();
            }
        }

        public void acknowledgementMessageReceived(final Class<?> id, final int rtt) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                path.acknowledgementMessageReceived(rtt);
            }
        }

        public int rtt(final Class<?> id) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                return path.rtt();
            }
            return NO_RTT;
        }

        public InetSocketAddress resolveEndpoint(final Class<?> id) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                return path.resolveEndpoint();
            }
            return null;
        }

        public InetSocketAddress getEndpoint(final Class<?> id) {
            final PeerPath path = paths.get(id);
            if (path != null) {
                return path.endpoint;
            }
            return null;
        }

        public PathEvent addPeerEvent(final InetSocketAddress endpoint,
                                      final Class<?> id,
                                      final int rtt) {
            if (role == SUPER) {
                return AddPathAndSuperPeerEvent.of(address, endpoint, id, rtt);
            }
            else {
                return AddPathAndChildrenEvent.of(address, endpoint, id);
            }
        }

        public PathEvent addPathEvent(final InetSocketAddress endpoint,
                                      final Class<?> id,
                                      final int rtt) {
            return AddPathEvent.of(address, endpoint, id, rtt);
        }

        public PathEvent removePathEvent(final Class<?> id) {
            return RemovePathEvent.of(address, id);
        }

        public PathEvent removePeerEvent(final Class<?> id) {
            return RemoveSuperPeerAndPathEvent.of(address, id);
        }
    }

    enum PeerRole {
        SUPER("S"),
        CLIENT("C"),
        UNDEFINED("");
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
        private final Class<?> id;
        private InetSocketAddress endpoint;
        private final short priority;
        private PeerPath next;
        long lastHelloMessageReceivedTime;
        long firstHelloMessageSentTime;
        long lastAcknowledgementMessageReceivedTime;
        int rtt;

        @SuppressWarnings("java:S107")
        PeerPath(final LongSupplier currentTime,
                 final Class<?> id,
                 final InetSocketAddress endpoint,
                 final short priority,
                 final PeerPath next,
                 final long lastHelloMessageReceivedTime,
                 final long firstHelloMessageSentTime,
                 final long lastAcknowledgementMessageReceivedTime,
                 final int rtt) {
            this.currentTime = requireNonNull(currentTime);
            this.id = requireNonNull(id);
            this.endpoint = endpoint;
            this.priority = priority;
            this.next = next;
            this.lastHelloMessageReceivedTime = lastHelloMessageReceivedTime;
            this.firstHelloMessageSentTime = firstHelloMessageSentTime;
            this.lastAcknowledgementMessageReceivedTime = lastAcknowledgementMessageReceivedTime;
            this.rtt = rtt;
        }

        PeerPath(final Class<?> id,
                 final InetSocketAddress endpoint,
                 final short priority) {
            this(System::currentTimeMillis, id, endpoint, priority, null, 0, 0, 0, NO_RTT);
        }

        @Override
        public String toString() {
            return "PeerPath{" +
                    "id=" + id +
                    ", endpoint=" + endpoint +
                    ", priority=" + priority +
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
            return priority == path.priority && lastHelloMessageReceivedTime == path.lastHelloMessageReceivedTime && Objects.equals(id, path.id) && Objects.equals(endpoint, path.endpoint) && Objects.equals(next, path.next);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, endpoint, priority, next, lastHelloMessageReceivedTime);
        }

        void helloMessageSent() {
            if (firstHelloMessageSentTime == 0) {
                firstHelloMessageSentTime = currentTime.getAsLong();
            }
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

        boolean isNew(final ChannelHandlerContext ctx) {
            return firstHelloMessageSentTime >= currentTime.getAsLong() - ((DrasylServerChannelConfig) ctx.channel().config()).getHelloTimeout().toMillis();
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
    }
}
