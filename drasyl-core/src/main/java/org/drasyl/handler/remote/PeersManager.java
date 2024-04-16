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
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.discovery.RemoveChildrenAndPathEvent;
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
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requirePositive;

public class PeersManager {
    private final Map<DrasylAddress, AbstractPeer> peers = new HashMap<>();
    private final long helloTimeoutMillis;
    private DrasylAddress defaultPeer;

    public PeersManager(final long helloTimeoutMillis) {
        this.helloTimeoutMillis = requirePositive(helloTimeoutMillis);
    }

    public PeersManager() {
        this(
                30_000 // config.getRemotePingTimeout().toMillis() -> DrasylServerChannel option?
        );
    }

    public boolean addSuperPeerPath(final ChannelHandlerContext ctx,
                                    final DrasylAddress peerKey,
                                    final Class<?> id,
                                    final InetSocketAddress endpoint,
                                    final short priority,
                                    long rtt) {
        final AbstractPeer peer = peers.computeIfAbsent(peerKey, key -> new SuperPeer());
        if (!(peer instanceof SuperPeer)) {
            throw new IllegalStateException();
        }

        if (peer.addPath(ctx, id, endpoint, priority)) {
            ctx.fireUserEventTriggered(AddPathAndSuperPeerEvent.of(peerKey, endpoint, id, rtt));
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
        return addSuperPeerPath(ctx, peerKey, id, endpoint, priority, -1);
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
            ctx.fireUserEventTriggered(AddPathAndChildrenEvent.of(peerKey, endpoint, id));
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
        return addClientPath(ctx, peerKey, id, endpoint, priority, -1);
    }

    public void removePaths(final ChannelHandlerContext ctx, final Class<?> id) {
        final Set<AbstractPeer> peersCopy = Set.copyOf(peers.values());
        peersCopy.forEach(peer -> peer.removePath(ctx, id));
    }

    public InetSocketAddress getDirectEndpoint(final DrasylAddress peerKey, final Class<?> id) {
        final AbstractPeer peer = peers.get(peerKey);
        if (peer != null) {
            return peer.getDirectEndpoint(id);
        }
        else {
            return null;
        }
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
            if (peer.pathIds.contains(id)) {
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

    public boolean isStale(final DrasylAddress peer, final Class<?> id) {
        return lastHelloMessageReceivedTime(peer, id) < System.currentTimeMillis() - helloTimeoutMillis;
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

    public void removeClientPaths(final ChannelHandlerContext ctx, final Class<?> id) {
        final Set<DrasylAddress> peerKeys = Set.copyOf(peers.keySet());
        peerKeys.forEach(peerKey -> removeClientPath(ctx, peerKey, id));
    }

    public boolean removeClientPath(final ChannelHandlerContext ctx, final DrasylAddress peerKey, final Class<?> id) {
        final AbstractPeer peer = peers.get(peerKey);
        if (peer != null && peer.removePath(ctx, id)) {
            ctx.fireUserEventTriggered(RemoveChildrenAndPathEvent.of(peerKey, id));
            return true;
        }
        else {
            return false;
        }
    }

    public boolean removeSuperPeerPath(ChannelHandlerContext ctx, DrasylAddress peerKey, Class<?> id) {
        final AbstractPeer peer = peers.get(peerKey);
        if (peer != null && peer.removePath(ctx, id)) {
            ctx.fireUserEventTriggered(RemoveSuperPeerAndPathEvent.of(peerKey, id));
            return true;
        }
        else {
            return false;
        }
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
        private PeerPath bestPath;
        private final Set<Class<?>> pathIds = new HashSet<>();
        private long lastApplicationMessageSentOrReceivedTime;

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

            if (!pathIds.add(id)) {
                // update endpoint?
                PeerPath current = bestPath;
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

            final PeerPath head = bestPath;
            if (head == null || head.priority > newPath.priority) {
                // replace head
                newPath.next = head;
                bestPath = newPath;
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
            if (!pathIds.remove(id)) {
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

        public InetSocketAddress resolve() {
            if (bestPath != null) {
                return bestPath.endpoint;
            }
            else {
                return null;
            }
        }

        public PeerPath getPath(final Class<?> id) {
            PeerPath current = bestPath;
            while (current != null) {
                if (current.id == id) {
                    return current;
                }

                current = current.next;
            }

            return null;
        }

        public InetSocketAddress getDirectEndpoint(final Class<?> id) {
            final PeerPath path = getPath(id);
            if (path != null) {
                return path.endpoint;
            }
            return null;
        }

        public List<InetSocketAddress> getEndpoints() {
            final List<InetSocketAddress> endpoints = new ArrayList<>();
            PeerPath current = bestPath;
            while (current != null) {
                endpoints.add(current.endpoint);
                current = current.next;
            }
            return endpoints;
        }

        public boolean hasPath() {
            return bestPath != null;
        }
    }

    static class SuperPeer extends AbstractPeer {
    }

    static class ClientPeer extends AbstractPeer {
    }

    static class PeerPath {
        private final Class<?> id;
        private InetSocketAddress endpoint;
        private final short priority;
        private PeerPath next;
        private long lastHelloMessageReceivedTime;

        public PeerPath(final Class<?> id,
                        final InetSocketAddress endpoint,
                        final short priority) {
            this.id = requireNonNull(id);
            this.endpoint = endpoint;
            this.priority = priority;
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

        public void helloMessageReceived() {
            lastHelloMessageReceivedTime = System.currentTimeMillis();
        }
    }
}
