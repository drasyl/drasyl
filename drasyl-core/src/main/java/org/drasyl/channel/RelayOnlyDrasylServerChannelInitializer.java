package org.drasyl.channel;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import org.drasyl.handler.remote.ApplicationMessageToPayloadCodec;
import org.drasyl.handler.remote.ByteToRemoteMessageCodec;
import org.drasyl.handler.remote.InvalidProofOfWorkFilter;
import org.drasyl.handler.remote.OtherNetworkFilter;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.crypto.ProtocolArmHandler;
import org.drasyl.handler.remote.crypto.UnarmedMessageDecoder;
import org.drasyl.handler.remote.internet.InternetDiscoveryChildrenHandler;
import org.drasyl.handler.remote.internet.UnconfirmedAddressResolveHandler;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;

import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ChannelInitializer} for {@link DrasylServerChannel}s that relays all messages through
 * super peers.
 */
public class RelayOnlyDrasylServerChannelInitializer extends ChannelInitializer<DrasylServerChannel> {
    public static final int BIND_PORT = 22527;
    public static final int NETWORK_ID = 1;
    public static final Map<IdentityPublicKey, InetSocketAddress> SUPER_PEERS = Map.of(
            IdentityPublicKey.of("c0900bcfabc493d062ecd293265f571edb70b85313ba4cdda96c9f77163ba62d"), new InetSocketAddress("sp-fra1.drasyl.org", 22527),
            IdentityPublicKey.of("5b4578909bf0ad3565bb5faf843a9f68b325dd87451f6cb747e49d82f6ce5f4c"), new InetSocketAddress("sp-nbg2.drasyl.org", 22527)
    );
    public static final int PING_INTERVAL_MILLIS = 5_000;
    public static final int PING_TIMEOUT_MILLIS = 30_000;
    public static final int MAX_TIME_OFFSET_MILLIS = 60_000;
    public static final int MAX_PEERS = 100;
    protected final Identity identity;
    protected final InetSocketAddress bindAddress;
    protected final int networkId;
    protected final Map<IdentityPublicKey, InetSocketAddress> superPeers;
    protected final boolean protocolArmEnabled;
    protected final int pingIntervalMillis;
    protected final int pingTimeoutMillis;
    protected final int maxTimeOffsetMillis;
    protected final int maxPeers;

    /**
     * @param identity            own identity
     * @param bindAddress         address the UDP server will bind to. Default value:
     *                            0.0.0.0:{@link #BIND_PORT}
     * @param networkId           the network we belong to. Default value: {@link #NETWORK_ID}
     * @param superPeers          list of super peers we register to. Default value:
     *                            {@link #SUPER_PEERS}
     * @param protocolArmEnabled  if {@code true} all control plane messages will be
     *                            encrypted/authenticated. Default value: {@code true}
     * @param pingIntervalMillis  interval in millis between a ping. Default value:
     *                            {@link #PING_INTERVAL_MILLIS}
     * @param pingTimeoutMillis   time in millis without ping response before a peer is assumed as
     *                            unreachable. Default value: {@link #PING_TIMEOUT_MILLIS}
     * @param maxTimeOffsetMillis time millis offset of received messages' timestamp before
     *                            discarding them. Default value: {@link #MAX_TIME_OFFSET_MILLIS}
     * @param maxPeers            maximum number of peers to which a traversed connection should be
     *                            maintained at the same time. Default value: {@link #MAX_PEERS}
     */
    @SuppressWarnings("java:S107")
    public RelayOnlyDrasylServerChannelInitializer(final Identity identity,
                                                   final InetSocketAddress bindAddress,
                                                   final int networkId,
                                                   final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                                   final boolean protocolArmEnabled,
                                                   final int pingIntervalMillis,
                                                   final int pingTimeoutMillis,
                                                   final int maxTimeOffsetMillis,
                                                   final int maxPeers) {
        this.identity = requireNonNull(identity);
        this.bindAddress = requireNonNull(bindAddress);
        this.networkId = networkId;
        this.superPeers = requireNonNull(superPeers);
        this.protocolArmEnabled = protocolArmEnabled;
        this.pingIntervalMillis = pingIntervalMillis;
        this.pingTimeoutMillis = pingTimeoutMillis;
        this.maxTimeOffsetMillis = maxTimeOffsetMillis;
        this.maxPeers = maxPeers;
    }

    /**
     * Creates a new channel initializer with default values for {@code pingIntervalMillis},
     * {@code pingTimeoutMillis}, {@code maxTimeOffsetMillis}, and {@code maxPeers}.
     *
     * @param identity           own identity
     * @param bindAddress        address the UDP server will bind to. Default value:
     *                           0.0.0.0:{@link #BIND_PORT}
     * @param networkId          the network we belong to. Default value: {@link #NETWORK_ID}
     * @param superPeers         list of super peers we register to. Default value:
     *                           {@link #SUPER_PEERS}
     * @param protocolArmEnabled if {@code true} all control plane messages will be
     *                           encrypted/authenticated. Default value: {@code true}
     */
    @SuppressWarnings("unused")
    public RelayOnlyDrasylServerChannelInitializer(final Identity identity,
                                                   final InetSocketAddress bindAddress,
                                                   final int networkId,
                                                   final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                                   final boolean protocolArmEnabled) {
        this(identity, bindAddress, networkId, superPeers, protocolArmEnabled, PING_INTERVAL_MILLIS, PING_TIMEOUT_MILLIS, MAX_TIME_OFFSET_MILLIS, MAX_PEERS);
    }

    /**
     * Creates a new channel initializer with default values for {@code pingIntervalMillis},
     * {@code pingTimeoutMillis}, {@code maxTimeOffsetMillis}, {@code maxPeers}, and enabled control
     * plane message arming.
     *
     * @param identity    own identity
     * @param bindAddress address the UDP server will bind to. Default value:
     *                    0.0.0.0:{@link #BIND_PORT}
     * @param networkId   the network we belong to. Default value: {@link #NETWORK_ID}
     * @param superPeers  list of super peers we register to. Default value: {@link #SUPER_PEERS}
     */
    @SuppressWarnings("unused")
    public RelayOnlyDrasylServerChannelInitializer(final Identity identity,
                                                   final InetSocketAddress bindAddress,
                                                   final int networkId,
                                                   final Map<IdentityPublicKey, InetSocketAddress> superPeers) {
        this(identity, bindAddress, networkId, superPeers, true, PING_INTERVAL_MILLIS, PING_TIMEOUT_MILLIS, MAX_TIME_OFFSET_MILLIS, MAX_PEERS);
    }

    /**
     * Creates a new channel initializer with default values for {@code networkId},
     * {@code superPeers}, {@code pingIntervalMillis}, {@code pingTimeoutMillis},
     * {@code maxTimeOffsetMillis}, {@code maxPeers}, and enabled control plane message arming.
     *
     * @param identity    own identity
     * @param bindAddress address the UDP server will bind to. Default value:
     *                    0.0.0.0:{@link #BIND_PORT}
     */
    @SuppressWarnings("unused")
    public RelayOnlyDrasylServerChannelInitializer(final Identity identity,
                                                   final InetSocketAddress bindAddress) {
        this(identity, bindAddress, NETWORK_ID, SUPER_PEERS, true, PING_INTERVAL_MILLIS, PING_TIMEOUT_MILLIS, MAX_TIME_OFFSET_MILLIS, MAX_PEERS);
    }

    /**
     * Creates a new channel initializer with default values for {@code networkId},
     * {@code superPeers}, {@code pingIntervalMillis}, {@code pingTimeoutMillis},
     * {@code maxTimeOffsetMillis}, {@code maxPeers}, and enabled control plane message arming.
     *
     * @param identity own identity
     * @param bindPort port the UDP server will bind to. Default value: {@link #BIND_PORT}
     */
    @SuppressWarnings("unused")
    public RelayOnlyDrasylServerChannelInitializer(final Identity identity,
                                                   final int bindPort) {
        this(identity, new InetSocketAddress(bindPort), NETWORK_ID, SUPER_PEERS, true, PING_INTERVAL_MILLIS, PING_TIMEOUT_MILLIS, MAX_TIME_OFFSET_MILLIS, MAX_PEERS);
    }

    /**
     * Creates a new channel initializer with default values for {@code bindPort},
     * {@code networkId}, {@code superPeers}, {@code pingIntervalMillis}, {@code pingTimeoutMillis},
     * {@code maxTimeOffsetMillis}, {@code maxPeers}, and enabled control plane message arming.
     *
     * @param identity own identity
     */
    @SuppressWarnings("unused")
    public RelayOnlyDrasylServerChannelInitializer(final Identity identity) {
        this(identity, new InetSocketAddress(BIND_PORT), NETWORK_ID, SUPER_PEERS, true, PING_INTERVAL_MILLIS, PING_TIMEOUT_MILLIS, MAX_TIME_OFFSET_MILLIS, MAX_PEERS);
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new UdpServer(bindAddress));
        p.addLast(new ByteToRemoteMessageCodec());
        p.addLast(new OtherNetworkFilter(networkId));
        p.addLast(new InvalidProofOfWorkFilter());
        if (protocolArmEnabled) {
            p.addLast(new ProtocolArmHandler(identity, maxPeers));
        }
        else {
            p.addLast(new UnarmedMessageDecoder());
        }
        p.addLast(new UnconfirmedAddressResolveHandler());
        p.addLast(new InternetDiscoveryChildrenHandler(networkId, identity.getIdentityPublicKey(), identity.getIdentitySecretKey(), identity.getProofOfWork(), 0, pingIntervalMillis, pingTimeoutMillis, maxTimeOffsetMillis, superPeers));
        p.addLast(new ApplicationMessageToPayloadCodec(networkId, identity.getIdentityPublicKey(), identity.getProofOfWork()));
    }
}
