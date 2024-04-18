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

import java.net.InetSocketAddress;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

import static java.util.Objects.requireNonNull;

public class PeersManager {
    private static final int NO_RTT = -1;
    private final Map<DrasylAddress, AbstractPeer> peers;
    private DrasylAddress defaultPeer;

    PeersManager(final Map<DrasylAddress, AbstractPeer> peers) {
        this.peers = requireNonNull(peers);
    }

    public PeersManager() {
        this(new HashMap<>());
    }

    public boolean addSuperPeerPath(final ChannelHandlerContext ctx,
                                    final DrasylAddress peerKey,
                                    final Class<?> id,
                                    final InetSocketAddress endpoint,
                                    final short priority,
                                    final int rtt) {
        final AbstractPeer peer = peers.computeIfAbsent(peerKey, key -> new SuperPeer());
        if (!(peer instanceof SuperPeer)) {
            throw new IllegalStateException();
        }

        if (peer.addPath(ctx, id, endpoint, priority)) {
            if (peer.getPathCount(ctx) == 1) {
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
        final AbstractPeer peer = peers.get(peerKey);
        if (peer != null) {
            if (!(peer instanceof SuperPeer)) {
                throw new IllegalStateException();
            }

            if (peer.removePath(ctx, id)) {
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

    public void removeSuperPeerPaths(final ChannelHandlerContext ctx, final Class<?> id) {
        final Set<DrasylAddress> peerKeys = Set.copyOf(peers.keySet());
        peerKeys.forEach(peerKey -> removeSuperPeerPath(ctx, peerKey, id));
    }

    public boolean addClientPath(final ChannelHandlerContext ctx,
                                 final DrasylAddress peerKey,
                                 final Class<?> id,
                                 final InetSocketAddress endpoint,
                                 final short priority,
                                 final long rtt) {
        final AbstractPeer peer = peers.computeIfAbsent(peerKey, key -> new ClientPeer());
        if (!(peer instanceof ClientPeer)) {
            throw new IllegalStateException();
        }

        if (peer.addPath(ctx, id, endpoint, priority)) {
            if (peer.getPathCount(ctx) == 1) {
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
        final AbstractPeer peer = peers.get(peerKey);
        if (peer != null && peer.removePath(ctx, id)) {
            ctx.fireUserEventTriggered(RemoveChildrenAndPathEvent.of(peerKey, id));
            return true;
        }
        else {
            return false;
        }
    }

    public void removeClientPaths(final ChannelHandlerContext ctx, final Class<?> id) {
        final Set<DrasylAddress> peerKeys = Set.copyOf(peers.keySet());
        peerKeys.forEach(peerKey -> removeClientPath(ctx, peerKey, id));
    }

    public InetSocketAddress getDirectEndpoint(final DrasylAddress peerKey) {
        final AbstractPeer peer = peers.get(peerKey);
        if (peer != null) {
            return peer.resolve();
        }
        else {
            return null;
        }
    }

    public InetSocketAddress getEndpoint(final DrasylAddress peer) {
        final InetSocketAddress endpoint = getDirectEndpoint(peer);
        if (endpoint != null) {
            return endpoint;
        }
        else {
            return getDirectEndpoint(getDefaultPeer());
        }
    }

    public Set<DrasylAddress> getPeers(final Class<?> id) {
        final Set<DrasylAddress> peerKeys = new HashSet<>();
        for (final Entry<DrasylAddress, AbstractPeer> entry : peers.entrySet()) {
            final DrasylAddress peerKey = entry.getKey();
            final AbstractPeer peer = entry.getValue();
            if (peer.paths.containsKey(id)) {
                peerKeys.add(peerKey);
            }
        }
        return peerKeys;
    }

    public void helloMessageReceived(final DrasylAddress peer, final Class<?> id) {
        final PeerPath path = getPath(peer, id);
        path.helloMessageReceived();
    }

    public void applicationMessageSentOrReceived(final DrasylAddress peerKey) {
        final AbstractPeer peer = peers.computeIfAbsent(peerKey, key -> new AbstractPeer());
        peer.applicationMessageSentOrReceived();
    }

    public boolean isStale(final ChannelHandlerContext ctx, final DrasylAddress peer, final Class<?> id) {
        return lastHelloMessageReceivedTime(peer, id) < System.currentTimeMillis() - ((DrasylServerChannel) ctx.channel()).config().getHelloTimeout().toMillis();
    }

    public long lastHelloMessageReceivedTime(final DrasylAddress peer, final Class<?> id) {
        final PeerPath path = getPath(peer, id);
        return path.lastHelloMessageReceivedTime;
    }

    public long lastApplicationMessageSentOrReceivedTime(final DrasylAddress peerKey) {
        final AbstractPeer peer = peers.computeIfAbsent(peerKey, key -> new AbstractPeer());
        return peer.lastApplicationMessageSentOrReceivedTime;
    }

    public boolean hasDefaultPeer() {
        return defaultPeer != null;
    }

    public void setDefaultPath(final ChannelHandlerContext ctx,
                               final DrasylAddress defaultPath) {
        this.defaultPeer = requireNonNull(defaultPath);
    }

    public void unsetDefaultPath(final ChannelHandlerContext ctx) {
        defaultPeer = null;
    }

    public DrasylAddress getDefaultPeer() {
        return defaultPeer;
    }

    public List<InetSocketAddress> getEndpoints(final DrasylAddress peerKey) {
        final AbstractPeer peer = peers.computeIfAbsent(peerKey, key -> new AbstractPeer());
        return peer.getEndpoints();
    }

    public boolean hasPath(final DrasylAddress peerKey) {
        AbstractPeer peer = peers.get(peerKey);
        if (peer != null) {
            return peer.hasPath();
        }
        else {
            return false;
        }
    }

    private PeerPath getPath(final DrasylAddress peerKey, final Class<?> id) {
        final AbstractPeer peer = peers.get(peerKey);
        if (peer != null) {
            return peer.getPath(id);
        }
        return null;
    }

    static class AbstractPeer {
        private final LongSupplier currentTime;
        private final Map<Class<?>, PeerPath> paths;
        PeerPath firstPath;
        private long lastApplicationMessageSentOrReceivedTime;

        protected AbstractPeer(final LongSupplier currentTime,
                               final Map<Class<?>, PeerPath> paths,
                               final PeerPath firstPath,
                               final long lastApplicationMessageSentOrReceivedTime) {
            this.currentTime = currentTime;
            this.firstPath = firstPath;
            this.paths = requireNonNull(paths);
            this.lastApplicationMessageSentOrReceivedTime = lastApplicationMessageSentOrReceivedTime;
        }

        protected AbstractPeer() {
            this(System::currentTimeMillis, new HashMap<>(), null, 0);
        }

        public void applicationMessageSentOrReceived() {
            lastApplicationMessageSentOrReceivedTime = System.currentTimeMillis();
        }

        public boolean addPath(final ChannelHandlerContext ctx,
                               final Class<?> id,
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

        public boolean removePath(final ChannelHandlerContext ctx, final Class<?> id) {
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

        public InetSocketAddress resolve() {
            if (firstPath != null) {
                return firstPath.endpoint;
            }
            else {
                return null;
            }
        }

        public PeerPath getPath(final Class<?> id) {
            PeerPath current = firstPath;
            while (current != null) {
                if (current.id == id) {
                    return current;
                }

                current = current.next;
            }

            return null;
        }

        public List<InetSocketAddress> getEndpoints() {
            final List<InetSocketAddress> endpoints = new ArrayList<>();
            PeerPath current = firstPath;
            while (current != null) {
                endpoints.add(current.endpoint);
                current = current.next;
            }
            return endpoints;
        }

        public boolean hasPath() {
            return firstPath != null;
        }

        public int getPathCount(final ChannelHandlerContext ctx) {
            return paths.size();
        }
    }

    static class SuperPeer extends AbstractPeer {
        public SuperPeer() {
            super();
        }

        public SuperPeer(final LongSupplier currentTime,
                         final Map<Class<?>, PeerPath> paths,
                         final PeerPath firstPath,
                         final long lastApplicationMessageSentOrReceivedTime) {
            super(currentTime, paths, firstPath, lastApplicationMessageSentOrReceivedTime);
        }
    }

    static class ClientPeer extends AbstractPeer {
        public ClientPeer() {
            super();
        }
    }

    static class PeerPath {
        private final LongSupplier currentTime;
        private final Class<?> id;
        private InetSocketAddress endpoint;
        private final short priority;
        private PeerPath next;
        long lastHelloMessageReceivedTime;

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

        public PeerPath(final Class<?> id,
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

        public void helloMessageReceived() {
            lastHelloMessageReceivedTime = currentTime.getAsLong();
        }
    }
}
