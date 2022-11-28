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
package org.drasyl.handler.remote.internet;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.handler.remote.protocol.UniteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.IdentitySecretKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.network.NetworkUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Extends {@link InternetDiscoveryChildrenHandler} by performing a rendezvous initiated by one of
 * our super peers.
 *
 * @see TraversingInternetDiscoverySuperPeerHandler
 */
@SuppressWarnings("unchecked")
public class TraversingInternetDiscoveryChildrenHandler extends InternetDiscoveryChildrenHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TraversingInternetDiscoveryChildrenHandler.class);
    private static final Object PATH = TraversingInternetDiscoveryChildrenHandler.class;
    private final long pingCommunicationTimeoutMillis;
    private final long maxPeers;
    private final Map<DrasylAddress, TraversingPeer> traversingPeers;

    @SuppressWarnings("java:S107")
    TraversingInternetDiscoveryChildrenHandler(final int myNetworkId,
                                               final IdentityPublicKey myPublicKey,
                                               final IdentitySecretKey mySecretKey,
                                               final ProofOfWork myProofOfWork,
                                               final LongSupplier currentTime,
                                               final long initialPingDelayMillis,
                                               final long pingIntervalMillis,
                                               final long pingTimeoutMillis,
                                               final long maxTimeOffsetMillis,
                                               final Map<IdentityPublicKey, SuperPeer> superPeers,
                                               final Future<?> heartbeatDisposable,
                                               final IdentityPublicKey bestSuperPeer,
                                               final long pingCommunicationTimeoutMillis,
                                               final long maxPeers,
                                               final Map<DrasylAddress, TraversingPeer> traversingPeers) {
        super(myNetworkId, myPublicKey, mySecretKey, myProofOfWork, currentTime, initialPingDelayMillis, pingIntervalMillis, pingTimeoutMillis, maxTimeOffsetMillis, superPeers, heartbeatDisposable, bestSuperPeer);
        this.pingCommunicationTimeoutMillis = requirePositive(pingCommunicationTimeoutMillis);
        this.maxPeers = requireNonNegative(maxPeers);
        this.traversingPeers = requireNonNull(traversingPeers);
    }

    /**
     * @param myNetworkId                    the network we belong to
     * @param myPublicKey                    own public key
     * @param mySecretKey                    own secret key
     * @param myProofOfWork                  own proof of work
     * @param initialPingDelayMillis         time in millis after
     *                                       {@link #channelActive(ChannelHandlerContext)} has been
     *                                       fired, we start to ping super peers
     * @param pingIntervalMillis             interval in millis between a ping
     * @param pingTimeoutMillis              time in millis without ping response before a peer is
     *                                       assumed as unreachable
     * @param maxTimeOffsetMillis            time millis offset of received messages' timestamp
     *                                       before discarding them
     * @param superPeerAddresses             inet addresses and public keys of super peers
     * @param pingCommunicationTimeoutMillis time in millis a traversed connection to a peer will be
     *                                       discarded without application traffic
     * @param maxPeers                       maximum number of peers to which a traversed connection
     *                                       should be maintained at the same time
     */
    @SuppressWarnings("java:S107")
    public TraversingInternetDiscoveryChildrenHandler(final int myNetworkId,
                                                      final IdentityPublicKey myPublicKey,
                                                      final IdentitySecretKey mySecretKey,
                                                      final ProofOfWork myProofOfWork,
                                                      final long initialPingDelayMillis,
                                                      final long pingIntervalMillis,
                                                      final long pingTimeoutMillis,
                                                      final long maxTimeOffsetMillis,
                                                      final Map<IdentityPublicKey, InetSocketAddress> superPeerAddresses,
                                                      final long pingCommunicationTimeoutMillis,
                                                      final long maxPeers) {
        super(myNetworkId, myPublicKey, mySecretKey, myProofOfWork, initialPingDelayMillis, pingIntervalMillis, pingTimeoutMillis, maxTimeOffsetMillis, superPeerAddresses);
        this.pingCommunicationTimeoutMillis = pingCommunicationTimeoutMillis;
        this.maxPeers = requireNonNegative(maxPeers);
        this.traversingPeers = new HashMap<>();
    }

    /*
     * Channel Events
     */

    @Override
    public void channelRead(final ChannelHandlerContext ctx,
                            final Object msg) {
        if (isUniteMessageFromSuperPeer(msg)) {
            final InetAddressedMessage<UniteMessage> addressedMsg = (InetAddressedMessage<UniteMessage>) msg;
            handleUniteMessage(ctx, addressedMsg.content());
        }
        else if (isDiscoveryMessageFromTraversingPeer(msg)) {
            final InetAddressedMessage<HelloMessage> addressedMsg = (InetAddressedMessage<HelloMessage>) msg;
            handleDiscoveryMessageFromTraversingPeer(ctx, addressedMsg.content(), addressedMsg.sender());
        }
        else if (isAcknowledgementMessageFromTraversingPeer(msg)) {
            final InetAddressedMessage<AcknowledgementMessage> addressedMsg = (InetAddressedMessage<AcknowledgementMessage>) msg;
            handleAcknowledgementMessageFromTraversingPeer(ctx, addressedMsg.content(), addressedMsg.sender());
        }
        else {
            if (isApplicationMessageFromTraversingPeer(msg)) {
                final TraversingPeer traversingPeer = traversingPeers.get(((InetAddressedMessage<ApplicationMessage>) msg).content().getSender());
                traversingPeer.applicationTrafficSentOrReceived();
            }

            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (isRoutableOutboundMessageToTraversingPeer(msg)) {
            // for one of my traversing peers -> route
            final OverlayAddressedMessage<ApplicationMessage> addressedMsg = (OverlayAddressedMessage<ApplicationMessage>) msg;
            handleRoutableOutboundMessageToTraversingPeer(ctx, promise, addressedMsg);
        }
        else {
            // unknown message type/no traversing recipient -> pass through
            super.write(ctx, msg, promise);
        }
    }

    /*
     * Traversing
     */

    @SuppressWarnings({ "java:S1067", "SuspiciousMethodCalls" })
    private boolean isUniteMessageFromSuperPeer(final Object msg) {
        return msg instanceof InetAddressedMessage &&
                ((InetAddressedMessage<?>) msg).content() instanceof UniteMessage &&
                myPublicKey.equals(((InetAddressedMessage<UniteMessage>) msg).content().getRecipient()) &&
                superPeers.containsKey(((InetAddressedMessage<UniteMessage>) msg).content().getSender());
    }

    private void handleUniteMessage(final ChannelHandlerContext ctx, final UniteMessage msg) {
        final DrasylAddress address = msg.getAddress();
        final Set<InetSocketAddress> inetAddresses = msg.getInetAddresses();
        LOG.trace("Got Unite for peer `{}` with addresses `{}`. Try to reach peer.", address, inetAddresses);

        if (maxPeers == 0 || maxPeers > traversingPeers.size()) {
            // send Hello
            final TraversingPeer traversingPeer = traversingPeers.computeIfAbsent(address, k -> new TraversingPeer(currentTime, pingTimeoutMillis, pingCommunicationTimeoutMillis, inetAddresses));
            traversingPeer.applicationTrafficSentOrReceived();
            traversingPeer.helloSent();
            for (final InetSocketAddress inetAddress : traversingPeer.inetAddressCandidates()) {
                writeHelloMessage(ctx, address, inetAddress, null);
            }
            ctx.flush();
        }
        else {
            LOG.trace("Got Unite for peer `{}` with address `{}`. But we've already reached maximum number of traversed peers. Drop message.", address, inetAddresses, inetAddresses);
        }
    }

    @SuppressWarnings("java:S1067")
    private boolean isDiscoveryMessageFromTraversingPeer(final Object msg) {
        return msg instanceof InetAddressedMessage &&
                ((InetAddressedMessage<?>) msg).content() instanceof HelloMessage &&
                myPublicKey.equals(((InetAddressedMessage<HelloMessage>) msg).content().getRecipient()) &&
                traversingPeers.containsKey(((InetAddressedMessage<HelloMessage>) msg).content().getSender()) &&
                Math.abs(currentTime.getAsLong() - (((InetAddressedMessage<HelloMessage>) msg).content()).getTime()) <= maxTimeOffsetMillis &&
                ((InetAddressedMessage<HelloMessage>) msg).content().getChildrenTime() == 0;
    }

    private void handleDiscoveryMessageFromTraversingPeer(final ChannelHandlerContext ctx,
                                                          final HelloMessage msg,
                                                          final InetSocketAddress inetAddress) {
        LOG.trace("Got Discovery from traversing peer `{}` from address `{}`.", msg.getSender(), inetAddress);

        final TraversingPeer traversingPeer = traversingPeers.get(msg.getSender());

        // reply with Acknowledgement
        final AcknowledgementMessage acknowledgementMsg = AcknowledgementMessage.of(myNetworkId, msg.getSender(), myPublicKey, myProofOfWork, msg.getTime());
        LOG.trace("Send Acknowledgement for traversing peer `{}` to `{}`.", msg::getSender, () -> inetAddress);
        ctx.writeAndFlush(new InetAddressedMessage<>(acknowledgementMsg, inetAddress));

        if (!traversingPeer.isReachable() && traversingPeer.addInetAddressCandidate(inetAddress)) {
            // send Discovery immediately to speed up traversal
            traversingPeer.applicationTrafficSentOrReceived();
            traversingPeer.helloSent();
            writeHelloMessage(ctx, msg.getSender(), inetAddress, null);
            ctx.flush();
        }
    }

    @SuppressWarnings("java:S1067")
    private boolean isAcknowledgementMessageFromTraversingPeer(final Object msg) {
        return msg instanceof InetAddressedMessage<?> &&
                ((InetAddressedMessage<?>) msg).content() instanceof AcknowledgementMessage &&
                myPublicKey.equals(((InetAddressedMessage<AcknowledgementMessage>) msg).content().getRecipient()) &&
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

        final AddPathEvent event = AddPathEvent.of(publicKey, inetAddress, PATH, rtt);
        if (pathEventFilter.add(event)) {
            ctx.fireUserEventTriggered(event);
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
                final RemovePathEvent event = RemovePathEvent.of(address, PATH);
                if (pathEventFilter.add(event)) {
                    ctx.fireUserEventTriggered(event);
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

    /*
     * Routing
     */

    private boolean isRoutableOutboundMessageToTraversingPeer(final Object msg) {
        if (msg instanceof OverlayAddressedMessage<?> &&
                ((OverlayAddressedMessage<?>) msg).content() instanceof ApplicationMessage) {
            final TraversingPeer traversingPeer = traversingPeers.get(((ApplicationMessage) ((OverlayAddressedMessage<?>) msg).content()).getRecipient());
            return traversingPeer != null && traversingPeer.isReachable();
        }
        else {
            return false;
        }
    }

    private void handleRoutableOutboundMessageToTraversingPeer(final ChannelHandlerContext ctx,
                                                               final ChannelPromise promise,
                                                               final OverlayAddressedMessage<ApplicationMessage> addressedMsg) {
        final DrasylAddress address = addressedMsg.content().getRecipient();
        final TraversingPeer traversingPeer = traversingPeers.get(address);
        final InetSocketAddress inetAddress = traversingPeer.primaryAddress();
        traversingPeer.applicationTrafficSentOrReceived();

        LOG.trace("Got ApplicationMessage `{}` for traversing peer `{}`. Resolve it to inet address `{}`.", addressedMsg.content().getNonce(), address, inetAddress);
        ctx.write(addressedMsg.resolve(inetAddress), promise);
    }

    static class TraversingPeer {
        private final LongSupplier currentTime;
        private final long pingTimeoutMillis;
        private final long pingCommunicationTimeoutMillis;
        long firstHelloTime;
        long lastAcknowledgementTime;
        long lastApplicationTime;
        private final Set<InetSocketAddress> inetAddressCandidates;
        private InetSocketAddress primaryInetAddress;

        TraversingPeer(final LongSupplier currentTime,
                       final long pingTimeoutMillis,
                       final long pingCommunicationTimeoutMillis,
                       final Set<InetSocketAddress> inetAddressCandidates,
                       final long firstHelloTime,
                       final long lastAcknowledgementTime,
                       final long lastApplicationTime) {
            this.currentTime = requireNonNull(currentTime);
            this.pingTimeoutMillis = pingTimeoutMillis;
            this.pingCommunicationTimeoutMillis = pingCommunicationTimeoutMillis;
            this.inetAddressCandidates = requireNonNull(inetAddressCandidates);
            this.firstHelloTime = firstHelloTime;
            this.lastAcknowledgementTime = lastAcknowledgementTime;
            this.lastApplicationTime = lastApplicationTime;
        }

        TraversingPeer(final LongSupplier currentTime,
                       final long pingTimeoutMillis,
                       final long pingCommunicationTimeoutMillis,
                       final Set<InetSocketAddress> inetAddressCandidates) {
            this(currentTime, pingTimeoutMillis, pingCommunicationTimeoutMillis, inetAddressCandidates, 0L, 0L, 0L);
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
                LOG.trace("Got our first Acknowledgement for traversing peer from `{}`. Drop all other candiates.", inetAddress);
                primaryInetAddress = inetAddress;
                inetAddressCandidates.retainAll(Set.of(primaryInetAddress));
            }
            this.lastAcknowledgementTime = currentTime.getAsLong();
        }

        public void applicationTrafficSentOrReceived() {
            this.lastApplicationTime = currentTime.getAsLong();
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
            return lastApplicationTime >= currentTime.getAsLong() - pingCommunicationTimeoutMillis;
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
    }
}
