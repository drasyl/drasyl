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
package org.drasyl.handler.monitoring;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.PathEvent;
import org.drasyl.handler.discovery.RemoveChildrenAndPathEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Uses emitted {@link PathEvent}s to build the node's current world view of the overlay network.
 * Other handler can implement this call and retrieve the topology by calling {@link
 * #topology(ChannelHandlerContext)}.
 */
@SuppressWarnings("java:S118")
public abstract class TopologyHandler extends ChannelInboundHandlerAdapter {
    protected final Map<DrasylAddress, InetSocketAddress> superPeers;
    protected final Map<DrasylAddress, InetSocketAddress> childrenPeers;
    protected final Map<DrasylAddress, InetSocketAddress> peers;

    protected TopologyHandler(final Map<DrasylAddress, InetSocketAddress> superPeers,
                              final Map<DrasylAddress, InetSocketAddress> childrenPeers,
                              final Map<DrasylAddress, InetSocketAddress> peers) {
        this.superPeers = requireNonNull(superPeers);
        this.childrenPeers = requireNonNull(childrenPeers);
        this.peers = requireNonNull(peers);
    }

    protected TopologyHandler() {
        this(new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) throws Exception {
        if (evt instanceof PathEvent) {
            final PathEvent e = (PathEvent) evt;
            // super peers
            if (evt instanceof AddPathAndSuperPeerEvent) {
                superPeers.put(e.getAddress(), ((AddPathAndSuperPeerEvent) e).getInetAddress());
            }
            else if (evt instanceof RemoveSuperPeerAndPathEvent) {
                superPeers.remove(e.getAddress());
            }
            // children peers
            else if (evt instanceof AddPathAndChildrenEvent) {
                childrenPeers.put(e.getAddress(), ((AddPathAndChildrenEvent) e).getInetAddress());
            }
            else if (evt instanceof RemoveChildrenAndPathEvent) {
                childrenPeers.remove(e.getAddress());
            }
            // "normal" peers
            else if (evt instanceof AddPathEvent) {
                peers.put(e.getAddress(), ((AddPathEvent) e).getInetAddress());
            }
            else if (evt instanceof RemovePathEvent) {
                peers.remove(e.getAddress());
            }
        }

        ctx.fireUserEventTriggered(evt);
    }

    protected Topology topology(final ChannelHandlerContext ctx) {
        return new Topology((DrasylAddress) ctx.channel().localAddress(), superPeers, childrenPeers, peers);
    }

    public static class Topology {
        private final DrasylAddress address;
        private final Map<DrasylAddress, InetSocketAddress> superPeers;
        private final Map<DrasylAddress, InetSocketAddress> childrenPeers;
        private final Map<DrasylAddress, InetSocketAddress> peers;

        public Topology(final DrasylAddress address,
                        final Map<DrasylAddress, InetSocketAddress> superPeers,
                        final Map<DrasylAddress, InetSocketAddress> childrenPeers,
                        final Map<DrasylAddress, InetSocketAddress> peers) {
            this.address = requireNonNull(address);
            this.superPeers = requireNonNull(superPeers);
            this.childrenPeers = requireNonNull(childrenPeers);
            this.peers = requireNonNull(peers);
        }

        public DrasylAddress address() {
            return address;
        }

        public Map<DrasylAddress, InetSocketAddress> superPeers() {
            return Map.copyOf(superPeers);
        }

        public Map<DrasylAddress, InetSocketAddress> childrenPeers() {
            return Map.copyOf(childrenPeers);
        }

        public Map<DrasylAddress, InetSocketAddress> peers() {
            return Map.copyOf(peers);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Topology topology = (Topology) o;
            return Objects.equals(address, topology.address) && Objects.equals(superPeers, topology.superPeers) && Objects.equals(childrenPeers, topology.childrenPeers) && Objects.equals(peers, topology.peers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, superPeers, childrenPeers, peers);
        }

        @Override
        public String toString() {
            return "Topology{" +
                    "address=" + address +
                    ", superPeers=" + superPeers.size() +
                    ", childrenPeers=" + childrenPeers.size() +
                    ", peers=" + peers.size() +
                    '}';
        }
    }
}
