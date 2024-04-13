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
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.handler.remote.protocol.UniteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
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
    private static final Object PATH = TraversingInternetDiscoveryChildrenHandler.class;
    static final Class<?> PATH_ID = TraversingInternetDiscoveryChildrenHandler.class;
    static final short PATH_PRIORITY = 95;
    private Long pingCommunicationTimeoutMillis;
    private Integer maxPeers;
    private final Map<DrasylAddress, TraversingPeer> traversingPeers;

    @SuppressWarnings("java:S107")
    TraversingInternetDiscoveryChildrenHandler(final Integer myNetworkId,
                                               final Identity myIdentity,
                                               final LongSupplier currentTime,
                                               final PeersManager peersManager,
                                               final long initialPingDelayMillis,
                                               final long pingIntervalMillis,
                                               final long pingTimeoutMillis,
                                               final long maxTimeOffsetMillis,
                                               final Map<IdentityPublicKey, SuperPeer> superPeers,
                                               final Future<?> heartbeatDisposable,
                                               final long pingCommunicationTimeoutMillis,
                                               final Integer maxPeers,
                                               final Map<DrasylAddress, TraversingPeer> traversingPeers) {
        super(myNetworkId, myIdentity, currentTime, initialPingDelayMillis, pingIntervalMillis, pingTimeoutMillis, maxTimeOffsetMillis, superPeers, heartbeatDisposable, peersManager);
        this.pingCommunicationTimeoutMillis = pingCommunicationTimeoutMillis;
        this.maxPeers = maxPeers;
        this.traversingPeers = requireNonNull(traversingPeers);
    }

    /**
     * @param pingCommunicationTimeoutMillis time in millis a traversed connection to a peer will be
     *                                       discarded without application traffic
     * @param maxPeers                       maximum number of peers to which a traversed connection
     *                                       should be maintained at the same time
     */
    @SuppressWarnings("java:S107")
    public TraversingInternetDiscoveryChildrenHandler() {
        super();
        this.traversingPeers = new HashMap<>();
    }

    /*
     * Channel Events
     */

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (pingCommunicationTimeoutMillis == null) {
            pingCommunicationTimeoutMillis = ((DrasylServerChannelConfig) ctx.channel().config()).getPathIdleTime().toMillis();
        }
        if (maxPeers == null) {
            maxPeers = ((DrasylServerChannelConfig) ctx.channel().config()).getMaxPeers();
        }

        super.channelActive(ctx);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx,
                            final Object msg) {
        if (isUniteMessageFromSuperPeer(msg)) {
            final InetAddressedMessage<UniteMessage> addressedMsg = (InetAddressedMessage<UniteMessage>) msg;
            handleUniteMessage(ctx, addressedMsg.content());
        }
        else if (isHelloMessageFromTraversingPeer(msg)) {
            final InetAddressedMessage<HelloMessage> addressedMsg = (InetAddressedMessage<HelloMessage>) msg;
            handleHelloMessageFromTraversingPeer(ctx, addressedMsg.content(), addressedMsg.sender());
        }
        else if (isAcknowledgementMessageFromTraversingPeer(msg)) {
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
    private boolean isUniteMessageFromSuperPeer(final Object msg) {
        return msg instanceof InetAddressedMessage &&
                ((InetAddressedMessage<?>) msg).content() instanceof UniteMessage &&
                myIdentity.getIdentityPublicKey().equals(((InetAddressedMessage<UniteMessage>) msg).content().getRecipient()) &&
                superPeers.containsKey(((InetAddressedMessage<UniteMessage>) msg).content().getSender());
    }

    private void handleUniteMessage(final ChannelHandlerContext ctx, final UniteMessage msg) {
        final DrasylAddress address = msg.getAddress();
        final Set<InetSocketAddress> endpoints = msg.getEndpoints();
        LOG.debug("Got Unite for peer `{}` with endpoints `{}`.", address, endpoints);

        if (maxPeers == 0 || maxPeers > traversingPeers.size()) {
            final TraversingPeer existingTraversingPeer = traversingPeers.get(address);
            final TraversingPeer newTraversingPeer = new TraversingPeer(currentTime, pingTimeoutMillis, pingCommunicationTimeoutMillis, endpoints, address, peersManager);

            if (!Objects.equals(existingTraversingPeer, newTraversingPeer)) {
                // new peer or endpoints have changes -> send Hello
                LOG.debug("Try to reach peer `{}` at endpoints `{}`", address, endpoints);
                traversingPeers.put(address, newTraversingPeer);
                peersManager.applicationMessageSentOrReceived(address);
                newTraversingPeer.helloSent();
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
    private boolean isHelloMessageFromTraversingPeer(final Object msg) {
        return msg instanceof InetAddressedMessage &&
                ((InetAddressedMessage<?>) msg).content() instanceof HelloMessage &&
                myIdentity.getIdentityPublicKey().equals(((InetAddressedMessage<HelloMessage>) msg).content().getRecipient()) &&
                traversingPeers.containsKey(((InetAddressedMessage<HelloMessage>) msg).content().getSender()) &&
                Math.abs(currentTime.getAsLong() - (((InetAddressedMessage<HelloMessage>) msg).content()).getTime()) <= maxTimeOffsetMillis &&
                ((InetAddressedMessage<HelloMessage>) msg).content().getChildrenTime() == 0;
    }

    private void handleHelloMessageFromTraversingPeer(final ChannelHandlerContext ctx,
                                                      final HelloMessage msg,
                                                      final InetSocketAddress inetAddress) {
        LOG.trace("Got Hello from traversing peer `{}` from address `{}`.", msg.getSender(), inetAddress);

        final TraversingPeer traversingPeer = traversingPeers.get(msg.getSender());

        // reply with Acknowledgement
        final AcknowledgementMessage acknowledgementMsg = AcknowledgementMessage.of(myNetworkId, msg.getSender(), myIdentity.getIdentityPublicKey(), myIdentity.getProofOfWork(), msg.getTime());
        LOG.trace("Send Acknowledgement for traversing peer `{}` to `{}`.", msg::getSender, () -> inetAddress);
        ctx.writeAndFlush(new InetAddressedMessage<>(acknowledgementMsg, inetAddress));

        if (!traversingPeer.isReachable() && traversingPeer.addInetAddressCandidate(inetAddress)) {
            // send Hello immediately to speed up traversal
            ((DrasylServerChannelConfig) ctx.channel().config()).getPeersManager().applicationMessageSentOrReceived(msg.getSender());
            traversingPeer.helloSent();
            writeHelloMessage(ctx, msg.getSender(), inetAddress, null);
            ctx.flush();
        }
    }

    @SuppressWarnings("java:S1067")
    private boolean isAcknowledgementMessageFromTraversingPeer(final Object msg) {
        return msg instanceof InetAddressedMessage<?> &&
                ((InetAddressedMessage<?>) msg).content() instanceof AcknowledgementMessage &&
                myIdentity.getIdentityPublicKey().equals(((InetAddressedMessage<AcknowledgementMessage>) msg).content().getRecipient()) &&
                traversingPeers.containsKey(((InetAddressedMessage<AcknowledgementMessage>) msg).content().getSender()) &&
                Math.abs(currentTime.getAsLong() - (((InetAddressedMessage<AcknowledgementMessage>) msg).content()).getTime()) <= maxTimeOffsetMillis;
    }

    private void handleAcknowledgementMessageFromTraversingPeer(final ChannelHandlerContext ctx,
                                                                final AcknowledgementMessage msg,
                                                                final InetSocketAddress inetAddress) {
        final DrasylAddress publicKey = msg.getSender();
        final long rtt = currentTime.getAsLong() - msg.getTime();
        LOG.trace("Got Acknowledgement ({}ms RTT) from traversing peer `{}`.", () -> rtt, () -> publicKey);

        final TraversingPeer traversingPeer = traversingPeers.get(publicKey);
        traversingPeer.acknowledgementReceived(inetAddress);

        if (peersManager.addPath(publicKey, PATH_ID, inetAddress, PATH_PRIORITY)) {
            ctx.fireUserEventTriggered(AddPathEvent.of(publicKey, inetAddress, PATH, rtt));
        }
        else {
            ctx.fireUserEventTriggered(PathRttEvent.of(publicKey, inetAddress, PATH, rtt));
        }
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

            if (traversingPeer.isStale()) {
                LOG.trace("Traversing peer `{}` is stale. Remove from my neighbour list.", address);
                it.remove();
                if (((DrasylServerChannelConfig) ctx.channel().config()).getPeersManager().removePath(address, PATH_ID)) {
                    ctx.fireUserEventTriggered(RemovePathEvent.of(address, PATH));
                }
            }
            else {
                // send Discovery
                traversingPeer.helloSent();
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

    private boolean isApplicationMessageFromTraversingPeer(final Object msg) {
        return msg instanceof InetAddressedMessage<?> &&
                ((InetAddressedMessage<?>) msg).content() instanceof ApplicationMessage &&
                traversingPeers.containsKey(((ApplicationMessage) ((InetAddressedMessage<?>) msg).content()).getSender());
    }

    static class TraversingPeer {
        private final LongSupplier currentTime;
        private final long pingTimeoutMillis;
        private final long pingCommunicationTimeoutMillis;
        long firstHelloTime;
        long lastAcknowledgementTime;
        private final DrasylAddress address;
        private final PeersManager peersManager;
        private final Set<InetSocketAddress> inetAddressCandidates;
        private InetSocketAddress primaryInetAddress;

        TraversingPeer(final LongSupplier currentTime,
                       final long pingTimeoutMillis,
                       final long pingCommunicationTimeoutMillis,
                       final Set<InetSocketAddress> inetAddressCandidates,
                       final long firstHelloTime,
                       final long lastAcknowledgementTime,
                       final DrasylAddress address,
                       final PeersManager peersManager) {
            this.currentTime = requireNonNull(currentTime);
            this.pingTimeoutMillis = pingTimeoutMillis;
            this.pingCommunicationTimeoutMillis = pingCommunicationTimeoutMillis;
            this.inetAddressCandidates = requireNonNull(inetAddressCandidates);
            this.firstHelloTime = firstHelloTime;
            this.lastAcknowledgementTime = lastAcknowledgementTime;
            this.address = requireNonNull(address);
            this.peersManager = requireNonNull(peersManager);
        }

        TraversingPeer(final LongSupplier currentTime,
                       final long pingTimeoutMillis,
                       final long pingCommunicationTimeoutMillis,
                       final Set<InetSocketAddress> inetAddressCandidates,
                       final DrasylAddress address,
                       final PeersManager peersManager) {
            this(currentTime, pingTimeoutMillis, pingCommunicationTimeoutMillis, inetAddressCandidates, 0L, 0L, address, peersManager);
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

        public void helloSent() {
            if (this.firstHelloTime == 0) {
                this.firstHelloTime = currentTime.getAsLong();
            }
        }

        public void acknowledgementReceived(final InetSocketAddress inetAddress) {
            if (primaryInetAddress == null) {
                // we got our first ack. drop all others candidates
                LOG.trace("Got our first Acknowledgement for traversing peer from `{}`. Drop all other candidates.", inetAddress);
                primaryInetAddress = inetAddress;
                inetAddressCandidates.retainAll(Set.of(primaryInetAddress));
            }
            this.lastAcknowledgementTime = currentTime.getAsLong();
        }

        /**
         * Returns {@code true}, if we just started to ping (within the last
         * {@link #pingTimeoutMillis} the peer.
         */
        public boolean isNew() {
            return firstHelloTime >= currentTime.getAsLong() - pingTimeoutMillis;
        }

        /**
         * Returns {@code true}, application message has been sent to or received from the peer
         * within the last {@link #pingCommunicationTimeoutMillis}.
         */
        public boolean hasApplicationTraffic() {
            return peersManager.lastApplicationMessageSentOrReceivedTime(address) >= currentTime.getAsLong() - pingCommunicationTimeoutMillis;
        }

        /**
         * Returns {@code true}, if we have received an acknowledgement message from the peer within
         * the last {@link #pingTimeoutMillis}ms.
         */
        public boolean isReachable() {
            return lastAcknowledgementTime >= currentTime.getAsLong() - pingTimeoutMillis;
        }

        /**
         * Returns {@code true}, if {@link #isNew()} ()} and ({@link #isNew()} or
         * {@link #isReachable()}) return {@code false}.
         */
        public boolean isStale() {
            return !isNew() && (!hasApplicationTraffic() || !isReachable());
        }

        @Override
        public boolean equals(Object o) {
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
