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

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.LongSupplier;

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
        final InetSocketAddress inetAddress = msg.getSocketAddress();
        LOG.trace("Got Unite for peer `{}` with address `{}`. Try to reach peer.", address, inetAddress);

        if (maxPeers == 0 || maxPeers > traversingPeers.size()) {
            // send Discovery
            final TraversingPeer traversingPeer = traversingPeers.computeIfAbsent(address, k -> new TraversingPeer(currentTime, pingTimeoutMillis, pingCommunicationTimeoutMillis, inetAddress));
            traversingPeer.applicationTrafficSentOrReceived();
            traversingPeer.helloSent();
            writeHelloMessage(ctx, address, traversingPeer.inetAddress(), false);
            ctx.flush();
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
        boolean inetAddressHasChanged = traversingPeer.setInetAddress(inetAddress);

        // reply with Acknowledgement
        final AcknowledgementMessage acknowledgementMsg = AcknowledgementMessage.of(myNetworkId, msg.getSender(), myPublicKey, myProofOfWork, msg.getTime());
        LOG.trace("Send Acknowledgement for traversing peer `{}` to `{}`.", msg::getSender, () -> inetAddress);
        ctx.writeAndFlush(new InetAddressedMessage<>(acknowledgementMsg, inetAddress));

        if (inetAddressHasChanged) {
            // send Discovery immediately to speed up traversal
            traversingPeer.applicationTrafficSentOrReceived();
            traversingPeer.helloSent();
            writeHelloMessage(ctx, msg.getSender(), traversingPeer.inetAddress(), false);
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
        LOG.trace("Got Acknowledgement ({}ms latency) from traversing peer `{}`.", () -> System.currentTimeMillis() - msg.getTime(), () -> publicKey);

        final TraversingPeer traversingPeer = traversingPeers.get(publicKey);
        traversingPeer.acknowledgementReceived(inetAddress);

        final AddPathEvent event = AddPathEvent.of(publicKey, inetAddress, PATH);
        if (pathEventFilter.add(event)) {
            ctx.fireUserEventTriggered(event);
        }
    }

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
                writeHelloMessage(ctx, address, traversingPeer.inetAddress, false);
                ctx.flush();
            }
        }
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
        final InetSocketAddress inetAddress = traversingPeer.inetAddress();
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
        private InetSocketAddress inetAddress;

        TraversingPeer(final LongSupplier currentTime,
                       final long pingTimeoutMillis,
                       final long pingCommunicationTimeoutMillis,
                       final InetSocketAddress inetAddress,
                       final long firstHelloTime,
                       final long lastAcknowledgementTime,
                       final long lastApplicationTime) {
            this.currentTime = requireNonNull(currentTime);
            this.pingTimeoutMillis = pingTimeoutMillis;
            this.pingCommunicationTimeoutMillis = pingCommunicationTimeoutMillis;
            this.inetAddress = requireNonNull(inetAddress);
            this.firstHelloTime = firstHelloTime;
            this.lastAcknowledgementTime = lastAcknowledgementTime;
            this.lastApplicationTime = lastApplicationTime;
        }

        public TraversingPeer(final LongSupplier currentTime,
                              final long pingTimeoutMillis,
                              final long pingCommunicationTimeoutMillis,
                              final InetSocketAddress inetAddress) {
            this(currentTime, pingTimeoutMillis, pingCommunicationTimeoutMillis, inetAddress, 0L, 0L, 0L);
        }

        public boolean setInetAddress(final InetSocketAddress inetAddress) {
            final boolean changed = !Objects.equals(inetAddress, this.inetAddress);
            this.inetAddress = requireNonNull(inetAddress);
            return changed;
        }

        public InetSocketAddress inetAddress() {
            return inetAddress;
        }

        public void helloSent() {
            if (this.firstHelloTime == 0) {
                this.firstHelloTime = currentTime.getAsLong();
            }
        }

        public void acknowledgementReceived(final InetSocketAddress inetAddress) {
            setInetAddress(inetAddress);
            this.lastAcknowledgementTime = currentTime.getAsLong();
        }

        public void applicationTrafficSentOrReceived() {
            this.lastApplicationTime = currentTime.getAsLong();
        }

        /**
         * Returns {@code true}, if we just started to ping (within the last {@link
         * #pingTimeoutMillis} the peer.
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
         * Returns {@code true}, if {@link #isNew()} ()} and ({@link #isNew()} or {@link
         * #isReachable()}) return {@code false}.
         */
        public boolean isStale() {
            return !isNew() && (!hasApplicationTraffic() || !isReachable());
        }
    }
}
