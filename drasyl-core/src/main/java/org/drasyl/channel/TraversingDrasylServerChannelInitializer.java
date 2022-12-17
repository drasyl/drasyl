/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.channel;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import org.drasyl.handler.remote.internet.InternetDiscoveryChildrenHandler;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * A {@link ChannelInitializer} for {@link DrasylServerChannel}s that tries to traverse (if any)
 * NATs with a fallback to relaying all messages through super peers.
 */
public class TraversingDrasylServerChannelInitializer extends RelayOnlyDrasylServerChannelInitializer {
    public static final int PING_COMMUNICATION_TIMEOUT_MILLIS = 60_000;
    private final int pingCommunicationTimeoutMillis;

    /**
     * @param identity                       own identity
     * @param udpServerGroup                 the {@link EventLoopGroup} the underlying udp server
     *                                       should run on
     * @param bindAddress                    address the UDP server will bind to. Default value:
     *                                       0.0.0.0:{@link #BIND_PORT}
     * @param networkId                      the network we belong to. Default value:
     *                                       {@link #NETWORK_ID}
     * @param superPeers                     list of super peers we register to. Default value:
     *                                       {@link #SUPER_PEERS}
     * @param protocolArmEnabled             if {@code true} all control plane messages will be
     *                                       encrypted/authenticated. Default value: {@code true}
     * @param pingIntervalMillis             interval in millis between a ping. Default value:
     *                                       {@link #PING_INTERVAL_MILLIS}
     * @param pingTimeoutMillis              time in millis without ping response before a peer is
     *                                       assumed as unreachable. Default value:
     *                                       {@link #PING_TIMEOUT_MILLIS}
     * @param maxTimeOffsetMillis            time millis offset of received messages' timestamp
     *                                       before discarding them. Default value:
     *                                       {@link #MAX_TIME_OFFSET_MILLIS}
     * @param maxPeers                       maximum number of peers to which a traversed connection
     *                                       should be maintained at the same time. Default value:
     *                                       {@link #MAX_PEERS}
     * @param pingCommunicationTimeoutMillis time in millis a traversed connection to a peer will be
     *                                       discarded without application traffic. Default value:
     *                                       {@link #PING_COMMUNICATION_TIMEOUT_MILLIS}
     */
    @SuppressWarnings("java:S107")
    public TraversingDrasylServerChannelInitializer(final Identity identity,
                                                    final EventLoopGroup udpServerGroup,
                                                    final InetSocketAddress bindAddress,
                                                    final int networkId,
                                                    final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                                    final boolean protocolArmEnabled,
                                                    final int pingIntervalMillis,
                                                    final int pingTimeoutMillis,
                                                    final int maxTimeOffsetMillis,
                                                    final int maxPeers,
                                                    final int pingCommunicationTimeoutMillis) {
        super(identity, udpServerGroup, bindAddress, networkId, superPeers, protocolArmEnabled, pingIntervalMillis, pingTimeoutMillis, maxTimeOffsetMillis, maxPeers);
        this.pingCommunicationTimeoutMillis = pingCommunicationTimeoutMillis;
    }

    /**
     * Creates a new channel initializer with default values for {@code pingIntervalMillis},
     * {@code pingTimeoutMillis}, {@code maxTimeOffsetMillis}, {@code maxPeers}, and
     * {@code pingCommunicationTimeoutMillis}.
     *
     * @param identity           own identity
     * @param udpServerGroup     the {@link EventLoopGroup} the underlying udp server should run
     *                           on
     * @param bindAddress        address the UDP server will bind to. Default value:
     *                           0.0.0.0:{@link #BIND_PORT}
     * @param networkId          the network we belong to. Default value: {@link #NETWORK_ID}
     * @param superPeers         list of super peers we register to. Default value:
     *                           {@link #SUPER_PEERS}
     * @param protocolArmEnabled if {@code true} all control plane messages will be
     *                           encrypted/authenticated. Default value: {@code true}
     */
    public TraversingDrasylServerChannelInitializer(final Identity identity,
                                                    final EventLoopGroup udpServerGroup,
                                                    final InetSocketAddress bindAddress,
                                                    final int networkId,
                                                    final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                                    final boolean protocolArmEnabled) {
        this(identity, udpServerGroup, bindAddress, networkId, superPeers, protocolArmEnabled, PING_INTERVAL_MILLIS, PING_TIMEOUT_MILLIS, MAX_TIME_OFFSET_MILLIS, MAX_PEERS, PING_COMMUNICATION_TIMEOUT_MILLIS);
    }

    /**
     * Creates a new channel initializer with default values for {@code pingIntervalMillis},
     * {@code pingTimeoutMillis}, {@code maxTimeOffsetMillis}, {@code maxPeers},
     * {@code pingCommunicationTimeoutMillis}, and enabled control plane message arming.
     *
     * @param identity       own identity
     * @param udpServerGroup the {@link EventLoopGroup} the underlying udp server should run on
     * @param bindAddress    address the UDP server will bind to. Default value:
     *                       0.0.0.0:{@link #BIND_PORT}
     * @param networkId      the network we belong to. Default value: {@link #NETWORK_ID}
     * @param superPeers     list of super peers we register to. Default value:
     *                       {@link #SUPER_PEERS}
     */
    @SuppressWarnings("unused")
    public TraversingDrasylServerChannelInitializer(final Identity identity,
                                                    final EventLoopGroup udpServerGroup,
                                                    final InetSocketAddress bindAddress,
                                                    final int networkId,
                                                    final Map<IdentityPublicKey, InetSocketAddress> superPeers) {
        this(identity, udpServerGroup, bindAddress, networkId, superPeers, true, PING_INTERVAL_MILLIS, PING_TIMEOUT_MILLIS, MAX_TIME_OFFSET_MILLIS, MAX_PEERS, PING_COMMUNICATION_TIMEOUT_MILLIS);
    }

    /**
     * Creates a new channel initializer with default values for {@code networkId},
     * {@code superPeers}, {@code pingIntervalMillis}, {@code pingTimeoutMillis},
     * {@code maxTimeOffsetMillis}, {@code maxPeers}, {@code pingCommunicationTimeoutMillis}, and
     * enabled control plane message arming.
     *
     * @param identity       own identity
     * @param udpServerGroup the {@link EventLoopGroup} the underlying udp server should run on
     * @param bindAddress    address the UDP server will bind to. Default value:
     *                       0.0.0.0:{@link #BIND_PORT}
     */
    @SuppressWarnings("unused")
    public TraversingDrasylServerChannelInitializer(final Identity identity,
                                                    final EventLoopGroup udpServerGroup,
                                                    final InetSocketAddress bindAddress) {
        this(identity, udpServerGroup, bindAddress, NETWORK_ID, SUPER_PEERS, true, PING_INTERVAL_MILLIS, PING_TIMEOUT_MILLIS, MAX_TIME_OFFSET_MILLIS, MAX_PEERS, PING_COMMUNICATION_TIMEOUT_MILLIS);
    }

    /**
     * Creates a new channel initializer with default values for {@code networkId},
     * {@code superPeers}, {@code pingIntervalMillis}, {@code pingTimeoutMillis},
     * {@code maxTimeOffsetMillis}, {@code maxPeers}, {@code pingCommunicationTimeoutMillis}, and
     * enabled control plane message arming.
     *
     * @param identity       own identity
     * @param udpServerGroup the {@link EventLoopGroup} the underlying udp server should run on
     * @param bindPort       port the UDP server will bind to. Default value: {@link #BIND_PORT}
     */
    @SuppressWarnings("unused")
    public TraversingDrasylServerChannelInitializer(final Identity identity,
                                                    final EventLoopGroup udpServerGroup,
                                                    final int bindPort) {
        this(identity, udpServerGroup, new InetSocketAddress(bindPort), NETWORK_ID, SUPER_PEERS, true, PING_INTERVAL_MILLIS, PING_TIMEOUT_MILLIS, MAX_TIME_OFFSET_MILLIS, MAX_PEERS, PING_COMMUNICATION_TIMEOUT_MILLIS);
    }

    /**
     * Creates a new channel initializer with default values for {@code bindPort},
     * {@code networkId}, {@code superPeers}, {@code pingIntervalMillis}, {@code pingTimeoutMillis},
     * {@code maxTimeOffsetMillis}, {@code maxPeers}, {@code pingCommunicationTimeoutMillis}, and
     * enabled control plane message arming.
     *
     * @param identity       own identity
     * @param udpServerGroup the {@link EventLoopGroup} the underlying udp server should run on
     */
    @SuppressWarnings("unused")
    public TraversingDrasylServerChannelInitializer(final Identity identity,
                                                    final EventLoopGroup udpServerGroup) {
        this(identity, udpServerGroup, new InetSocketAddress(BIND_PORT), NETWORK_ID, SUPER_PEERS, true, PING_INTERVAL_MILLIS, PING_TIMEOUT_MILLIS, MAX_TIME_OFFSET_MILLIS, MAX_PEERS, PING_COMMUNICATION_TIMEOUT_MILLIS);
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        super.initChannel(ch);

        ch.pipeline().replace(InternetDiscoveryChildrenHandler.class, null, new TraversingInternetDiscoveryChildrenHandler(networkId, identity.getIdentityPublicKey(), identity.getIdentitySecretKey(), identity.getProofOfWork(), 0, pingIntervalMillis, pingTimeoutMillis, maxTimeOffsetMillis, superPeers, pingCommunicationTimeoutMillis, maxPeers));
    }
}
