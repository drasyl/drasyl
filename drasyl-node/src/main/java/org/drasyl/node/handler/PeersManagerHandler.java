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
package org.drasyl.node.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInboundInvoker;
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.PathEvent;
import org.drasyl.handler.discovery.RemoveChildrenAndPathEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.node.event.Node;
import org.drasyl.node.event.NodeOfflineEvent;
import org.drasyl.node.event.NodeOnlineEvent;
import org.drasyl.node.event.Peer;
import org.drasyl.node.event.PeerDirectEvent;
import org.drasyl.node.event.PeerRelayEvent;
import org.drasyl.util.HashSetMultimap;
import org.drasyl.util.SetMultimap;

import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * This handler track received {@link PathEvent}s and will contain an internal state of all peers,
 * super peers, children, and available paths.
 * <p>
 * This handler will emit...
 * <ul>
 * <li>...a {@link org.drasyl.node.event.PeerDirectEvent} for each peer with at least one available path.
 * <li>...a {@link org.drasyl.node.event.PeerRelayEvent} when the last path to a peer becomes unavailable.
 * <li>...a {@link org.drasyl.node.event.NodeOnlineEvent} if this node is registered to at least one super peer.
 * <li>...a {@link org.drasyl.node.event.NodeOfflineEvent} when this node is not longer registered to any super peer.
 */
public class PeersManagerHandler extends ChannelInboundHandlerAdapter {
    private final SetMultimap<DrasylAddress, Object> paths;
    private final Set<DrasylAddress> children;
    private final Set<DrasylAddress> superPeers;
    private final Identity identity;

    @SuppressWarnings("java:S2384")
    PeersManagerHandler(final SetMultimap<DrasylAddress, Object> paths,
                        final Set<DrasylAddress> children,
                        final Set<DrasylAddress> superPeers,
                        final Identity identity) {
        this.paths = requireNonNull(paths);
        this.children = requireNonNull(children);
        this.superPeers = requireNonNull(superPeers);
        this.identity = requireNonNull(identity);
    }

    public PeersManagerHandler(final Identity identity) {
        this(new HashSetMultimap<>(), new HashSet<>(), new HashSet<>(), identity);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (evt instanceof PathEvent) {
            final PathEvent e = (PathEvent) evt;
            if (e instanceof AddPathEvent) {
                addPath(ctx, e.getAddress(), e.getPath());
            }
            else if (e instanceof RemovePathEvent) {
                removePath(ctx, e.getAddress(), e.getPath());
            }
            else if (e instanceof AddPathAndSuperPeerEvent) {
                addPathAndSuperPeer(ctx, e.getAddress(), e.getPath());
            }
            else if (e instanceof RemoveSuperPeerAndPathEvent) {
                removeSuperPeerAndPath(ctx, e.getAddress(), e.getPath());
            }
            else if (e instanceof AddPathAndChildrenEvent) {
                addPathAndChildren(ctx, e.getAddress(), e.getPath());
            }
            else if (e instanceof RemoveChildrenAndPathEvent) {
                removeChildrenAndPath(ctx, e.getAddress(), e.getPath());
            }
        }

        ctx.fireUserEventTriggered(evt);
    }

    private void addPath(final ChannelInboundInvoker ctx,
                         final DrasylAddress publicKey,
                         final Object path) {
        requireNonNull(publicKey);

        final boolean firstPath = paths.get(publicKey).isEmpty();
        if (paths.put(publicKey, path) && firstPath) {
            ctx.fireUserEventTriggered(PeerDirectEvent.of(Peer.of(publicKey)));
        }
    }

    private void removePath(final ChannelInboundInvoker ctx,
                            final DrasylAddress publicKey,
                            final Object path) {
        requireNonNull(publicKey);
        requireNonNull(path);

        if (paths.remove(publicKey, path) && paths.get(publicKey).isEmpty()) {
            ctx.fireUserEventTriggered(PeerRelayEvent.of(Peer.of(publicKey)));
        }
    }

    private void addPathAndSuperPeer(final ChannelInboundInvoker ctx,
                                     final DrasylAddress publicKey,
                                     final Object path) {
        requireNonNull(publicKey);
        requireNonNull(path);

        // path
        final boolean firstPath = paths.get(publicKey).isEmpty();
        if (paths.put(publicKey, path) && firstPath) {
            ctx.fireUserEventTriggered(PeerDirectEvent.of(Peer.of(publicKey)));
        }

        // role (super peer)
        final boolean firstSuperPeer = superPeers.isEmpty();
        if (superPeers.add(publicKey) && firstSuperPeer) {
            ctx.fireUserEventTriggered(NodeOnlineEvent.of(Node.of(identity)));
        }
    }

    private void removeSuperPeerAndPath(final ChannelInboundInvoker ctx,
                                        final DrasylAddress publicKey,
                                        final Object path) {
        requireNonNull(path);

        // role (super peer)
        if (superPeers.remove(publicKey) && superPeers.isEmpty()) {
            ctx.fireUserEventTriggered(NodeOfflineEvent.of(Node.of(identity)));
        }

        // path
        if (paths.remove(publicKey, path) && paths.get(publicKey).isEmpty()) {
            ctx.fireUserEventTriggered(PeerRelayEvent.of(Peer.of(publicKey)));
        }
    }

    private void addPathAndChildren(final ChannelInboundInvoker ctx,
                                    final DrasylAddress publicKey,
                                    final Object path) {
        requireNonNull(publicKey);
        requireNonNull(path);

        // path
        final boolean firstPath = paths.get(publicKey).isEmpty();
        if (paths.put(publicKey, path) && firstPath) {
            ctx.fireUserEventTriggered(PeerDirectEvent.of(Peer.of(publicKey)));
        }

        // role (children peer)
        children.add(publicKey);
    }

    private void removeChildrenAndPath(final ChannelInboundInvoker ctx,
                                       final DrasylAddress publicKey,
                                       final Object path) {
        requireNonNull(publicKey);
        requireNonNull(path);

        // path
        if (paths.remove(publicKey, path) && paths.get(publicKey).isEmpty()) {
            ctx.fireUserEventTriggered(PeerRelayEvent.of(Peer.of(publicKey)));
        }

        // role (children)
        children.remove(publicKey);
    }
}
