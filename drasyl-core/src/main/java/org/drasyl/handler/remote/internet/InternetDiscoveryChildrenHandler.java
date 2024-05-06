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

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.IdentityChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.DuplicatePathEventFilter;
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.handler.remote.UdpServer.UdpServerBound;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.DnsResolver;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requireNonNegative;

/**
 * Joins one or multiple super peer(s) as a children. Uses the super peer with the best RTT as a
 * default gateway for outbound messages.
 *
 * @see InternetDiscoverySuperPeerHandler
 */
@UnstableApi
@SuppressWarnings("unchecked")
public class InternetDiscoveryChildrenHandler extends ChannelDuplexHandler {
    private static final long DEFAULT_CHILDREN_TIME = 60; // seconds
    private static final Logger LOG = LoggerFactory.getLogger(InternetDiscoveryChildrenHandler.class);
    private static final Object PATH = InternetDiscoveryChildrenHandler.class;
    protected final LongSupplier currentTime;
    protected final Map<IdentityPublicKey, SuperPeer> superPeers;
    protected final DuplicatePathEventFilter pathEventFilter = new DuplicatePathEventFilter();
    private final long initialPingDelayMillis;
    Future<?> heartbeatDisposable;
    private IdentityPublicKey bestSuperPeer;
    protected InetSocketAddress bindAddress;

    @SuppressWarnings("java:S107")
    InternetDiscoveryChildrenHandler(final LongSupplier currentTime,
                                     final long initialPingDelayMillis,
                                     final Map<IdentityPublicKey, SuperPeer> superPeers,
                                     final Future<?> heartbeatDisposable,
                                     final IdentityPublicKey bestSuperPeer) {
        this.currentTime = requireNonNull(currentTime);
        this.initialPingDelayMillis = requireNonNegative(initialPingDelayMillis);
        this.superPeers = requireNonNull(superPeers);
        this.heartbeatDisposable = heartbeatDisposable;
        this.bestSuperPeer = bestSuperPeer;
    }

    @SuppressWarnings("java:S107")
    public InternetDiscoveryChildrenHandler(final long initialPingDelayMillis,
                                            final Map<IdentityPublicKey, InetSocketAddress> superPeerAddresses) {
        this(
                System::currentTimeMillis,
                initialPingDelayMillis,
                superPeerAddresses.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> new SuperPeer(System::currentTimeMillis, e.getValue()))),
                null,
                null
        );
    }

    @SuppressWarnings("java:S107")
    public InternetDiscoveryChildrenHandler(final Map<IdentityPublicKey, InetSocketAddress> superPeerAddresses) {
        this(0, superPeerAddresses);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws UnknownHostException {
        if (ctx.channel().isActive()) {
            startHeartbeat(ctx);
        }
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
        if (isAcknowledgementMessageFromSuperPeer(ctx, msg)) {
            final InetAddressedMessage<AcknowledgementMessage> addressedMsg = (InetAddressedMessage<AcknowledgementMessage>) msg;
            handleAcknowledgementMessage(ctx, addressedMsg.content(), addressedMsg.sender());
        }
        else if (isApplicationMessageForMe(ctx, msg)) {
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

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
        if (evt instanceof UdpServerBound) {
            bindAddress = ((UdpServerBound) evt).getBindAddress();
        }
        ctx.fireUserEventTriggered(evt);
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
        heartbeatDisposable = ctx.executor().scheduleWithFixedDelay(() -> doHeartbeat(ctx), initialPingDelayMillis, config(ctx).getHelloInterval().toMillis(), MILLISECONDS);
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

        // get own private address(es)
        final Set<InetSocketAddress> privateInetAddresses = getPrivateAddresses();

        // ping super peers
        superPeers.forEach(((publicKey, superPeer) -> {
            // if possible, resolve the given address every single time. This ensures, that we are aware of DNS record updates
            final InetSocketAddress resolvedAddress = superPeer.resolveInetAddress();

            writeHelloMessage(ctx, publicKey, resolvedAddress, privateInetAddresses);
        }));
        ctx.flush();
    }

    protected Set<InetSocketAddress> getPrivateAddresses() {
        // not used by this implementation. Only required for NAT traversal.
        return Set.of();
    }

    /**
     * Make sure to call {@link Channel#flush()} by your own!
     */
    protected void writeHelloMessage(final ChannelHandlerContext ctx,
                                     final DrasylAddress publicKey,
                                     final InetSocketAddress inetAddress,
                                     final Set<InetSocketAddress> privateInetAddresses) {
        final boolean isChildrenJoin = privateInetAddresses != null;
        final HelloMessage msg;
        if (isChildrenJoin) {
            // hello message is used to register at super peer as children
            msg = HelloMessage.of(config(ctx).getNetworkId(), publicKey, ((IdentityChannel) ctx.channel()).identity().getIdentityPublicKey(), ((IdentityChannel) ctx.channel()).identity().getProofOfWork(), DEFAULT_CHILDREN_TIME, ((IdentityChannel) ctx.channel()).identity().getIdentitySecretKey(), privateInetAddresses);
        }
        else {
            // hello message is used to announce us at peer
            msg = HelloMessage.of(config(ctx).getNetworkId(), publicKey, ((IdentityChannel) ctx.channel()).identity().getIdentityPublicKey(), ((IdentityChannel) ctx.channel()).identity().getProofOfWork());
        }

        LOG.trace("Send Hello `{}` (children = {}) for peer `{}` to `{}`.", () -> msg, () -> isChildrenJoin, () -> publicKey, () -> inetAddress);
        ctx.write(new InetAddressedMessage<>(msg, inetAddress)).addListener(future -> {
            if (!future.isSuccess()) {
                //noinspection unchecked
                LOG.warn("Unable to send Hello `{}` for peer `{}` to address `{}`:", () -> msg, () -> publicKey, () -> inetAddress, future::cause);
            }
        });
    }

    /*
     * Ponging
     */

    @SuppressWarnings({ "java:S1067", "SuspiciousMethodCalls" })
    private boolean isAcknowledgementMessageFromSuperPeer(final ChannelHandlerContext ctx,
                                                          final Object msg) {
        return msg instanceof InetAddressedMessage<?> &&
                ((InetAddressedMessage<?>) msg).content() instanceof AcknowledgementMessage &&
                config(ctx).getSuperPeers().keySet().contains(((InetAddressedMessage<AcknowledgementMessage>) msg).content().getSender()) &&
                ctx.channel().localAddress().equals(((InetAddressedMessage<AcknowledgementMessage>) msg).content().getRecipient()) &&
                Math.abs(currentTime.getAsLong() - (((InetAddressedMessage<AcknowledgementMessage>) msg).content()).getTime()) <= config(ctx).getMaxMessageAge().toMillis();
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

    private boolean isApplicationMessageForMe(final ChannelHandlerContext ctx,
                                              final Object msg) {
        return msg instanceof InetAddressedMessage<?> &&
                ((InetAddressedMessage<?>) msg).content() instanceof ApplicationMessage &&
                ctx.channel().localAddress().equals((((InetAddressedMessage<ApplicationMessage>) msg).content()).getRecipient());
    }

    @SuppressWarnings("java:S2325")
    private void handleApplicationMessage(final ChannelHandlerContext ctx,
                                          final InetAddressedMessage<ApplicationMessage> addressedMsg) {
        ctx.fireChannelRead(addressedMsg);
    }

    private void determineBestSuperPeer(final ChannelHandlerContext ctx) {
        IdentityPublicKey newBestSuperPeer = null;
        long bestRtt = Long.MAX_VALUE;
        for (final Entry<IdentityPublicKey, SuperPeer> entry : superPeers.entrySet()) {
            final IdentityPublicKey publicKey = entry.getKey();
            final SuperPeer superPeer = entry.getValue();
            if (!superPeer.isStale(ctx)) {
                if (superPeer.rtt < bestRtt) {
                    newBestSuperPeer = publicKey;
                    bestRtt = superPeer.rtt;
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
                    LOG.trace("New best super peer ({}ms RTT)! Replace `{}` with `{}`", bestRtt, oldBestSuperPeer, newBestSuperPeer);
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
        LOG.warn("Got unexpected message `{}`. Drop it.", msg);
        ReferenceCountUtil.release(msg);
    }

    protected static class SuperPeer {
        private final LongSupplier currentTime;
        long lastAcknowledgementTime;
        long rtt;
        private InetSocketAddress inetAddress;

        SuperPeer(final LongSupplier currentTime,
                  final InetSocketAddress inetAddress,
                  final long lastAcknowledgementTime,
                  final long rtt) {
            this.currentTime = requireNonNull(currentTime);
            this.inetAddress = requireNonNull(inetAddress);
            this.lastAcknowledgementTime = lastAcknowledgementTime;
            this.rtt = rtt;
        }

        SuperPeer(final LongSupplier currentTime,
                  final InetSocketAddress inetAddress) {
            this(currentTime, inetAddress, 0L, 0L);
        }

        public InetSocketAddress inetAddress() {
            return inetAddress;
        }

        public void acknowledgementReceived(final long rtt) {
            this.lastAcknowledgementTime = currentTime.getAsLong();
            this.rtt = rtt;
        }

        public boolean isStale(final ChannelHandlerContext ctx) {
            return lastAcknowledgementTime < currentTime.getAsLong() - config(ctx).getHelloTimeout().toMillis();
        }

        /**
         * Triggers a new resolve of the hostname into an {@link java.net.InetAddress}.
         */
        public InetSocketAddress resolveInetAddress() {
            try {
                final InetAddress resolvedAddress = DnsResolver.resolve(inetAddress.getHostString());
                inetAddress = new InetSocketAddress(resolvedAddress, inetAddress.getPort());
            }
            catch (final UnknownHostException e) {
                // keep existing address
                if (inetAddress.isUnresolved()) {
                    LOG.warn("Unable to resolve super peer address `{}`", inetAddress, e);
                }
            }
            return inetAddress;
        }
    }

    protected static DrasylServerChannelConfig config(final ChannelHandlerContext ctx) {
        return (DrasylServerChannelConfig) ctx.channel().config();
    }
}
