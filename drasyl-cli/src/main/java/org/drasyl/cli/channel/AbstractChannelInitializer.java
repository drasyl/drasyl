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
package org.drasyl.cli.channel;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.handler.SuperPeerTimeoutHandler;
import org.drasyl.handler.remote.ApplicationMessageToPayloadCodec;
import org.drasyl.handler.remote.ByteToRemoteMessageCodec;
import org.drasyl.handler.remote.InvalidProofOfWorkFilter;
import org.drasyl.handler.remote.OtherNetworkFilter;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.crypto.ProtocolArmHandler;
import org.drasyl.handler.remote.crypto.UnarmedMessageDecoder;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;

import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requirePositive;

public abstract class AbstractChannelInitializer extends ChannelInitializer<DrasylServerChannel> {
    public static final int PING_INTERVAL_MILLIS = 5_000;
    public static final int PING_TIMEOUT_MILLIS = 30_000;
    public static final int MAX_TIME_OFFSET_MILLIS = 60_000;
    public static final int PING_COMMUNICATION_TIMEOUT_MILLIS = 60_000;
    public static final int MAX_PEERS = 10;
    private final Identity identity;
    private final InetSocketAddress bindAddress;
    private final int networkId;
    private final long onlineTimeoutMillis;
    private final Map<IdentityPublicKey, InetSocketAddress> superPeers;
    private final boolean protocolArmEnabled;

    @SuppressWarnings("java:S107")
    protected AbstractChannelInitializer(final Identity identity,
                                         final InetSocketAddress bindAddress,
                                         final int networkId,
                                         final long onlineTimeoutMillis,
                                         final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                         final boolean protocolArmEnabled) {
        this.identity = requireNonNull(identity);
        this.bindAddress = requireNonNull(bindAddress);
        this.networkId = networkId;
        this.onlineTimeoutMillis = requirePositive(onlineTimeoutMillis);
        this.superPeers = requireNonNull(superPeers);
        this.protocolArmEnabled = protocolArmEnabled;
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new UdpServer(bindAddress));
        p.addLast(new ByteToRemoteMessageCodec());
        p.addLast(new OtherNetworkFilter(networkId));
        p.addLast(new InvalidProofOfWorkFilter());
        if (protocolArmEnabled) {
            p.addLast(new ProtocolArmHandler(identity, MAX_PEERS));
        }
        else {
            p.addLast(new UnarmedMessageDecoder());
        }
        p.addLast(new TraversingInternetDiscoveryChildrenHandler(networkId, identity.getIdentityPublicKey(), identity.getIdentitySecretKey(), identity.getProofOfWork(), 0, PING_INTERVAL_MILLIS, PING_TIMEOUT_MILLIS, MAX_TIME_OFFSET_MILLIS, superPeers, PING_COMMUNICATION_TIMEOUT_MILLIS, MAX_PEERS));
        p.addLast(new ApplicationMessageToPayloadCodec(networkId, identity.getIdentityPublicKey(), identity.getProofOfWork()));
        p.addLast(new SuperPeerTimeoutHandler(onlineTimeoutMillis));
    }
}

