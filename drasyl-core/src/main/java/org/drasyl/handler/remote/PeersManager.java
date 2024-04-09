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

import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.HashSetMultimap;
import org.drasyl.util.SetMultimap;

import java.net.InetSocketAddress;
import java.nio.channels.UnresolvedAddressException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requirePositive;

public class PeersManager {
    private final Map<DrasylAddress, PeerPath> paths = new HashMap<>();
    private final SetMultimap<DrasylAddress, Class<?>> ids = new HashSetMultimap<>();
    private final long helloTimeoutMillis;

    public PeersManager(final long helloTimeoutMillis) {
        this.helloTimeoutMillis = requirePositive(helloTimeoutMillis);
    }

    public PeersManager() {
        this(30_000);
    }

    public boolean addPath(final DrasylAddress peer,
                           final Class<?> id,
                           final InetSocketAddress endpoint,
                           final short priority) {
        if (endpoint.isUnresolved()) {
            throw new UnresolvedAddressException();
        }

        if (!ids.put(peer, id)) {
            return false;
        }

        final PeerPath newPath = new PeerPath(id, endpoint, priority);

        final PeerPath head = paths.get(peer);
        if (head == null || head.priority > newPath.priority) {
            // create head
            newPath.next = null;
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

    public boolean removePath(final DrasylAddress peer,
                              final Class<?> id) {
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

    public void removePaths(final Class<?> id) {
        final Set<DrasylAddress> peers = Set.copyOf(ids.keySet());
        peers.forEach(peer -> removePath(peer, id));
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

    public InetSocketAddress getEndpoint(final DrasylAddress peer, final Class<?> id) {
        final PeerPath path = getPath(peer, id);
        if (path != null) {
            return path.endpoint;
        }
        return null;
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

    public void inboundHelloOccurred(final DrasylAddress peer, final Class<?> id) {
        final PeerPath path = getPath(peer, id);
        path.inboundHelloOccurred();
    }

    public boolean isStale(final DrasylAddress peer, final Class<?> id) {
        return lastInboundHelloTime(peer, id) < System.currentTimeMillis() - helloTimeoutMillis;
    }

    public long lastInboundHelloTime(final DrasylAddress peer, final Class<?> id) {
        final PeerPath path = getPath(peer, id);
        return path.lastInboundHelloTime;
    }

    static class PeerPath {
        private final Class<?> id;
        private final InetSocketAddress endpoint;
        private final short priority;
        private PeerPath next;
        private long lastInboundHelloTime;

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

        public void inboundHelloOccurred() {
            lastInboundHelloTime = System.currentTimeMillis();
        }
    }
}
