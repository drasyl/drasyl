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
package org.drasyl.handler.remote.internet;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.IdentityChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.remote.PeersManager.PathId;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.handler.remote.protocol.UniteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.network.NetworkUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Extends {@link InternetDiscoveryChildrenHandler} by performing a rendezvous initiated by one of
 * our super peers.
 *
 * @see TraversingInternetDiscoverySuperPeerHandler
 */
@UnstableApi
@SuppressWarnings("unchecked")
public class TraversingInternetDiscoveryChildrenHandler extends InternetDiscoveryChildrenHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TraversingInternetDiscoveryChildrenHandler.class);
    static final PathId PATH_ID = new PathId() {
        @Override
        public short priority() {
            return 95;
        }
    };
    private final Map<DrasylAddress, TraversingPeer> traversingPeers;

    @SuppressWarnings("java:S107")
    TraversingInternetDiscoveryChildrenHandler(final LongSupplier currentTime,
                                               final long initialPingDelayMillis,
                                               final Future<?> heartbeatDisposable,
                                               final Map<DrasylAddress, TraversingPeer> traversingPeers) {
        super(currentTime, initialPingDelayMillis, heartbeatDisposable);
        this.traversingPeers = requireNonNull(traversingPeers);
    }

    @SuppressWarnings("java:S107")
    public TraversingInternetDiscoveryChildrenHandler() {
        super();
        this.traversingPeers = new HashMap<>();
    }

    /*
     * Channel Events
     */

    @Override
    public void channelRead(final ChannelHandlerContext ctx,
                            final Object msg) {
        if (isUniteMessageFromSuperPeer(ctx, msg)) {
            final InetAddressedMessage<UniteMessage> addressedMsg = (InetAddressedMessage<UniteMessage>) msg;
            handleUniteMessage(ctx, addressedMsg.content());
        }
        else if (isHelloMessageFromTraversingPeer(ctx, msg)) {
            final InetAddressedMessage<HelloMessage> addressedMsg = (InetAddressedMessage<HelloMessage>) msg;
            handleHelloMessageFromTraversingPeer(ctx, addressedMsg.content(), addressedMsg.sender());
        }
        else if (isAcknowledgementMessageFromTraversingPeer(ctx, msg)) {
            final InetAddressedMessage<AcknowledgementMessage> addressedMsg = (InetAddressedMessage<AcknowledgementMessage>) msg;
            handleAcknowledgementMessageFromTraversingPeer(ctx, addressedMsg.content(), addressedMsg.sender());
        }
        else {
            super.channelRead(ctx, msg);
        }
    }

    /*
     * Traversing
     */

    @SuppressWarnings({ "java:S1067", "SuspiciousMethodCalls" })
    private boolean isUniteMessageFromSuperPeer(final ChannelHandlerContext ctx, final Object msg) {
        return msg instanceof InetAddressedMessage &&
                ((InetAddressedMessage<?>) msg).content() instanceof UniteMessage &&
                ctx.channel().localAddress().equals(((InetAddressedMessage<UniteMessage>) msg).content().getRecipient()) &&
                config(ctx).getSuperPeers().keySet().contains(((InetAddressedMessage<UniteMessage>) msg).content().getSender());
    }

    private void handleUniteMessage(final ChannelHandlerContext ctx, final UniteMessage msg) {
        final DrasylAddress address = msg.getAddress();
        final Set<InetSocketAddress> endpoints = msg.getEndpoints();
        LOG.debug("Got Unite for peer `{}` with endpoints `{}`.", address, endpoints);

        if (config(ctx).getMaxPeers() == 0 || config(ctx).getMaxPeers() > traversingPeers.size()) {
            final TraversingPeer existingTraversingPeer = traversingPeers.get(address);
            final TraversingPeer newTraversingPeer = new TraversingPeer(currentTime, endpoints);

            if (!Objects.equals(existingTraversingPeer, newTraversingPeer)) {
                // new peer or endpoints have changes -> send Hello
                LOG.debug("Try to reach peer `{}` at endpoints `{}`", address, endpoints);
                traversingPeers.put(address, newTraversingPeer);
                for (final InetSocketAddress inetAddress : newTraversingPeer.inetAddressCandidates()) {
                    writeHelloMessage(ctx, address, inetAddress, null);
                }
                ctx.flush();
            }
            else {
                LOG.debug("Do nothing, as we are already trying to reach the peer via received endpoints.");
            }
        }
        else {
            LOG.trace("Got Unite for peer `{}` with address `{}`. But we've already reached maximum number of traversed peers. Drop message.", address, endpoints, endpoints);
        }
    }

    @SuppressWarnings("java:S1067")
    private boolean isHelloMessageFromTraversingPeer(final ChannelHandlerContext ctx, final Object msg) {
        return msg instanceof InetAddressedMessage &&
                ((InetAddressedMessage<?>) msg).content() instanceof HelloMessage &&
                ctx.channel().localAddress().equals(((InetAddressedMessage<HelloMessage>) msg).content().getRecipient()) &&
                traversingPeers.containsKey(((InetAddressedMessage<HelloMessage>) msg).content().getSender()) &&
                Math.abs(currentTime.getAsLong() - (((InetAddressedMessage<HelloMessage>) msg).content()).getTime()) <= config(ctx).getMaxMessageAge().toMillis() &&
                ((InetAddressedMessage<HelloMessage>) msg).content().getChildrenTime() == 0;
    }

    private void handleHelloMessageFromTraversingPeer(final ChannelHandlerContext ctx,
                                                      final HelloMessage msg,
                                                      final InetSocketAddress inetAddress) {
        LOG.trace("Got Hello from traversing peer `{}` from address `{}`.", msg.getSender(), inetAddress);

        final TraversingPeer traversingPeer = traversingPeers.get(msg.getSender());

        // reply with Acknowledgement
        final AcknowledgementMessage acknowledgementMsg = AcknowledgementMessage.of(config(ctx).getNetworkId(), msg.getSender(), ((IdentityChannel) ctx.channel()).identity().getIdentityPublicKey(), ((IdentityChannel) ctx.channel()).identity().getProofOfWork(), msg.getTime());
        LOG.trace("Send Acknowledgement for traversing peer `{}` to `{}`.", msg::getSender, () -> inetAddress);
        ctx.writeAndFlush(new InetAddressedMessage<>(acknowledgementMsg, inetAddress));

        if (!config(ctx).getPeersManager().isReachable(ctx, msg.getSender(), PATH_ID) && traversingPeer.addInetAddressCandidate(inetAddress)) {
            // send Hello immediately to speed up traversal
            config(ctx).getPeersManager().helloMessageSent(msg.getSender(), PATH_ID);
            writeHelloMessage(ctx, msg.getSender(), inetAddress, null);
            ctx.flush();
        }
    }

    @SuppressWarnings("java:S1067")
    private boolean isAcknowledgementMessageFromTraversingPeer(final ChannelHandlerContext ctx, final Object msg) {
        return msg instanceof InetAddressedMessage<?> &&
                ((InetAddressedMessage<?>) msg).content() instanceof AcknowledgementMessage &&
                ctx.channel().localAddress().equals(((InetAddressedMessage<AcknowledgementMessage>) msg).content().getRecipient()) &&
                traversingPeers.containsKey(((InetAddressedMessage<AcknowledgementMessage>) msg).content().getSender()) &&
                Math.abs(currentTime.getAsLong() - (((InetAddressedMessage<AcknowledgementMessage>) msg).content()).getTime()) <= config(ctx).getMaxMessageAge().toMillis();
    }

    private void handleAcknowledgementMessageFromTraversingPeer(final ChannelHandlerContext ctx,
                                                                final AcknowledgementMessage msg,
                                                                final InetSocketAddress inetAddress) {
        final DrasylAddress publicKey = msg.getSender();
        final int rtt = (int) (currentTime.getAsLong() - msg.getTime());
        LOG.trace("Got Acknowledgement ({}ms RTT) from traversing peer `{}`.", () -> rtt, () -> publicKey);

        final TraversingPeer traversingPeer = traversingPeers.get(publicKey);
        traversingPeer.acknowledgementReceived(inetAddress);

        config(ctx).getPeersManager().addChildrenPath(ctx, publicKey, PATH_ID, inetAddress, rtt);
        config(ctx).getPeersManager().acknowledgementMessageReceived(publicKey, PATH_ID);
    }

    /*
     * Pinging
     */

    @Override
    void doHeartbeat(final ChannelHandlerContext ctx) {
        super.doHeartbeat(ctx);

        for (final Iterator<Entry<DrasylAddress, TraversingPeer>> it = traversingPeers.entrySet().iterator();
             it.hasNext(); ) {
            final Entry<DrasylAddress, TraversingPeer> entry = it.next();
            final DrasylAddress address = entry.getKey();
            final TraversingPeer traversingPeer = entry.getValue();

            final PeersManager peersManager = config(ctx).getPeersManager();
            if (!traversingPeer.isNew(ctx) && (!peersManager.hasApplicationTraffic(ctx, address) || !peersManager.isReachable(ctx, address, PATH_ID))) {
                LOG.trace("Traversing peer `{}` is stale. Remove from my neighbour list.", address);
                it.remove();
                peersManager.removeChildrenPath(ctx, address, PATH_ID);
            }
            else {
                // send Hello
                peersManager.helloMessageSent(address, PATH_ID);
                for (final InetSocketAddress inetAddress : traversingPeer.inetAddressCandidates()) {
                    writeHelloMessage(ctx, address, inetAddress, null);
                }
                ctx.flush();
            }
        }
    }

    @Override
    protected Set<InetSocketAddress> getPrivateAddresses() {
        final Set<InetAddress> addresses;
        if (bindAddress == null) {
            return Set.of();
        }
        if (bindAddress.getAddress().isAnyLocalAddress()) {
            // use all available addresses
            addresses = NetworkUtil.getAddresses();
        }
        else {
            // use given host
            addresses = Set.of(bindAddress.getAddress());
        }
        return addresses.stream().map(a -> new InetSocketAddress(a, bindAddress.getPort())).collect(Collectors.toSet());
    }

    static class TraversingPeer {
        private final LongSupplier currentTime;
        private final Set<InetSocketAddress> inetAddressCandidates;
        private final long firstHelloTime;
        private InetSocketAddress primaryInetAddress;

        @SuppressWarnings("java:S107")
        TraversingPeer(final LongSupplier currentTime,
                       final Set<InetSocketAddress> inetAddressCandidates) {
            this.currentTime = requireNonNull(currentTime);
            this.inetAddressCandidates = requireNonNull(inetAddressCandidates);
            firstHelloTime = currentTime.getAsLong();
        }

        public boolean addInetAddressCandidate(final InetSocketAddress inetAddress) {
            return inetAddressCandidates.add(inetAddress);
        }

        public Set<InetSocketAddress> inetAddressCandidates() {
            return inetAddressCandidates;
        }

        public InetSocketAddress primaryAddress() {
            return primaryInetAddress;
        }

        public void acknowledgementReceived(final InetSocketAddress inetAddress) {
            if (primaryInetAddress == null) {
                // we got our first ack. drop all others candidates
                LOG.trace("Got our first Acknowledgement for traversing peer from `{}`. Drop all other candidates.", inetAddress);
                primaryInetAddress = inetAddress;
                inetAddressCandidates.retainAll(Set.of(primaryInetAddress));
            }
        }

        /**
         * Returns {@code true}, if we just started to ping (within the last
         * {@link #pingTimeoutMillis} the peer.
         */
        public boolean isNew(final ChannelHandlerContext ctx) {
            return firstHelloTime >= currentTime.getAsLong() - config(ctx).getHelloTimeout().toMillis();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final TraversingPeer that = (TraversingPeer) o;
            return Objects.equals(inetAddressCandidates, that.inetAddressCandidates);
        }

        @Override
        public int hashCode() {
            return Objects.hash(inetAddressCandidates);
        }
    }
}
