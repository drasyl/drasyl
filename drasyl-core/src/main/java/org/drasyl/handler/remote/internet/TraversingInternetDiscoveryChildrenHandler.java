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
import org.drasyl.channel.AddressedMessage;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.DiscoveryMessage;
import org.drasyl.handler.remote.protocol.UniteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.LongSupplier;

import static java.util.Objects.requireNonNull;

/**
 * Extends {@link InternetDiscoveryChildrenHandler} by performing a rendezvous initiated by one of
 * our super peers.
 *
 * @see TraversingInternetDiscoverySuperPeerHandler
 */
public class TraversingInternetDiscoveryChildrenHandler extends InternetDiscoveryChildrenHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TraversingInternetDiscoveryChildrenHandler.class);
    private static final Object PATH = TraversingInternetDiscoveryChildrenHandler.class;
    private final long pingCommunicationTimeoutMillis;
    private final long maxPeers;
    private final Map<DrasylAddress, TraversingPeer> traversingPeers;

    @SuppressWarnings("java:S107")
    TraversingInternetDiscoveryChildrenHandler(final int myNetworkId,
                                               final IdentityPublicKey myPublicKey,
                                               final ProofOfWork myProofOfWork,
                                               final LongSupplier currentTime,
                                               final long pingIntervalMillis,
                                               final long pingTimeoutMillis,
                                               final Map<IdentityPublicKey, SuperPeer> superPeers,
                                               final Future<?> heartbeatDisposable,
                                               final IdentityPublicKey bestSuperPeer,
                                               final long pingCommunicationTimeoutMillis,
                                               final long maxPeers,
                                               final Map<DrasylAddress, TraversingPeer> traversingPeers) {
        super(myNetworkId, myPublicKey, myProofOfWork, currentTime, pingIntervalMillis, pingTimeoutMillis, superPeers, heartbeatDisposable, bestSuperPeer);
        this.pingCommunicationTimeoutMillis = pingCommunicationTimeoutMillis;
        this.maxPeers = maxPeers;
        this.traversingPeers = requireNonNull(traversingPeers);
    }

    @SuppressWarnings("java:S107")
    public TraversingInternetDiscoveryChildrenHandler(final int myNetworkId,
                                                      final IdentityPublicKey myPublicKey,
                                                      final ProofOfWork myProofOfWork,
                                                      final long pingIntervalMillis,
                                                      final long pingTimeoutMillis,
                                                      final Map<IdentityPublicKey, InetSocketAddress> superPeerAddresses,
                                                      final long pingCommunicationTimeoutMillis,
                                                      final long maxPeers) {
        super(myNetworkId, myPublicKey, myProofOfWork, pingIntervalMillis, pingTimeoutMillis, superPeerAddresses);
        this.pingCommunicationTimeoutMillis = pingCommunicationTimeoutMillis;
        this.maxPeers = maxPeers;
        this.traversingPeers = new HashMap<>();
    }

    /*
     * Channel Events
     */

    @Override
    public void channelRead(final ChannelHandlerContext ctx,
                            final Object msg) {
        if (isUniteMessageFromSuperPeer(msg)) {
            final AddressedMessage<UniteMessage, ?> addressedMsg = (AddressedMessage<UniteMessage, ?>) msg;
            handleUniteMessage(ctx, addressedMsg.message());
        }
        else if (isDiscoveryMessageFromTraversingPeer(msg)) {
            final AddressedMessage<DiscoveryMessage, InetSocketAddress> addressedMsg = (AddressedMessage<DiscoveryMessage, InetSocketAddress>) msg;
            handleDiscoveryMessageFromTraversingPeer(ctx, addressedMsg.message(), addressedMsg.address());
        }
        else if (isAcknowledgementMessageFromTraversingPeer(msg)) {
            final AddressedMessage<AcknowledgementMessage, InetSocketAddress> addressedMsg = (AddressedMessage<AcknowledgementMessage, InetSocketAddress>) msg;
            handleAcknowledgementMessageFromTraversingPeer(ctx, addressedMsg.message(), addressedMsg.address());
        }
        else {
            if (isApplicationMessageFromTraversingPeer(msg)) {
                final TraversingPeer traversingPeer = traversingPeers.get(((AddressedMessage<ApplicationMessage, ?>) msg).message().getSender());
                traversingPeer.applicationSentOrReceived();
            }

            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (isRoutableOutboundMessage(msg)) {
            // for one of my traversing peers -> route
            final AddressedMessage<ApplicationMessage, InetSocketAddress> addressedMsg = (AddressedMessage<ApplicationMessage, InetSocketAddress>) msg;
            final DrasylAddress address = addressedMsg.message().getRecipient();
            final TraversingPeer traversingPeer = traversingPeers.get(address);
            final InetSocketAddress inetAddress = traversingPeer.inetAddress();
            traversingPeer.applicationSentOrReceived();

            LOG.trace("Got ApplicationMessage for traversing peer `{}`. Route it to address `{}`.", address, inetAddress);
            ctx.write(addressedMsg.route(inetAddress), promise);
        }
        else {
            // unknown message type/no traversing recipient -> pass through
            super.write(ctx, msg, promise);
        }
    }

    /*
     * Traversing
     */

    @SuppressWarnings("java:S1067")
    private boolean isUniteMessageFromSuperPeer(final Object msg) {
        return msg instanceof AddressedMessage &&
                ((AddressedMessage<?, ?>) msg).message() instanceof UniteMessage &&
                myPublicKey.equals(((AddressedMessage<UniteMessage, ?>) msg).message().getRecipient()) &&
                superPeers.containsKey(((AddressedMessage<UniteMessage, ?>) msg).message().getSender());
    }

    private void handleUniteMessage(final ChannelHandlerContext ctx, final UniteMessage msg) {
        final DrasylAddress address = msg.getAddress();
        final InetSocketAddress inetAddress = msg.getSocketAddress();
        LOG.trace("Got Unite for peer `{}` with address `{}`. Try to reach peer.", address, inetAddress);

        if (maxPeers == 0 || maxPeers > traversingPeers.size()) {
            // send Discovery
            final TraversingPeer traversingPeer = traversingPeers.computeIfAbsent(address, k -> new TraversingPeer(currentTime, pingTimeoutMillis, pingCommunicationTimeoutMillis, inetAddress));
            traversingPeer.applicationSentOrReceived();
            traversingPeer.discoverySent();
            writeDiscoveryMessage(ctx, address, traversingPeer.inetAddress(), false);
            ctx.flush();
        }
    }

    @SuppressWarnings("java:S1067")
    private boolean isDiscoveryMessageFromTraversingPeer(final Object msg) {
        return msg instanceof AddressedMessage &&
                ((AddressedMessage<?, ?>) msg).message() instanceof DiscoveryMessage &&
                ((AddressedMessage<?, ?>) msg).address() instanceof InetSocketAddress &&
                myPublicKey.equals(((AddressedMessage<DiscoveryMessage, ?>) msg).message().getRecipient()) &&
                traversingPeers.containsKey(((AddressedMessage<DiscoveryMessage, ?>) msg).message().getSender()) &&
                (((AddressedMessage<DiscoveryMessage, ?>) msg).message()).getTime() > currentTime.getAsLong() - pingTimeoutMillis &&
                ((AddressedMessage<DiscoveryMessage, ?>) msg).message().getChildrenTime() == 0;
    }

    private void handleDiscoveryMessageFromTraversingPeer(final ChannelHandlerContext ctx,
                                                          final DiscoveryMessage msg,
                                                          final InetSocketAddress inetAddress) {
        LOG.trace("Got Discovery from traversing peer `{}` from address `{}`.", msg.getSender(), inetAddress);

        final TraversingPeer traversingPeer = traversingPeers.get(msg.getSender());
        traversingPeer.setInetAddress(inetAddress);

        // reply with Acknowledgement
        final AcknowledgementMessage acknowledgementMsg = AcknowledgementMessage.of(myNetworkId, msg.getSender(), myPublicKey, myProofOfWork, msg.getTime());
        LOG.trace("Send Acknowledgement for traversing peer `{}` to `{}`.", msg::getSender, () -> inetAddress);
        ctx.writeAndFlush(new AddressedMessage<>(acknowledgementMsg, inetAddress));
    }

    @SuppressWarnings("java:S1067")
    private boolean isAcknowledgementMessageFromTraversingPeer(final Object msg) {
        return msg instanceof AddressedMessage<?, ?> &&
                ((AddressedMessage<AcknowledgementMessage, ?>) msg).message() instanceof AcknowledgementMessage &&
                ((AddressedMessage<?, ?>) msg).address() instanceof InetSocketAddress &&
                myPublicKey.equals(((AddressedMessage<AcknowledgementMessage, ?>) msg).message().getRecipient()) &&
                traversingPeers.containsKey(((AddressedMessage<AcknowledgementMessage, ?>) msg).message().getSender()) &&
                ((AddressedMessage<AcknowledgementMessage, ?>) msg).message().getTime() > currentTime.getAsLong() - pingTimeoutMillis;
    }

    private void handleAcknowledgementMessageFromTraversingPeer(final ChannelHandlerContext ctx,
                                                                final AcknowledgementMessage msg,
                                                                final InetSocketAddress inetAddress) {
        final DrasylAddress publicKey = msg.getSender();
        LOG.trace("Got Acknowledgement ({}ms latency) from traversing peer `{}`.", () -> System.currentTimeMillis() - msg.getTime(), () -> publicKey);

        final TraversingPeer traversingPeer = traversingPeers.get(publicKey);
        traversingPeer.acknowledgementReceived(inetAddress);

        ctx.fireUserEventTriggered(AddPathEvent.of(publicKey, inetAddress, PATH));
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
                ctx.fireUserEventTriggered(RemovePathEvent.of(address, PATH));
            }
            else {
                // send Discovery
                traversingPeer.discoverySent();
                writeDiscoveryMessage(ctx, address, traversingPeer.inetAddress, false);
                ctx.flush();
            }
        }
    }

    private boolean isApplicationMessageFromTraversingPeer(final Object msg) {
        return msg instanceof AddressedMessage<?, ?> &&
                ((AddressedMessage<?, ?>) msg).message() instanceof ApplicationMessage &&
                traversingPeers.containsKey(((ApplicationMessage) ((AddressedMessage<?, ?>) msg).message()).getSender());
    }

    /*
     * Routing
     */

    private boolean isRoutableOutboundMessage(final Object msg) {
        if (msg instanceof AddressedMessage<?, ?> &&
                ((AddressedMessage<?, ?>) msg).message() instanceof ApplicationMessage &&
                ((AddressedMessage<?, ?>) msg).address() instanceof IdentityPublicKey) {
            final TraversingPeer traversingPeer = traversingPeers.get(((ApplicationMessage) ((AddressedMessage<?, ?>) msg).message()).getRecipient());
            return traversingPeer != null && !traversingPeer.isStale();
        }
        else {
            return false;
        }
    }

    static class TraversingPeer {
        private final LongSupplier currentTime;
        private final long pingTimeoutMillis;
        private final long pingCommunicationTimeoutMillis;
        private InetSocketAddress inetAddress;
        long firstDiscoveryTime;
        long lastAcknowledgementTime;
        long lastApplicationTime;

        TraversingPeer(final LongSupplier currentTime,
                       final long pingTimeoutMillis,
                       final long pingCommunicationTimeoutMillis,
                       final InetSocketAddress inetAddress,
                       final long firstDiscoveryTime,
                       final long lastAcknowledgementTime,
                       final long lastApplicationTime) {
            this.currentTime = requireNonNull(currentTime);
            this.pingTimeoutMillis = pingTimeoutMillis;
            this.pingCommunicationTimeoutMillis = pingCommunicationTimeoutMillis;
            this.inetAddress = requireNonNull(inetAddress);
            this.firstDiscoveryTime = firstDiscoveryTime;
            this.lastAcknowledgementTime = lastAcknowledgementTime;
            this.lastApplicationTime = lastApplicationTime;
        }

        public TraversingPeer(final LongSupplier currentTime,
                              final long pingTimeoutMillis,
                              final long pingCommunicationTimeoutMillis,
                              final InetSocketAddress inetAddress) {
            this(currentTime, pingTimeoutMillis, pingCommunicationTimeoutMillis, inetAddress, 0L, 0L, 0L);
        }

        public void setInetAddress(final InetSocketAddress inetAddress) {
            this.inetAddress = requireNonNull(inetAddress);
        }

        public InetSocketAddress inetAddress() {
            return inetAddress;
        }

        public void discoverySent() {
            if (this.firstDiscoveryTime == 0) {
                this.firstDiscoveryTime = currentTime.getAsLong();
            }
        }

        public void acknowledgementReceived(final InetSocketAddress inetAddress) {
            setInetAddress(inetAddress);
            this.lastAcknowledgementTime = currentTime.getAsLong();
        }

        public void applicationSentOrReceived() {
            this.lastApplicationTime = currentTime.getAsLong();
        }

        public boolean isStale() {
            return lastApplicationTime < currentTime.getAsLong() - pingCommunicationTimeoutMillis ||
                    Math.max(firstDiscoveryTime, lastAcknowledgementTime) < currentTime.getAsLong() - pingTimeoutMillis;
        }
    }
}
