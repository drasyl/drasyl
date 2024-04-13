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
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.remote.UdpServer.UdpServerBound;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
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
    static final Class<?> PATH_ID = InternetDiscoveryChildrenHandler.class;
    static final short PATH_PRIORITY = 100;
    protected final LongSupplier currentTime;
    protected Map<IdentityPublicKey, SuperPeer> superPeers;
    private final long initialPingDelayMillis;
    Future<?> heartbeatDisposable;
    protected InetSocketAddress bindAddress;
    private boolean initialized;

    @SuppressWarnings("java:S107")
    InternetDiscoveryChildrenHandler(final LongSupplier currentTime,
                                     final Long initialPingDelayMillis,
                                     final Future<?> heartbeatDisposable) {
        this.currentTime = requireNonNull(currentTime);
        this.initialPingDelayMillis = initialPingDelayMillis;
        this.heartbeatDisposable = heartbeatDisposable;
    }

    @SuppressWarnings("java:S107")
    public InternetDiscoveryChildrenHandler(final LongSupplier currentTime,
                                            final long initialPingDelayMillis) {
        this(
                currentTime,
                initialPingDelayMillis,
                null
        );
    }

    @SuppressWarnings("java:S107")
    public InternetDiscoveryChildrenHandler(final long initialPingDelayMillis) {
        this(
                System::currentTimeMillis,
                initialPingDelayMillis
        );
    }

    @SuppressWarnings("java:S107")
    public InternetDiscoveryChildrenHandler() {
        this(0);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
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
        else if (isUnexpectedMessage(msg)) {
            handleUnexpectedMessage(ctx, msg);
        }
        else {
            // pass through
            ctx.fireChannelRead(msg);
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
        if (!initialized) {
            initialized = true;
            superPeers = config(ctx).getSuperPeers().entrySet().stream()
                    .collect(Collectors.toMap(Entry::getKey, e -> new SuperPeer(currentTime, config(ctx).getHelloTimeout().toMillis(), e.getValue())));
            LOG.debug("Start Heartbeat job.");
            // populate initial state (RemoveSuperPeerAndPathEvent) for all super peers to our path event filter
            for (final Entry<IdentityPublicKey, SuperPeer> entry : superPeers.entrySet()) {
                final IdentityPublicKey publicKey = entry.getKey();
                config(ctx).getPeersManager().removePath(publicKey, PATH_ID);
            }
            heartbeatDisposable = ctx.executor().scheduleWithFixedDelay(() -> doHeartbeat(ctx), initialPingDelayMillis, config(ctx).getHelloInterval().toMillis(), MILLISECONDS);
        }
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
            msg = HelloMessage.of(config(ctx).getNetworkId(), publicKey, ((DrasylServerChannel) ctx.channel()).identity().getIdentityPublicKey(), ((DrasylServerChannel) ctx.channel()).identity().getProofOfWork(), DEFAULT_CHILDREN_TIME, ((DrasylServerChannel) ctx.channel()).identity().getIdentitySecretKey(), privateInetAddresses);
        }
        else {
            // hello message is used to announce us at peer
            msg = HelloMessage.of(config(ctx).getNetworkId(), publicKey, ((DrasylServerChannel) ctx.channel()).identity().getIdentityPublicKey(), ((DrasylServerChannel) ctx.channel()).identity().getProofOfWork());
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
    private boolean isAcknowledgementMessageFromSuperPeer(ChannelHandlerContext ctx, final Object msg) {
        return msg instanceof InetAddressedMessage<?> &&
                ((InetAddressedMessage<?>) msg).content() instanceof AcknowledgementMessage &&
                superPeers.containsKey(((InetAddressedMessage<AcknowledgementMessage>) msg).content().getSender()) &&
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
        if (!config(ctx).getPeersManager().hasDefaultPeer()) {
            config(ctx).getPeersManager().setDefaultPath(publicKey);
        }

        if (config(ctx).getPeersManager().addPath(publicKey, PATH_ID, inetAddress, PATH_PRIORITY)) {
            ctx.fireUserEventTriggered(AddPathAndSuperPeerEvent.of(publicKey, inetAddress, PATH_ID, rtt));
        }
        else {
            ctx.fireUserEventTriggered(PathRttEvent.of(publicKey, inetAddress, PATH_ID, rtt));
        }

        determineBestSuperPeer(ctx);
    }

    private void determineBestSuperPeer(final ChannelHandlerContext ctx) {
        IdentityPublicKey newBestSuperPeer = null;
        long bestRtt = Long.MAX_VALUE;
        final PeersManager peersManager = config(ctx).getPeersManager();
        for (final Entry<IdentityPublicKey, SuperPeer> entry : superPeers.entrySet()) {
            final IdentityPublicKey publicKey = entry.getKey();
            final SuperPeer superPeer = entry.getValue();
            if (!superPeer.isStale()) {
                if (superPeer.rtt < bestRtt) {
                    newBestSuperPeer = publicKey;
                    bestRtt = superPeer.rtt;
                }
            }
            else {
                if (peersManager.removePath(publicKey, PATH_ID)) {
                    ctx.fireUserEventTriggered(RemoveSuperPeerAndPathEvent.of(publicKey, PATH_ID));
                }
            }
        }

        if (!Objects.equals(peersManager.getDefaultPeer(), newBestSuperPeer)) {
            final DrasylAddress oldBestSuperPeer = peersManager.getDefaultPeer();
            if (newBestSuperPeer != null) {
                peersManager.setDefaultPath(newBestSuperPeer);
            }
            else {
                peersManager.unsetDefaultPath();
            }
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

    protected static DrasylServerChannelConfig config(final ChannelHandlerContext ctx) {
        return (DrasylServerChannelConfig) ctx.channel().config();
    }

    protected static class SuperPeer {
        private final LongSupplier currentTime;
        private final long pingTimeoutMillis;
        long lastAcknowledgementTime;
        long rtt;
        private InetSocketAddress inetAddress;

        SuperPeer(final LongSupplier currentTime,
                  final long pingTimeoutMillis,
                  final InetSocketAddress inetAddress,
                  final long lastAcknowledgementTime,
                  final long rtt) {
            this.currentTime = requireNonNull(currentTime);
            this.pingTimeoutMillis = pingTimeoutMillis;
            this.inetAddress = requireNonNull(inetAddress);
            this.lastAcknowledgementTime = lastAcknowledgementTime;
            this.rtt = rtt;
        }

        SuperPeer(final LongSupplier currentTime,
                  final long pingTimeoutMillis,
                  final InetSocketAddress inetAddress) {
            this(currentTime, pingTimeoutMillis, inetAddress, 0L, 0L);
        }

        public InetSocketAddress inetAddress() {
            return inetAddress;
        }

        public void acknowledgementReceived(final long rtt) {
            this.lastAcknowledgementTime = currentTime.getAsLong();
            this.rtt = rtt;
        }

        public boolean isStale() {
            return lastAcknowledgementTime < currentTime.getAsLong() - pingTimeoutMillis;
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
}
