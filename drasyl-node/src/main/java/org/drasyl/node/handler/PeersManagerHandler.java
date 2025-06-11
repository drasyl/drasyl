/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
import org.drasyl.channel.rs.RustDrasylServerChannel;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.event.Node;
import org.drasyl.node.event.NodeOfflineEvent;
import org.drasyl.node.event.NodeOnlineEvent;
import org.drasyl.node.event.Peer;
import org.drasyl.node.event.PeerDirectEvent;
import org.drasyl.node.event.PeerRelayEvent;
import org.drasyl.util.internal.UnstableApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_peers_list;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_peers_list_peer_pk;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_peers_list_peer_reachable;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_peers_list_peer_super_peer;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_peers_list_peers;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_peers_list_peers_free;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_peers_list_peers_len;
import static org.drasyl.channel.rs.Libdrasyl.ensureSuccess;

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
@UnstableApi
public class PeersManagerHandler extends ChannelInboundHandlerAdapter {
    private final Set<DrasylAddress> reachableChildren;
    private final Set<DrasylAddress> reachableSuperPeers;

    @SuppressWarnings("java:S2384")
    PeersManagerHandler(final Set<DrasylAddress> reachableChildren,
                        final Set<DrasylAddress> reachableSuperPeers) {
        this.reachableChildren = requireNonNull(reachableChildren);
        this.reachableSuperPeers = requireNonNull(reachableSuperPeers);
    }

    public PeersManagerHandler() {
        this(new HashSet<>(), new HashSet<>());
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        final RustDrasylServerChannel channel = (RustDrasylServerChannel) ctx.channel();

        ctx.executor().scheduleAtFixedRate(() -> {
            if (channel.isActive()) {
                final ByteBuffer peersListBuf = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);
                ensureSuccess(drasyl_node_peers_list(channel.bind, peersListBuf.array()));
                final long peersList = peersListBuf.getLong();
                final ByteBuffer peersBuf = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);
                ensureSuccess(drasyl_peers_list_peers(peersList, peersBuf.array()));
                final long peers = peersBuf.getLong();

                final long peersLen = drasyl_peers_list_peers_len(peers);
                for (int i = 0; i < peersLen; i++) {
                    final byte[] pkBytes = new byte[IdentityPublicKey.KEY_LENGTH_AS_BYTES];
                    ensureSuccess(drasyl_peers_list_peer_pk(peers, i, pkBytes));
                    final IdentityPublicKey publicKey = IdentityPublicKey.of(pkBytes);
                    final boolean isSuperPeer = ensureSuccess(drasyl_peers_list_peer_super_peer(peers, i)) == 1;
                    final boolean isReachable = ensureSuccess(drasyl_peers_list_peer_reachable(peers, i)) == 1;

                    if (isSuperPeer) {
                        if (isReachable) {
                            if (this.reachableSuperPeers.isEmpty()) {
                                ctx.fireUserEventTriggered(NodeOnlineEvent.of(Node.of(channel.identity())));
                            }
                            if (this.reachableSuperPeers.add(publicKey)) {
                                ctx.fireUserEventTriggered(PeerDirectEvent.of(Peer.of(publicKey)));
                            }
                        }
                        else {
                            if (this.reachableSuperPeers.remove(publicKey)) {
                                ctx.fireUserEventTriggered(PeerRelayEvent.of(Peer.of(publicKey)));

                                if (this.reachableSuperPeers.isEmpty()) {
                                    ctx.fireUserEventTriggered(NodeOfflineEvent.of(Node.of(channel.identity())));
                                }
                            }
                        }
                    }
                    else {
                        if (isReachable) {
                            if (this.reachableChildren.add(publicKey)) {
                                ctx.fireUserEventTriggered(PeerDirectEvent.of(Peer.of(publicKey)));
                            }
                        }
                        else {
                            if (this.reachableChildren.remove(publicKey)) {
                                ctx.fireUserEventTriggered(PeerRelayEvent.of(Peer.of(publicKey)));
                            }
                        }
                    }
                }

                ensureSuccess(drasyl_peers_list_peers_free(peers));
            }
            else {
                if (!this.reachableSuperPeers.isEmpty()) {
                    this.reachableSuperPeers.clear();
                    ctx.fireUserEventTriggered(NodeOfflineEvent.of(Node.of(channel.identity())));
                }
            }
        }, 0, 100, MILLISECONDS);
    }
}
