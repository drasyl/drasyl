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
import org.drasyl.channel.AddressedMessage;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.DiscoveryMessage;
import org.drasyl.identity.IdentityPublicKey;
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
import static org.drasyl.util.RandomUtil.randomLong;

/**
 * Joins one ore multiple super peer(s) as a children. Uses the super peer with the best latency as
 * a default gateway for outbound messages.
 *
 * @see InternetDiscoverySuperPeerHandler
 */
public class InternetDiscoveryChildrenHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(InternetDiscoveryChildrenHandler.class);
    private static final Object PATH = InternetDiscoveryChildrenHandler.class;
    protected final int myNetworkId;
    protected final IdentityPublicKey myPublicKey;
    protected final ProofOfWork myProofOfWork;
    protected final LongSupplier currentTime;
    private final long pingIntervalMillis;
    protected final long pingTimeoutMillis;
    protected final Map<IdentityPublicKey, SuperPeer> superPeers;
    Future<?> heartbeatDisposable;
    private IdentityPublicKey bestSuperPeer;

    @SuppressWarnings("java:S107")
    InternetDiscoveryChildrenHandler(final int myNetworkId,
                                     final IdentityPublicKey myPublicKey,
                                     final ProofOfWork myProofOfWork,
                                     final LongSupplier currentTime,
                                     final long pingIntervalMillis,
                                     final long pingTimeoutMillis,
                                     final Map<IdentityPublicKey, SuperPeer> superPeers,
                                     final Future<?> heartbeatDisposable,
                                     final IdentityPublicKey bestSuperPeer) {
        this.myNetworkId = myNetworkId;
        this.myPublicKey = requireNonNull(myPublicKey);
        this.myProofOfWork = requireNonNull(myProofOfWork);
        this.currentTime = requireNonNull(currentTime);
        this.pingIntervalMillis = pingIntervalMillis;
        this.pingTimeoutMillis = pingTimeoutMillis;
        this.superPeers = requireNonNull(superPeers);
        this.heartbeatDisposable = heartbeatDisposable;
        this.bestSuperPeer = bestSuperPeer;
    }

    public InternetDiscoveryChildrenHandler(final int myNetworkId,
                                            final IdentityPublicKey myPublicKey,
                                            final ProofOfWork myProofOfWork,
                                            final LongSupplier currentTime,
                                            final long pingIntervalMillis,
                                            final long pingTimeoutMillis,
                                            final Map<IdentityPublicKey, InetSocketAddress> superPeerAddresses) {
        this(
                myNetworkId,
                myPublicKey,
                myProofOfWork,
                currentTime,
                pingIntervalMillis,
                pingTimeoutMillis,
                superPeerAddresses.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> new SuperPeer(currentTime, pingTimeoutMillis, e.getValue()))),
                null,
                null
        );
    }

    public InternetDiscoveryChildrenHandler(final int myNetworkId,
                                            final IdentityPublicKey myPublicKey,
                                            final ProofOfWork myProofOfWork,
                                            final long pingIntervalMillis,
                                            final long pingTimeoutMillis,
                                            final Map<IdentityPublicKey, InetSocketAddress> superPeerAddresses) {
        this(
                myNetworkId,
                myPublicKey,
                myProofOfWork,
                System::currentTimeMillis,
                pingIntervalMillis,
                pingTimeoutMillis,
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
            final AddressedMessage<AcknowledgementMessage, InetSocketAddress> addressedMsg = (AddressedMessage<AcknowledgementMessage, InetSocketAddress>) msg;
            handleAcknowledgementMessage(ctx, addressedMsg.message(), addressedMsg.address());
        }
        else if (isUnexpectedMessage(msg)) {
            LOG.trace("Got unexpected message `{}`. Drop it.", msg);
            ReferenceCountUtil.release(msg);
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
        if (isRoutableMessage(msg)) {
            final AddressedMessage<ApplicationMessage, IdentityPublicKey> addressedMsg = (AddressedMessage<ApplicationMessage, IdentityPublicKey>) msg;
            routeMessage(ctx, promise, addressedMsg.message());
        }
        else {
            // pass through
            ctx.write(msg, promise);
        }
    }

    @SuppressWarnings({ "java:S1067", "java:S2325" })
    protected boolean isUnexpectedMessage(final Object msg) {
        return msg instanceof AddressedMessage &&
                !(((AddressedMessage<?, ?>) msg).message() instanceof ApplicationMessage) &&
                !(((AddressedMessage<?, ?>) msg).message() instanceof DiscoveryMessage && ((AddressedMessage<DiscoveryMessage, ?>) msg).message().getRecipient() == null);
    }

    /*
     * Pinging
     */

    void startHeartbeat(final ChannelHandlerContext ctx) {
        LOG.debug("Start Heartbeat job.");
        heartbeatDisposable = ctx.executor().scheduleWithFixedDelay(() -> doHeartbeat(ctx), randomLong(pingIntervalMillis), pingIntervalMillis, MILLISECONDS);
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
            superPeer.discoverySent();
            writeDiscoveryMessage(ctx, publicKey, superPeer.inetAddress(), true);
        }));
        ctx.flush();
    }

    /**
     * Make sure to call {@link Channel#flush()} by your own!
     */
    protected void writeDiscoveryMessage(final ChannelHandlerContext ctx,
                                         final IdentityPublicKey publicKey,
                                         final InetSocketAddress inetAddress,
                                         final boolean isChildrenJoin) {
        final long childrenTime = isChildrenJoin ? currentTime.getAsLong() : 0;
        final DiscoveryMessage msg = DiscoveryMessage.of(myNetworkId, publicKey, myPublicKey, myProofOfWork, childrenTime);
        LOG.trace("Send Discovery (children = {}) for peer `{}` to `{}`.", () -> isChildrenJoin, () -> publicKey, () -> inetAddress);
        ctx.write(new AddressedMessage<>(msg, inetAddress)).addListener(future -> {
            if (!future.isSuccess()) {
                //noinspection unchecked
                LOG.warn("Unable to send Discovery for peer `{}` to address `{}`:", () -> publicKey, () -> inetAddress, future::cause);
            }
        });
    }

    /*
     * Ponging
     */

    @SuppressWarnings("java:S1067")
    private boolean isAcknowledgementMessageFromSuperPeer(final Object msg) {
        return msg instanceof AddressedMessage<?, ?> &&
                ((AddressedMessage<AcknowledgementMessage, ?>) msg).message() instanceof AcknowledgementMessage &&
                ((AddressedMessage<?, ?>) msg).address() instanceof InetSocketAddress &&
                superPeers.containsKey(((AddressedMessage<AcknowledgementMessage, ?>) msg).message().getSender()) &&
                myPublicKey.equals(((AddressedMessage<AcknowledgementMessage, ?>) msg).message().getRecipient()) &&
                ((AddressedMessage<AcknowledgementMessage, ?>) msg).message().getTime() > currentTime.getAsLong() - pingTimeoutMillis;
    }

    private void handleAcknowledgementMessage(final ChannelHandlerContext ctx,
                                              final AcknowledgementMessage msg,
                                              final InetSocketAddress inetAddress) {
        final IdentityPublicKey publicKey = msg.getSender();
        LOG.trace("Got Acknowledgement ({}ms latency) from super peer `{}`.", () -> System.currentTimeMillis() - msg.getTime(), () -> publicKey);

        final long latency = currentTime.getAsLong() - msg.getTime();
        final SuperPeer superPeer = superPeers.get(publicKey);
        superPeer.acknowledgementReceived(latency);

        ctx.fireUserEventTriggered(AddPathAndSuperPeerEvent.of(publicKey, inetAddress, PATH));

        determineBestSuperPeer(ctx);
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
                ctx.fireUserEventTriggered(RemoveSuperPeerAndPathEvent.of(publicKey, PATH));
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

    private boolean isRoutableMessage(final Object msg) {
        return bestSuperPeer != null &&
                msg instanceof AddressedMessage &&
                ((AddressedMessage<?, ?>) msg).message() instanceof ApplicationMessage &&
                ((AddressedMessage<?, ?>) msg).address() instanceof IdentityPublicKey;
    }

    private void routeMessage(final ChannelHandlerContext ctx,
                              final ChannelPromise promise,
                              final ApplicationMessage msg) {
        final SuperPeer superPeer = superPeers.get(msg.getRecipient());
        if (superPeer != null) {
            LOG.trace("Message is addressed to one of our super peers. Route message for super peer `{}` to well-known address `{}`.", msg.getRecipient(), superPeer.inetAddress());
            ctx.write(new AddressedMessage<>(msg, superPeer.inetAddress()), promise);
        }
        else {
            final InetSocketAddress inetAddress = superPeers.get(bestSuperPeer).inetAddress();
            LOG.trace("No direct connection to message recipient. Use super peer as default gateway. Relay message for peer `{}` to super peer `{}` via well-known address `{}`.", msg.getRecipient(), bestSuperPeer, inetAddress);
            ctx.write(new AddressedMessage<>(msg, inetAddress), promise);
        }
    }

    static class SuperPeer {
        private final LongSupplier currentTime;
        private final long pingTimeoutMillis;
        private final InetSocketAddress inetAddress;
        long firstDiscoveryTime;
        long lastAcknowledgementTime;
        long latency;

        SuperPeer(final LongSupplier currentTime,
                  final long pingTimeoutMillis,
                  final InetSocketAddress inetAddress,
                  final long firstDiscoveryTime,
                  final long lastAcknowledgementTime,
                  final long latency) {
            this.currentTime = requireNonNull(currentTime);
            this.pingTimeoutMillis = pingTimeoutMillis;
            this.inetAddress = requireNonNull(inetAddress);
            this.firstDiscoveryTime = firstDiscoveryTime;
            this.lastAcknowledgementTime = lastAcknowledgementTime;
            this.latency = latency;
        }

        public SuperPeer(final LongSupplier currentTime,
                         final long pingTimeoutMillis,
                         final InetSocketAddress inetAddress) {
            this(currentTime, pingTimeoutMillis, inetAddress, 0L, 0L, 0L);
        }

        public InetSocketAddress inetAddress() {
            return inetAddress;
        }

        public void discoverySent() {
            if (this.firstDiscoveryTime == 0) {
                this.firstDiscoveryTime = currentTime.getAsLong();
            }
        }

        public void acknowledgementReceived(final long latency) {
            this.lastAcknowledgementTime = currentTime.getAsLong();
            this.latency = latency;
        }

        public boolean isStale() {
            return Math.max(firstDiscoveryTime, lastAcknowledgementTime) < currentTime.getAsLong() - pingTimeoutMillis;
        }
    }
}
