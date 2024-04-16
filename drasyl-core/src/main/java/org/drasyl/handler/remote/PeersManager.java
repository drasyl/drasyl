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
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.discovery.RemoveChildrenAndPathEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.HashSetMultimap;
import org.drasyl.util.SetMultimap;

import java.net.InetSocketAddress;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requirePositive;

public class PeersManager {
    private final Map<DrasylAddress, Peer> peers = new HashMap<>();
    private final Map<DrasylAddress, PeerPath> paths = new HashMap<>();
    private final SetMultimap<DrasylAddress, Class<?>> ids = new HashSetMultimap<>();
    private final Map<DrasylAddress, Integer> localPaths = new HashMap<>();
    private final long helloTimeoutMillis;
    private DrasylAddress defaultPath;

    public PeersManager(final long helloTimeoutMillis) {
        this.helloTimeoutMillis = requirePositive(helloTimeoutMillis);
    }

    public PeersManager() {
        this(
                30_000 // config.getRemotePingTimeout().toMillis() -> DrasylServerChannel option?
        );
    }

    /**
     * @throws UnresolvedAddressException
     */
    public boolean addPath(final ChannelHandlerContext ctx,
                           final DrasylAddress peer,
                           final Class<?> id,
                           final InetSocketAddress endpoint,
                           final short priority) {
//        assert ctx.channel() instanceof DrasylServerChannel;

        if (endpoint.isUnresolved()) {
            throw new UnresolvedAddressException();
        }

        if (!ids.put(peer, id)) {
            // update endpoint?
            PeerPath current = paths.get(peer);
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

        final PeerPath head = paths.get(peer);
        if (head == null || head.priority > newPath.priority) {
            // replace head
            newPath.next = head;
            paths.put(peer, newPath);
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

    public boolean addSuperPeerPath(final ChannelHandlerContext ctx,
                                    final DrasylAddress peer,
                                    final Class<?> id,
                                    final InetSocketAddress endpoint,
                                    final short priority) {
        return addPath(ctx, peer, id, endpoint, priority);
    }

    public boolean addClientPath(final ChannelHandlerContext ctx,
                                 final DrasylAddress peer,
                                 final Class<?> id,
                                 final InetSocketAddress endpoint,
                                 final short priority,
                                 final long rtt) {
        if (addPath(ctx, peer, id, endpoint, priority)) {
            ctx.fireUserEventTriggered(AddPathAndChildrenEvent.of(peer, endpoint, id));
            return true;
        }
        ctx.fireUserEventTriggered(PathRttEvent.of(peer, endpoint, id, rtt));
        return false;
    }

    public boolean addClientPath(final ChannelHandlerContext ctx,
                                 final DrasylAddress peer,
                                 final Class<?> id,
                                 final InetSocketAddress endpoint,
                                 final short priority) {
        if (addPath(ctx, peer, id, endpoint, priority)) {
            ctx.fireUserEventTriggered(AddPathAndChildrenEvent.of(peer, endpoint, id));
            return true;
        }
        return false;
    }

    public void addLocalClientPath(final ChannelHandlerContext ctx,
                                   final DrasylAddress peer,
                                   final Class<?> id) {
        assert ctx.channel() instanceof DrasylServerChannel;

        final int count = localPaths.computeIfAbsent(peer, k -> 0);
        localPaths.put(peer, count + 1);
        if (count == 0) {
            ctx.fireUserEventTriggered(AddPathAndChildrenEvent.of(peer, null, "local"));
        }
    }

    public void removeLocalClientPath(final ChannelHandlerContext ctx,
                                      final DrasylAddress peer,
                                      final Class<?> id) {
        assert ctx.channel() instanceof DrasylServerChannel;

        final int count = localPaths.get(peer);
        if (count == 1) {
            localPaths.remove(peer);
            ctx.fireUserEventTriggered(RemoveChildrenAndPathEvent.of(peer, "local"));
        }
        else {
            localPaths.put(peer, count - 1);
        }
    }

    public boolean removePath(final ChannelHandlerContext ctx,
                              final DrasylAddress peer,
                              final Class<?> id) {
        assert ctx.channel() instanceof DrasylServerChannel;

        if (!ids.remove(peer, id)) {
            return false;
        }

        PeerPath prev = null;
        PeerPath current = paths.get(peer);
        while (current != null) {
            if (current.id == id) {
                if (prev != null) {
                    prev.next = current.next;
                }
                else if (current.next != null) {
                    paths.put(peer, current.next);
                }
                else {
                    paths.remove(peer);
                }

                return true;
            }

            prev = current;
            current = current.next;
        }

        return false;
    }

    public void removePaths(final ChannelHandlerContext ctx, final Class<?> id) {
        final Set<DrasylAddress> peers = Set.copyOf(ids.keySet());
        peers.forEach(peer -> removePath(ctx, peer, id));
    }

    public InetSocketAddress resolve(final DrasylAddress peer) {
        final PeerPath path = paths.get(peer);
        if (path != null) {
            return path.endpoint;
        }
        else {
            return null;
        }
    }

    private PeerPath getPath(final DrasylAddress peer, final Class<?> id) {
        PeerPath current = paths.get(peer);
        while (current != null) {
            if (current.id == id) {
                return current;
            }

            current = current.next;
        }

        return null;
    }

    public InetSocketAddress getDirectEndpoint(final DrasylAddress peer, final Class<?> id) {
        final PeerPath path = getPath(peer, id);
        if (path != null) {
            return path.endpoint;
        }
        return null;
    }

    public InetSocketAddress getDirectEndpoint(final DrasylAddress peer) {
        final PeerPath path = paths.get(peer);
        if (path != null) {
            return path.endpoint;
        }
        return null;
    }

    public InetSocketAddress getEndpoint(final DrasylAddress peer) {
        final InetSocketAddress endpoint = getDirectEndpoint(peer);
        if (endpoint != null) {
            return endpoint;
        }
        return getDirectEndpoint(getDefaultPeer());
    }

    public Set<DrasylAddress> getPeers(final Class<?> id) {
        final Set<DrasylAddress> peers = new HashSet<>();
        for (final DrasylAddress peer : ids.keySet()) {
            if (ids.get(peer).contains(id)) {
                peers.add(peer);
            }
        }
        return peers;
    }

    public void helloMessageReceived(final DrasylAddress peer, final Class<?> id) {
        final PeerPath path = getPath(peer, id);
        path.helloMessageReceived();
    }

    public void applicationMessageSentOrReceived(final DrasylAddress peerKey) {
        final Peer peer = peers.computeIfAbsent(peerKey, key -> new Peer());
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
        final Peer peer = peers.computeIfAbsent(peerKey, key -> new Peer());
        return peer.lastApplicationMessageSentOrReceivedTime;
    }

    public boolean hasDefaultPeer() {
        return defaultPath != null;
    }

    public void setDefaultPath(final ChannelHandlerContext ctx,
                               final DrasylAddress defaultPath) {
        assert ctx.channel() instanceof DrasylServerChannel;

        this.defaultPath = requireNonNull(defaultPath);
    }

    public void unsetDefaultPath(final ChannelHandlerContext ctx) {
        assert ctx.channel() instanceof DrasylServerChannel;
        defaultPath = null;
    }

    public DrasylAddress getDefaultPeer() {
        return defaultPath;
    }

    public List<InetSocketAddress> getEndpoints(final DrasylAddress peer) {
        final List<InetSocketAddress> endpoints = new ArrayList<>();
        PeerPath current = paths.get(peer);
        while (current != null) {
            endpoints.add(current.endpoint);
            current = current.next;
        }
        return endpoints;
    }

    public void removeClientPaths(ChannelHandlerContext ctx, Class<?> id) {
        final Set<DrasylAddress> peers = Set.copyOf(ids.keySet());
        peers.forEach(peer -> removeClientPath(ctx, peer, id));
    }

    public boolean removeClientPath(ChannelHandlerContext ctx, DrasylAddress peer, Class<?> id) {
        if (removePath(ctx, peer, id)) {
            ctx.fireUserEventTriggered(RemoveChildrenAndPathEvent.of(peer, id));
            return true;
        }
        return false;
    }

    public boolean removeSuperPeerPath(ChannelHandlerContext ctx, DrasylAddress peer, Class<?> id) {
        if (removePath(ctx, peer, id)) {
            ctx.fireUserEventTriggered(RemoveSuperPeerAndPathEvent.of(peer, id));
            return true;
        }
        return false;
    }

    public boolean hasPath(final DrasylAddress peers) {
        return paths.get(peers) != null;
    }

    static class Peer {
        private long lastApplicationMessageSentOrReceivedTime;

        public void applicationMessageSentOrReceived() {
            lastApplicationMessageSentOrReceivedTime = System.currentTimeMillis();
        }
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
            this.endpoint = requireNonNull(endpoint);
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
