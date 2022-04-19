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
package org.drasyl.jtasklet.channel;

import io.netty.channel.ChannelInitializer;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.cli.handler.SuperPeerTimeoutHandler;
import org.drasyl.handler.remote.ApplicationMessageToPayloadCodec;
import org.drasyl.handler.remote.ByteToRemoteMessageCodec;
import org.drasyl.handler.remote.InvalidProofOfWorkFilter;
import org.drasyl.handler.remote.OtherNetworkFilter;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.UnresolvedOverlayMessageHandler;
import org.drasyl.handler.remote.crypto.ProtocolArmHandler;
import org.drasyl.handler.remote.crypto.UnarmedMessageDecoder;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler;
import org.drasyl.handler.remote.internet.UnconfirmedAddressResolveHandler;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.handler.PathEventsFilter;
import org.drasyl.util.Worm;

import java.io.PrintStream;
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
    protected final Identity identity;
    protected final InetSocketAddress bindAddress;
    protected final int networkId;
    protected final long onlineTimeoutMillis;
    private final Map<IdentityPublicKey, InetSocketAddress> superPeers;
    protected final boolean protocolArmEnabled;
    protected final PrintStream err;
    private final Worm<Integer> exitCode;

    @SuppressWarnings("java:S107")
    protected AbstractChannelInitializer(final Identity identity,
                                         final InetSocketAddress bindAddress,
                                         final int networkId,
                                         final long onlineTimeoutMillis,
                                         final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                         final boolean protocolArmEnabled,
                                         final PrintStream err,
                                         final Worm<Integer> exitCode) {
        this.identity = requireNonNull(identity);
        this.bindAddress = requireNonNull(bindAddress);
        this.networkId = networkId;
        this.onlineTimeoutMillis = requirePositive(onlineTimeoutMillis);
        this.superPeers = requireNonNull(superPeers);
        this.protocolArmEnabled = protocolArmEnabled;
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) throws Exception {
        firstStage(ch);
        ipStage(ch);
        serializationStage(ch);
        gatekeeperStage(ch);
        discoveryStage(ch);
        lastStage(ch);
    }

    @SuppressWarnings("java:S1172")
    protected void firstStage(final DrasylServerChannel ch) {
        // NOOP
    }

    protected void ipStage(final DrasylServerChannel ch) {
        ch.pipeline().addLast(new UdpServer(bindAddress));
    }

    protected void serializationStage(final DrasylServerChannel ch) {
        ch.pipeline().addLast(new ByteToRemoteMessageCodec());
    }

    protected void gatekeeperStage(final DrasylServerChannel ch) {
        ch.pipeline().addLast(new OtherNetworkFilter(networkId));
        ch.pipeline().addLast(new InvalidProofOfWorkFilter());
        if (protocolArmEnabled) {
            ch.pipeline().addLast(new ProtocolArmHandler(identity, MAX_PEERS));
        }
        else {
            ch.pipeline().addLast(new UnarmedMessageDecoder());
        }
    }

    protected void discoveryStage(final DrasylServerChannel ch) {
        ch.pipeline().addLast(new UnresolvedOverlayMessageHandler());
        ch.pipeline().addLast(new UnconfirmedAddressResolveHandler());
        ch.pipeline().addLast(new TraversingInternetDiscoveryChildrenHandler(networkId, identity.getIdentityPublicKey(), identity.getIdentitySecretKey(), identity.getProofOfWork(), 0, PING_INTERVAL_MILLIS, PING_TIMEOUT_MILLIS, MAX_TIME_OFFSET_MILLIS, superPeers, PING_COMMUNICATION_TIMEOUT_MILLIS, MAX_PEERS));
        ch.pipeline().addLast(new ApplicationMessageToPayloadCodec(networkId, identity.getIdentityPublicKey(), identity.getProofOfWork()));
    }

    protected void lastStage(final DrasylServerChannel ch) throws Exception {
        ch.pipeline().addLast(new SuperPeerTimeoutHandler(onlineTimeoutMillis));
        ch.pipeline().addLast(new PathEventsFilter());
        ch.pipeline().addLast(new PrintAndExitOnExceptionHandler(err, exitCode));
    }
}

