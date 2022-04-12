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

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.DuplicatePathEventFilter;
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.IdentitySecretKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Joins one ore multiple super peer(s) as a children. Uses the super peer with the best latency as
 * a default gateway for outbound messages.
 *
 * @see InternetDiscoverySuperPeerHandler
 */
@SuppressWarnings("unchecked")
public class InternetDiscoveryChildrenHandler extends ChannelDuplexHandler {
    private static final long DEFAULT_CHILDREN_TIME = 60; // seconds
    private static final Logger LOG = LoggerFactory.getLogger(InternetDiscoveryChildrenHandler.class);
    private static final Object PATH = InternetDiscoveryChildrenHandler.class;
    protected final int myNetworkId;
    protected final IdentityPublicKey myPublicKey;
    protected final ProofOfWork myProofOfWork;
    protected final LongSupplier currentTime;
    protected final long pingTimeoutMillis;
    protected final long maxTimeOffsetMillis;
    protected final Map<IdentityPublicKey, SuperPeer> superPeers;
    protected final DuplicatePathEventFilter pathEventFilter = new DuplicatePathEventFilter();
    private final IdentitySecretKey mySecretKey;
    private final long initialPingDelayMillis;
    private final long pingIntervalMillis;
    Future<?> heartbeatDisposable;
    private IdentityPublicKey bestSuperPeer;

    @SuppressWarnings("java:S107")
    InternetDiscoveryChildrenHandler(final int myNetworkId,
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
                                     final IdentityPublicKey bestSuperPeer) {
        this.myNetworkId = myNetworkId;
        this.myPublicKey = requireNonNull(myPublicKey);
        this.mySecretKey = requireNonNull(mySecretKey);
        this.myProofOfWork = requireNonNull(myProofOfWork);
        this.currentTime = requireNonNull(currentTime);
        this.initialPingDelayMillis = requireNonNegative(initialPingDelayMillis);
        this.pingIntervalMillis = requirePositive(pingIntervalMillis);
        this.pingTimeoutMillis = requirePositive(pingTimeoutMillis);
        this.maxTimeOffsetMillis = requirePositive(maxTimeOffsetMillis);
        this.superPeers = requireNonNull(superPeers);
        this.heartbeatDisposable = heartbeatDisposable;
        this.bestSuperPeer = bestSuperPeer;
    }

    @SuppressWarnings("java:S107")
    public InternetDiscoveryChildrenHandler(final int myNetworkId,
                                            final IdentityPublicKey myPublicKey,
                                            final IdentitySecretKey mySecretKey,
                                            final ProofOfWork myProofOfWork,
                                            final LongSupplier currentTime,
                                            final long initialPingDelayMillis,
                                            final long pingIntervalMillis,
                                            final long pingTimeoutMillis,
                                            final long maxTimeOffsetMillis,
                                            final Map<IdentityPublicKey, InetSocketAddress> superPeerAddresses) {
        this(
                myNetworkId,
                myPublicKey,
                mySecretKey,
                myProofOfWork,
                currentTime,
                initialPingDelayMillis,
                pingIntervalMillis,
                pingTimeoutMillis,
                maxTimeOffsetMillis,
                superPeerAddresses.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> new SuperPeer(currentTime, pingTimeoutMillis, e.getValue()))),
                null,
                null
        );
    }

    @SuppressWarnings("java:S107")
    public InternetDiscoveryChildrenHandler(final int myNetworkId,
                                            final IdentityPublicKey myPublicKey,
                                            final IdentitySecretKey mySecretKey,
                                            final ProofOfWork myProofOfWork,
                                            final long initialPingDelayMillis,
                                            final long pingIntervalMillis,
                                            final long pingTimeoutMillis,
                                            final long maxTimeOffsetMillis,
                                            final Map<IdentityPublicKey, InetSocketAddress> superPeerAddresses) {
        this(
                myNetworkId,
                myPublicKey,
                mySecretKey,
                myProofOfWork,
                System::currentTimeMillis,
                initialPingDelayMillis,
                pingIntervalMillis,
                pingTimeoutMillis,
                maxTimeOffsetMillis,
                superPeerAddresses
        );
    }

    /*
     * Channel Events
     */

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        startHeartbeat(ctx);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        stopHeartbeat();
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (isAcknowledgementMessageFromSuperPeer(msg)) {
            final InetAddressedMessage<AcknowledgementMessage> addressedMsg = (InetAddressedMessage<AcknowledgementMessage>) msg;
            handleAcknowledgementMessage(ctx, addressedMsg.content(), addressedMsg.sender());
        }
        else if (isApplicationMessageForMe(msg)) {
            final InetAddressedMessage<ApplicationMessage> addressedMsg = (InetAddressedMessage<ApplicationMessage>) msg;
            handleApplicationMessage(ctx, addressedMsg);
        }
        else if (isUnexpectedMessage(msg)) {
            handleUnexpectedMessage(ctx, msg);
        }
        else {
            // pass through
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (isRoutableOutboundMessage(msg)) {
            final OverlayAddressedMessage<ApplicationMessage> addressedMsg = (OverlayAddressedMessage<ApplicationMessage>) msg;
            handleRoutableOutboundMessage(ctx, addressedMsg, promise);
        }
        else {
            // pass through
            ctx.write(msg, promise);
        }
    }

    /*
     * Pinging
     */

    void startHeartbeat(final ChannelHandlerContext ctx) {
        LOG.debug("Start Heartbeat job.");
        // populate initial state (RemoveSuperPeerAndPathEvent) for all super peers to our path event filter
        for (final Entry<IdentityPublicKey, SuperPeer> entry : superPeers.entrySet()) {
            final IdentityPublicKey publicKey = entry.getKey();
            final RemoveSuperPeerAndPathEvent event = RemoveSuperPeerAndPathEvent.of(publicKey, PATH);
            pathEventFilter.add(event);
        }
        heartbeatDisposable = ctx.executor().scheduleWithFixedDelay(() -> doHeartbeat(ctx), initialPingDelayMillis, pingIntervalMillis, MILLISECONDS);
    }

    void stopHeartbeat() {
        if (heartbeatDisposable != null) {
            LOG.debug("Stop Heartbeat job.");
            heartbeatDisposable.cancel(false);
            heartbeatDisposable = null;
        }
    }

    void doHeartbeat(final ChannelHandlerContext ctx) {
        determineBestSuperPeer(ctx);

        // ping super peers
        superPeers.forEach(((publicKey, superPeer) -> {
            // if possible, resolve the given address every single time. This ensures, that we are aware of DNS record updates
            final InetSocketAddress resolvedAddress = superPeer.resolveInetAddress();

            writeHelloMessage(ctx, publicKey, resolvedAddress, true);
        }));
        ctx.flush();
    }

    /**
     * Make sure to call {@link Channel#flush()} by your own!
     */
    protected void writeHelloMessage(final ChannelHandlerContext ctx,
                                     final DrasylAddress publicKey,
                                     final InetSocketAddress inetAddress,
                                     final boolean isChildrenJoin) {
        final HelloMessage msg;
        if (isChildrenJoin) {
            // hello message is used to register at super peer as children
            msg = HelloMessage.of(myNetworkId, publicKey, myPublicKey, myProofOfWork, DEFAULT_CHILDREN_TIME, mySecretKey);
        }
        else {
            // hello message is used to announce us at peer
            msg = HelloMessage.of(myNetworkId, publicKey, myPublicKey, myProofOfWork);
        }

        LOG.trace("Send Discovery (children = {}) for peer `{}` to `{}`.", () -> isChildrenJoin, () -> publicKey, () -> inetAddress);
        ctx.write(new InetAddressedMessage<>(msg, inetAddress)).addListener(future -> {
            if (!future.isSuccess()) {
                //noinspection unchecked
                LOG.warn("Unable to send Discovery for peer `{}` to address `{}`:", () -> publicKey, () -> inetAddress, future::cause);
            }
        });
    }

    /*
     * Ponging
     */

    @SuppressWarnings({ "java:S1067", "SuspiciousMethodCalls" })
    private boolean isAcknowledgementMessageFromSuperPeer(final Object msg) {
        return msg instanceof InetAddressedMessage<?> &&
                ((InetAddressedMessage<?>) msg).content() instanceof AcknowledgementMessage &&
                superPeers.containsKey(((InetAddressedMessage<AcknowledgementMessage>) msg).content().getSender()) &&
                myPublicKey.equals(((InetAddressedMessage<AcknowledgementMessage>) msg).content().getRecipient()) &&
                Math.abs(currentTime.getAsLong() - (((InetAddressedMessage<AcknowledgementMessage>) msg).content()).getTime()) <= maxTimeOffsetMillis;
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    private void handleAcknowledgementMessage(final ChannelHandlerContext ctx,
                                              final AcknowledgementMessage msg,
                                              final InetSocketAddress inetAddress) {
        final DrasylAddress publicKey = msg.getSender();
        final long rtt = currentTime.getAsLong() - msg.getTime();
        LOG.trace("Got Acknowledgement ({}ms RTT) from super peer `{}`.", () -> rtt, () -> publicKey);

        final SuperPeer superPeer = superPeers.get(publicKey);
        superPeer.acknowledgementReceived(rtt);

        // we don't have a super peer yet, so this is now our best one
        if (bestSuperPeer == null) {
            bestSuperPeer = (IdentityPublicKey) publicKey;
        }

        final AddPathAndSuperPeerEvent event = AddPathAndSuperPeerEvent.of(publicKey, inetAddress, PATH, rtt);
        if (pathEventFilter.add(event)) {
            ctx.fireUserEventTriggered(event);
        }
        else {
            ctx.fireUserEventTriggered(PathRttEvent.of(publicKey, inetAddress, PATH, rtt));
        }

        determineBestSuperPeer(ctx);
    }

    private boolean isApplicationMessageForMe(final Object msg) {
        return msg instanceof InetAddressedMessage<?> &&
                ((InetAddressedMessage<?>) msg).content() instanceof ApplicationMessage &&
                myPublicKey.equals((((InetAddressedMessage<ApplicationMessage>) msg).content()).getRecipient());
    }

    @SuppressWarnings("java:S2325")
    private void handleApplicationMessage(final ChannelHandlerContext ctx,
                                          final InetAddressedMessage<ApplicationMessage> addressedMsg) {
        ctx.fireChannelRead(addressedMsg);
    }

    private void determineBestSuperPeer(final ChannelHandlerContext ctx) {
        IdentityPublicKey newBestSuperPeer = null;
        long bestLatency = Long.MAX_VALUE;
        for (final Entry<IdentityPublicKey, SuperPeer> entry : superPeers.entrySet()) {
            final IdentityPublicKey publicKey = entry.getKey();
            final SuperPeer superPeer = entry.getValue();
            if (!superPeer.isStale()) {
                if (superPeer.latency < bestLatency) {
                    newBestSuperPeer = publicKey;
                    bestLatency = superPeer.latency;
                }
            }
            else {
                final RemoveSuperPeerAndPathEvent event = RemoveSuperPeerAndPathEvent.of(publicKey, PATH);
                if (pathEventFilter.add(event)) {
                    ctx.fireUserEventTriggered(event);
                }
            }
        }

        if (!Objects.equals(bestSuperPeer, newBestSuperPeer)) {
            final IdentityPublicKey oldBestSuperPeer = bestSuperPeer;
            bestSuperPeer = newBestSuperPeer;
            if (LOG.isTraceEnabled()) {
                if (newBestSuperPeer != null) {
                    LOG.trace("New best super peer ({}ms latency)! Replace `{}` with `{}`", bestLatency, oldBestSuperPeer, newBestSuperPeer);
                }
                else {
                    LOG.trace("All super peers stale!");
                }
            }
        }
    }

    /*
     * Routing
     */

    private boolean isRoutableOutboundMessage(final Object msg) {
        return bestSuperPeer != null &&
                msg instanceof OverlayAddressedMessage &&
                ((OverlayAddressedMessage<?>) msg).content() instanceof ApplicationMessage;
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    private void handleRoutableOutboundMessage(final ChannelHandlerContext ctx,
                                               final OverlayAddressedMessage<ApplicationMessage> msg,
                                               final ChannelPromise promise) {
        final SuperPeer superPeer = superPeers.get(msg.recipient());
        if (superPeer != null) {
            LOG.trace("Message `{}` is addressed to one of our super peers. Route message for super peer `{}` to well-known address `{}`.", msg.content().getNonce(), msg.recipient(), superPeer.inetAddress());
            ctx.write(msg.resolve(superPeer.inetAddress()), promise);
        }
        else {
            final InetSocketAddress inetAddress = superPeers.get(bestSuperPeer).inetAddress();
            LOG.trace("No direct connection to message recipient. Use super peer as default gateway. Relay message `{}` for peer `{}` to super peer `{}` via well-known address `{}`.", msg.content().getNonce(), msg.recipient(), bestSuperPeer, inetAddress);
            ctx.write(msg.resolve(inetAddress), promise);
        }
    }

    /*
     * Unexpected messages handling
     */

    @SuppressWarnings({ "java:S1067", "java:S2325" })
    protected boolean isUnexpectedMessage(final Object msg) {
        return msg instanceof InetAddressedMessage &&
                !(((InetAddressedMessage<?>) msg).content() instanceof HelloMessage && ((InetAddressedMessage<HelloMessage>) msg).content().getRecipient() == null);
    }

    @SuppressWarnings({ "unused", "java:S2325" })
    private void handleUnexpectedMessage(final ChannelHandlerContext ctx,
                                         final Object msg) {
        ReferenceCountUtil.release(msg);
        LOG.trace("Got unexpected message `{}`. Drop it.", msg);
    }

    static class SuperPeer {
        private final LongSupplier currentTime;
        private final long pingTimeoutMillis;
        long lastAcknowledgementTime;
        long latency;
        private InetSocketAddress inetAddress;

        SuperPeer(final LongSupplier currentTime,
                  final long pingTimeoutMillis,
                  final InetSocketAddress inetAddress,
                  final long lastAcknowledgementTime,
                  final long latency) {
            this.currentTime = requireNonNull(currentTime);
            this.pingTimeoutMillis = pingTimeoutMillis;
            this.inetAddress = requireNonNull(inetAddress);
            this.lastAcknowledgementTime = lastAcknowledgementTime;
            this.latency = latency;
        }

        public SuperPeer(final LongSupplier currentTime,
                         final long pingTimeoutMillis,
                         final InetSocketAddress inetAddress) {
            this(currentTime, pingTimeoutMillis, inetAddress, 0L, 0L);
        }

        public InetSocketAddress inetAddress() {
            return inetAddress;
        }

        public void acknowledgementReceived(final long latency) {
            this.lastAcknowledgementTime = currentTime.getAsLong();
            this.latency = latency;
        }

        public boolean isStale() {
            return lastAcknowledgementTime < currentTime.getAsLong() - pingTimeoutMillis;
        }

        /**
         * Triggers a new resolve of the hostname into an {@link java.net.InetAddress}.
         */
        public InetSocketAddress resolveInetAddress() {
            inetAddress = new InetSocketAddress(inetAddress.getHostString(), inetAddress.getPort());
            return inetAddress;
        }
    }
}
