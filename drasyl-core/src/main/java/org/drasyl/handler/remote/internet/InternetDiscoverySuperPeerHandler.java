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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.discovery.RemoveChildrenAndPathEvent;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.DiscoveryMessage;
import org.drasyl.handler.remote.protocol.HopCount;
import org.drasyl.handler.remote.protocol.RemoteMessage;
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
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requirePositive;
import static org.drasyl.util.RandomUtil.randomLong;

/**
 * Operates as a super peer allowing other nodes to join as children. Relays inbound/Route outbound
 * messages to children.
 *
 * @see InternetDiscoveryChildrenHandler
 */
@SuppressWarnings("unchecked")
public class InternetDiscoverySuperPeerHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(InternetDiscoverySuperPeerHandler.class);
    private static final Object PATH = InternetDiscoverySuperPeerHandler.class;
    protected final int myNetworkId;
    protected final IdentityPublicKey myPublicKey;
    protected final ProofOfWork myProofOfWork;
    private final LongSupplier currentTime;
    private final long pingIntervalMillis;
    private final long pingTimeoutMillis;
    private final long maxTimeOffsetMillis;
    protected final Map<DrasylAddress, ChildrenPeer> childrenPeers;
    private final HopCount hopLimit;
    Future<?> stalePeerCheckDisposable;

    @SuppressWarnings("java:S107")
    InternetDiscoverySuperPeerHandler(final int myNetworkId,
                                      final IdentityPublicKey myPublicKey,
                                      final ProofOfWork myProofOfWork,
                                      final LongSupplier currentTime,
                                      final long pingIntervalMillis,
                                      final long pingTimeoutMillis,
                                      final long maxTimeOffsetMillis,
                                      final Map<DrasylAddress, ChildrenPeer> childrenPeers,
                                      final HopCount hopLimit,
                                      final Future<?> stalePeerCheckDisposable) {
        this.myNetworkId = myNetworkId;
        this.myPublicKey = requireNonNull(myPublicKey);
        this.myProofOfWork = requireNonNull(myProofOfWork);
        this.currentTime = requireNonNull(currentTime);
        this.pingIntervalMillis = requirePositive(pingIntervalMillis);
        this.pingTimeoutMillis = requirePositive(pingTimeoutMillis);
        this.maxTimeOffsetMillis = requirePositive(maxTimeOffsetMillis);
        this.childrenPeers = requireNonNull(childrenPeers);
        this.hopLimit = requireNonNull(hopLimit);
        this.stalePeerCheckDisposable = stalePeerCheckDisposable;
    }

    public InternetDiscoverySuperPeerHandler(final int myNetworkId,
                                             final IdentityPublicKey myPublicKey,
                                             final ProofOfWork myProofOfWork,
                                             final long pingIntervalMillis,
                                             final long pingTimeoutMillis,
                                             final long maxTimeOffsetMillis,
                                             final HopCount hopLimit) {
        this(
                myNetworkId,
                myPublicKey,
                myProofOfWork,
                System::currentTimeMillis,
                pingIntervalMillis,
                pingTimeoutMillis,
                maxTimeOffsetMillis,
                new HashMap<>(),
                hopLimit,
                null
        );
    }

    /*
     * Channel Events
     */

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        startStalePeerCheck(ctx);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        stopStalePeerCheck();
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (isDiscoveryMessageWithChildrenTime(msg)) {
            final InetAddressedMessage<DiscoveryMessage> addressedMsg = (InetAddressedMessage<DiscoveryMessage>) msg;
            handleDiscoveryMessage(ctx, addressedMsg.content(), addressedMsg.sender());
        }
        else if (isRoutableInboundMessage(msg)) {
            final InetAddressedMessage<RemoteMessage> addressedMsg = (InetAddressedMessage<RemoteMessage>) msg;
            handleRoutableInboundMessage(ctx, addressedMsg);
        }
        else if (isUnexpectedMessage(msg)) {
            handleUnexpectedMessage(ctx, msg);
        }
        else {
            // unknown message type/recipient -> pass through
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (isRoutableOutboundMessage(msg)) {
            // for one of my children -> route
            final OverlayAddressedMessage<ApplicationMessage> addressedMsg = (OverlayAddressedMessage<ApplicationMessage>) msg;
            handleRoutableOutboundMessage(ctx, addressedMsg, promise);
        }
        else {
            // unknown message type/recipient -> pass through
            ctx.write(msg, promise);
        }
    }

    /*
     * Routing/Relaying
     */

    @SuppressWarnings({ "java:S1067", "java:S2325" })
    private boolean isRoutableInboundMessage(final Object msg) {
        return msg instanceof InetAddressedMessage<?> &&
                ((InetAddressedMessage<?>) msg).content() instanceof RemoteMessage &&
                !(((InetAddressedMessage<?>) msg).content() instanceof DiscoveryMessage && Math.abs(currentTime.getAsLong() - (((InetAddressedMessage<DiscoveryMessage>) msg).content()).getTime()) > maxTimeOffsetMillis);
    }

    private void handleRoutableInboundMessage(final ChannelHandlerContext ctx,
                                              final InetAddressedMessage<RemoteMessage> addressedMsg) {
        if (myPublicKey.equals(addressedMsg.content().getRecipient())) {
            // for me? -> pass through
            ctx.fireChannelRead(addressedMsg);
        }
        else {
            // for one of my children? -> try to relay
            final DrasylAddress address = addressedMsg.content().getRecipient();
            final ChildrenPeer childrenPeer = childrenPeers.get(address);
            if (childrenPeer != null) {
                final InetSocketAddress inetAddress = childrenPeer.inetAddress();
                relayMessage(ctx, addressedMsg, inetAddress);
            }
            else {
                LOG.trace("Got RemoteMessage for unknown peer `{}`. Drop it.", address);
                addressedMsg.release();
            }
        }
    }

    private boolean isRoutableOutboundMessage(final Object msg) {
        return msg instanceof OverlayAddressedMessage<?> &&
                ((OverlayAddressedMessage<?>) msg).content() instanceof ApplicationMessage &&
                childrenPeers.containsKey(((ApplicationMessage) ((OverlayAddressedMessage<?>) msg).content()).getRecipient());
    }

    private void handleRoutableOutboundMessage(final ChannelHandlerContext ctx,
                                               final OverlayAddressedMessage<ApplicationMessage> addressedMsg,
                                               final ChannelPromise promise) {
        final DrasylAddress address = addressedMsg.content().getRecipient();
        final InetSocketAddress inetAddress = childrenPeers.get(address).inetAddress();

        LOG.trace("Got ApplicationMessage for children peer `{}`. Resolve it to inet address `{}`.", address, inetAddress);
        ctx.write(addressedMsg.resolve(inetAddress), promise);
    }

    @SuppressWarnings("java:S2325")
    protected void relayMessage(final ChannelHandlerContext ctx,
                                final InetAddressedMessage<RemoteMessage> addressedMsg,
                                final InetSocketAddress inetAddress) {
        final RemoteMessage msg = addressedMsg.content();
        LOG.trace("Got RemoteMessage for children peer `{}`. Resolve it to inet address `{}`.", msg::getRecipient, () -> inetAddress);

        // increment hop count every time a message is relayed
        if (hopLimit.compareTo(msg.getHopCount()) > 0) {
            ctx.writeAndFlush((addressedMsg.route(inetAddress)).replace(msg.incrementHopCount()));
        }
        else {
            ReferenceCountUtil.release(addressedMsg);
            LOG.trace("Hop limit has been reached. Drop message.");
        }
    }

    /*
     * Pinging
     */

    void startStalePeerCheck(final ChannelHandlerContext ctx) {
        LOG.debug("Start StalePeerCheck job.");
        stalePeerCheckDisposable = ctx.executor().scheduleWithFixedDelay(() -> doStalePeerCheck(ctx), randomLong(pingIntervalMillis), pingIntervalMillis, MILLISECONDS);
    }

    void stopStalePeerCheck() {
        if (stalePeerCheckDisposable != null) {
            LOG.debug("Stop StalePeerCheck job.");
            stalePeerCheckDisposable.cancel(false);
            stalePeerCheckDisposable = null;
        }
    }

    void doStalePeerCheck(final ChannelHandlerContext ctx) {
        for (final Iterator<Entry<DrasylAddress, ChildrenPeer>> it = childrenPeers.entrySet().iterator();
             it.hasNext(); ) {
            final Entry<DrasylAddress, ChildrenPeer> entry = it.next();
            final DrasylAddress address = entry.getKey();
            final ChildrenPeer childrenPeer = entry.getValue();

            if (childrenPeer.isStale()) {
                LOG.trace("Children peer `{}` is stale. Remove from my neighbour list.", address);
                it.remove();
                ctx.fireUserEventTriggered(RemoveChildrenAndPathEvent.of(address, PATH));
            }
        }
    }

    @SuppressWarnings("java:S1067")
    private boolean isDiscoveryMessageWithChildrenTime(final Object msg) {
        return msg instanceof InetAddressedMessage<?> &&
                ((InetAddressedMessage<?>) msg).content() instanceof DiscoveryMessage &&
                myPublicKey.equals(((InetAddressedMessage<DiscoveryMessage>) msg).content().getRecipient()) &&
                (((InetAddressedMessage<DiscoveryMessage>) msg).content()).getChildrenTime() > 0 &&
                Math.abs(currentTime.getAsLong() - (((InetAddressedMessage<DiscoveryMessage>) msg).content()).getTime()) <= maxTimeOffsetMillis;
    }

    private void handleDiscoveryMessage(final ChannelHandlerContext ctx,
                                        final DiscoveryMessage msg,
                                        final InetSocketAddress inetAddress) {
        LOG.trace("Got Discovery from `{}`.", msg.getSender());

        final ChildrenPeer childrenPeer = childrenPeers.computeIfAbsent(msg.getSender(), k -> new ChildrenPeer(currentTime, pingTimeoutMillis, inetAddress));
        childrenPeer.discoveryReceived();
        ctx.fireUserEventTriggered(AddPathAndChildrenEvent.of(msg.getSender(), inetAddress, PATH));

        // reply with Acknowledgement
        final AcknowledgementMessage acknowledgementMsg = AcknowledgementMessage.of(myNetworkId, msg.getSender(), myPublicKey, myProofOfWork, msg.getTime());
        LOG.trace("Send Acknowledgement for peer `{}` to `{}`.", msg::getSender, () -> inetAddress);
        ctx.writeAndFlush(new InetAddressedMessage<>(acknowledgementMsg, inetAddress));
    }

    @SuppressWarnings({ "java:S1067", "java:S2325" })
    private boolean isUnexpectedMessage(final Object msg) {
        return msg instanceof InetAddressedMessage &&
                !(((InetAddressedMessage<?>) msg).content() instanceof ApplicationMessage) &&
                !(((InetAddressedMessage<?>) msg).content() instanceof DiscoveryMessage && ((InetAddressedMessage<DiscoveryMessage>) msg).content().getRecipient() == null) &&
                !(((InetAddressedMessage<?>) msg).content() instanceof DiscoveryMessage && Math.abs(currentTime.getAsLong() - (((InetAddressedMessage<DiscoveryMessage>) msg).content()).getTime()) <= maxTimeOffsetMillis);
    }

    @SuppressWarnings({ "unused", "java:S2325" })
    private void handleUnexpectedMessage(final ChannelHandlerContext ctx,
                                         final Object msg) {
        LOG.trace("Got unexpected message `{}`. Drop it.", msg);
        ReferenceCountUtil.release(msg);
    }

    static class ChildrenPeer {
        private final LongSupplier currentTime;
        private final long pingTimeoutMillis;
        private final InetSocketAddress inetAddress;
        long lastDiscoveryTime;

        ChildrenPeer(final LongSupplier currentTime,
                     final long pingTimeoutMillis,
                     final InetSocketAddress inetAddress,
                     final long lastDiscoveryTime) {
            this.currentTime = requireNonNull(currentTime);
            this.pingTimeoutMillis = pingTimeoutMillis;
            this.inetAddress = requireNonNull(inetAddress);
            this.lastDiscoveryTime = lastDiscoveryTime;
        }

        public ChildrenPeer(final LongSupplier currentTime,
                            final long pingTimeoutMillis,
                            final InetSocketAddress inetAddress) {
            this(currentTime, pingTimeoutMillis, inetAddress, 0L);
        }

        public InetSocketAddress inetAddress() {
            return inetAddress;
        }

        public void discoveryReceived() {
            this.lastDiscoveryTime = currentTime.getAsLong();
        }

        public boolean isStale() {
            return lastDiscoveryTime < currentTime.getAsLong() - pingTimeoutMillis;
        }
    }
}
