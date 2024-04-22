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
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.discovery.RemoveChildrenAndPathEvent;
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

public class PeersManager {
    private static final Logger LOG = LoggerFactory.getLogger(PeersManager.class);
    private static final int NO_RTT = -1;
    private final LongSupplier currentTime;
    private final Map<DrasylAddress, Peer> peers;
    private DrasylAddress defaultPeerKey;

    PeersManager(final LongSupplier currentTime,
                 final Map<DrasylAddress, Peer> peers,
                 final DrasylAddress defaultPeerKey) {
        this.peers = requireNonNull(peers);
        this.currentTime = requireNonNull(currentTime);
        this.defaultPeerKey = defaultPeerKey;
    }

    PeersManager(final Map<DrasylAddress, Peer> peers) {
        this(System::currentTimeMillis, peers, null);
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
            if (endpoint == null && defaultPeerKey != null) {
                final Peer defaultPeer = peers.get(defaultPeerKey);
                return defaultPeer.resolve();
            }
            return endpoint;
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

    public DrasylAddress setDefaultPeer(final DrasylAddress defaultPeer) {
        final DrasylAddress previousDefaultPeer = defaultPeerKey;
        this.defaultPeerKey = requireNonNull(defaultPeer);
        return previousDefaultPeer;
    }

    public boolean unsetDefaultPeer() {
        final DrasylAddress previousDefaultPeer = defaultPeerKey;
        defaultPeerKey = null;
        return previousDefaultPeer != null;
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
        final Peer peer = peers.computeIfAbsent(peerKey, key -> new SuperPeer(currentTime));
        if (!(peer instanceof SuperPeer)) {
            throw new IllegalStateException();
        }

        if (peer.addPath(id, endpoint, priority)) {
            if (peer.getPathCount() == 1) {
                ctx.fireUserEventTriggered(AddPathAndSuperPeerEvent.of(peerKey, endpoint, id, rtt));
            }
            else {
                ctx.fireUserEventTriggered(AddPathEvent.of(peerKey, endpoint, id, rtt));
            }
            return true;
        }
        else if (rtt >= 0) {
            ctx.fireUserEventTriggered(PathRttEvent.of(peerKey, endpoint, id, rtt));
        }
        return false;
    }

    public boolean addSuperPeerPath(final ChannelHandlerContext ctx,
                                    final DrasylAddress peerKey,
                                    final Class<?> id,
                                    final InetSocketAddress endpoint,
                                    final short priority) {
        return addSuperPeerPath(ctx, peerKey, id, endpoint, priority, NO_RTT);
    }

    public boolean removeSuperPeerPath(ChannelHandlerContext ctx,
                                       DrasylAddress peerKey,
                                       Class<?> id) {
        final Peer peer = peers.get(peerKey);
        if (peer != null) {
            if (!(peer instanceof SuperPeer)) {
                throw new IllegalStateException();
            }

            if (peer.removePath(id)) {
                if (peer.hasPath()) {
                    ctx.fireUserEventTriggered(RemovePathEvent.of(peerKey, id));
                }
                else {
                    peers.remove(peerKey);
                    ctx.fireUserEventTriggered(RemoveSuperPeerAndPathEvent.of(peerKey, id));
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
                                 final long rtt) {
        final Peer peer = peers.computeIfAbsent(peerKey, key -> new ClientPeer(currentTime));
        if (!(peer instanceof ClientPeer)) {
            throw new IllegalStateException();
        }

        if (peer.addPath(id, endpoint, priority)) {
            if (peer.getPathCount() == 1) {
                ctx.fireUserEventTriggered(AddPathAndChildrenEvent.of(peerKey, endpoint, id));
            }
            else {
                ctx.fireUserEventTriggered(AddPathEvent.of(peerKey, endpoint, id, rtt));
            }
            return true;
        }
        else if (rtt >= 0) {
            ctx.fireUserEventTriggered(PathRttEvent.of(peerKey, endpoint, id, rtt));
        }
        return false;
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
        final Peer peer = peers.get(peerKey);
        if (peer != null) {
            if (!(peer instanceof ClientPeer)) {
                throw new IllegalStateException();
            }

            if (peer.removePath(id)) {
                if (peer.hasPath()) {
                    ctx.fireUserEventTriggered(RemovePathEvent.of(peerKey, id));
                }
                else {
                    peers.remove(peerKey);
                    ctx.fireUserEventTriggered(RemoveChildrenAndPathEvent.of(peerKey, id));
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

    public void removeClientPaths(final ChannelHandlerContext ctx,
                                  final Class<?> id) {
        final Set<DrasylAddress> peerKeys = Set.copyOf(peers.keySet());
        peerKeys.forEach(peerKey -> removeClientPath(ctx, peerKey, id));
    }

    /*
     * Paths
     */

    public Set<DrasylAddress> getPeers(final Class<?> id) {
        final Set<DrasylAddress> peerKeys = new HashSet<>();
        for (final Entry<DrasylAddress, Peer> entry : peers.entrySet()) {
            final DrasylAddress peerKey = entry.getKey();
            final Peer peer = entry.getValue();
            if (peer.getPaths().containsKey(id)) {
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
        private final Map<Class<?>, PeerPath> paths;
        PeerPath firstPath;
        private long lastApplicationMessageSentOrReceivedTime;

        Peer(final LongSupplier currentTime,
             final Map<Class<?>, PeerPath> paths,
             final PeerPath firstPath,
             final long lastApplicationMessageSentOrReceivedTime) {
            this.currentTime = currentTime;
            this.firstPath = firstPath;
            this.paths = requireNonNull(paths);
            this.lastApplicationMessageSentOrReceivedTime = lastApplicationMessageSentOrReceivedTime;
        }

        Peer(final LongSupplier currentTime,
             final Map<Class<?>, PeerPath> paths,
             final PeerPath firstPath) {
            this(currentTime, paths, firstPath, 0);
        }

        Peer(final LongSupplier currentTime) {
            this(currentTime, new HashMap<>(), null);
        }

        public Peer(final Map<Class<?>, PeerPath> paths,
                    final PeerPath firstPath) {
            this(System::currentTimeMillis, paths, firstPath, 0);
        }

        int getPathCount() {
            return paths.size();
        }

        boolean hasPath() {
            return firstPath != null;
        }

        Map<Class<?>, PeerPath> getPaths() {
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

        void applicationMessageSentOrReceived() {
            lastApplicationMessageSentOrReceivedTime = currentTime.getAsLong();
        }

        boolean hasApplicationTraffic(final ChannelHandlerContext ctx) {
            final long pathIdleTimeMillis = ((DrasylServerChannel) ctx.channel()).config().getPathIdleTime().toMillis();
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
    }

    static class SuperPeer extends Peer {
        SuperPeer(final LongSupplier currentTime) {
            super(currentTime);
        }
    }

    static class ClientPeer extends Peer {
        ClientPeer(final LongSupplier currentTime) {
            super(currentTime);
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
        int rtt = NO_RTT;

        PeerPath(final LongSupplier currentTime,
                 final Class<?> id,
                 final InetSocketAddress endpoint,
                 final short priority,
                 final PeerPath next) {
            this.currentTime = requireNonNull(currentTime);
            this.id = requireNonNull(id);
            this.endpoint = endpoint;
            this.priority = priority;
            this.next = next;
        }

        PeerPath(final Class<?> id,
                 final InetSocketAddress endpoint,
                 final short priority) {
            this(System::currentTimeMillis, id, endpoint, priority, null);
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
            return lastHelloMessageReceivedTime < currentTime.getAsLong() - ((DrasylServerChannel) ctx.channel()).config().getHelloTimeout().toMillis();
        }

        boolean isReachable(final ChannelHandlerContext ctx) {
            return lastAcknowledgementMessageReceivedTime >= currentTime.getAsLong() - ((DrasylServerChannel) ctx.channel()).config().getHelloTimeout().toMillis();
        }

        boolean isNew(final ChannelHandlerContext ctx) {
            return firstHelloMessageSentTime >= currentTime.getAsLong() - ((DrasylServerChannel) ctx.channel()).config().getHelloTimeout().toMillis();
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
