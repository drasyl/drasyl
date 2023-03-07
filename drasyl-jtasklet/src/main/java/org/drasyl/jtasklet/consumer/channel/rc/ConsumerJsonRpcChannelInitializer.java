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
package org.drasyl.jtasklet.consumer.channel.rc;

import io.netty.channel.EventLoopGroup;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.channel.AbstractChannelInitializer;
import org.drasyl.handler.PeersRttHandler;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.consumer.channel.ProactiveDirectConnectionHandler;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class ConsumerJsonRpcChannelInitializer extends AbstractChannelInitializer {
    private final List<IdentityPublicKey> peers;

    @SuppressWarnings("java:S107")
    public ConsumerJsonRpcChannelInitializer(final Identity identity,
                                             final EventLoopGroup udpServerGroup,
                                             final InetSocketAddress bindAddress,
                                             final int networkId,
                                             final long onlineTimeoutMillis,
                                             final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                             final boolean protocolArmEnabled,
                                             final List<IdentityPublicKey> peers) {
        super(identity, udpServerGroup, bindAddress, networkId, onlineTimeoutMillis, superPeers, protocolArmEnabled);
        this.peers = requireNonNull(peers);
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        super.initChannel(ch);

        ch.pipeline().addLast(new ProactiveDirectConnectionHandler(peers));
        ch.pipeline().addLast(new PeersRttHandler(2_500L));
    }
}