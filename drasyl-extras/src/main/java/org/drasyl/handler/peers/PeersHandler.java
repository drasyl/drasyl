/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.peers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.PathEvent;
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.internal.UnstableApi;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * A {@link io.netty.channel.ChannelHandler} that tracks all emitted {@link PathEvent}s and
 * generates a list of all peers retrievable through {@link #getPeers()}.
 */
@UnstableApi
public class PeersHandler extends ChannelInboundHandlerAdapter {
    private final Map<DrasylAddress, Peer> peers;

    PeersHandler(final Map<DrasylAddress, Peer> peers) {
        this.peers = requireNonNull(peers);
    }

    public PeersHandler() {
        this(new HashMap<>());
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (evt instanceof AddPathAndSuperPeerEvent) {
            final DrasylAddress address = ((AddPathAndSuperPeerEvent) evt).getAddress();
            final InetSocketAddress inetAddress = ((AddPathAndSuperPeerEvent) evt).getInetAddress();
            final long rtt = ((AddPathAndSuperPeerEvent) evt).getRtt();

            final Peer peer = new Peer(Role.SUPER, inetAddress, rtt);
            peers.put(address, peer);
        }
        else if (evt instanceof AddPathEvent) {
            final DrasylAddress address = ((AddPathEvent) evt).getAddress();
            final InetSocketAddress inetAddress = ((AddPathEvent) evt).getInetAddress();
            final long rtt = ((AddPathEvent) evt).getRtt();

            final Peer peer = new Peer(Role.DEFAULT, inetAddress, rtt);
            peers.put(address, peer);
        }
        else if (evt instanceof PathRttEvent) {
            final DrasylAddress address = ((PathRttEvent) evt).getAddress();
            final long rtt = ((PathRttEvent) evt).getRtt();

            final Peer peer = peers.get(address);
            if (peer != null) {
                peer.last(rtt);
            }
        }
        else if (evt instanceof RemoveSuperPeerAndPathEvent || evt instanceof RemovePathEvent) {
            peers.remove(((PathEvent) evt).getAddress());
        }

        ctx.fireUserEventTriggered(evt);
    }

    public PeersList getPeers() {
        return new PeersList(peers);
    }
}
