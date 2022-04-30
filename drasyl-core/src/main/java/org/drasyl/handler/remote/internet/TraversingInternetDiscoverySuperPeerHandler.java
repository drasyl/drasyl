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
import io.netty.util.concurrent.Future;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.HopCount;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.handler.remote.protocol.UniteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.ExpiringSet;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

import static java.util.Objects.requireNonNull;

/**
 * Extends {@link InternetDiscoverySuperPeerHandler} by performing a rendezvous on communication
 * between two children peers.
 *
 * @see TraversingInternetDiscoveryChildrenHandler
 */
public class TraversingInternetDiscoverySuperPeerHandler extends InternetDiscoverySuperPeerHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TraversingInternetDiscoverySuperPeerHandler.class);
    private final Set<Pair<DrasylAddress, DrasylAddress>> uniteAttemptsCache;

    @SuppressWarnings("java:S107")
    TraversingInternetDiscoverySuperPeerHandler(final int myNetworkId,
                                                final IdentityPublicKey myPublicKey,
                                                final ProofOfWork myProofOfWork,
                                                final LongSupplier currentTime,
                                                final long pingIntervalMillis,
                                                final long pingTimeoutMillis,
                                                final long maxTimeOffsetMillis,
                                                final HopCount hopLimit,
                                                final Map<DrasylAddress, ChildrenPeer> childrenPeers,
                                                final Future<?> stalePeerCheckDisposable,
                                                final Set<Pair<DrasylAddress, DrasylAddress>> uniteAttemptsCache) {
        super(myNetworkId, myPublicKey, myProofOfWork, currentTime, pingIntervalMillis, pingTimeoutMillis, maxTimeOffsetMillis, childrenPeers, hopLimit, stalePeerCheckDisposable);
        this.uniteAttemptsCache = requireNonNull(uniteAttemptsCache);
    }

    @SuppressWarnings("java:S107")
    public TraversingInternetDiscoverySuperPeerHandler(final int myNetworkId,
                                                       final IdentityPublicKey myPublicKey,
                                                       final ProofOfWork myProofOfWork,
                                                       final long pingIntervalMillis,
                                                       final long pingTimeoutMillis,
                                                       final long maxTimeOffsetMillis,
                                                       final HopCount hopLimit,
                                                       final long uniteMinIntervalMillis) {
        super(myNetworkId, myPublicKey, myProofOfWork, pingIntervalMillis, pingTimeoutMillis, maxTimeOffsetMillis, hopLimit);
        if (uniteMinIntervalMillis > 0) {
            uniteAttemptsCache = new ExpiringSet<>(1_000, uniteMinIntervalMillis);
        }
        else {
            uniteAttemptsCache = null;
        }
    }

    @Override
    protected void relayMessage(final ChannelHandlerContext ctx,
                                final InetAddressedMessage<RemoteMessage> addressedMsg,
                                final InetSocketAddress inetAddress) {
        super.relayMessage(ctx, addressedMsg, inetAddress);

        final DrasylAddress senderKey = addressedMsg.content().getSender();
        final DrasylAddress recipientKey = addressedMsg.content().getRecipient();
        if (shouldInitiateRendezvous(senderKey, recipientKey)) {
            initiateRendezvous(ctx, senderKey, recipientKey);
        }
    }

    private boolean shouldInitiateRendezvous(final DrasylAddress sender,
                                             final DrasylAddress recipient) {
        if (uniteAttemptsCache == null) {
            return false;
        }

        final Pair<DrasylAddress, DrasylAddress> key;
        if (sender.hashCode() > recipient.hashCode()) {
            key = Pair.of(sender, recipient);
        }
        else {
            key = Pair.of(recipient, sender);
        }
        return !uniteAttemptsCache.contains(key) && uniteAttemptsCache.add(key);
    }

    @SuppressWarnings("unchecked")
    private void initiateRendezvous(final ChannelHandlerContext ctx,
                                    final DrasylAddress senderKey,
                                    final DrasylAddress recipientKey) {
        final ChildrenPeer sender = childrenPeers.get(senderKey);
        final ChildrenPeer recipient = childrenPeers.get(recipientKey);

        if (sender != null && recipient != null) {
            LOG.trace("The clients `{}` and `{}` wants to communicate with each other. Initiate rendezvous so that they try to establish a direct connecting.", () -> senderKey, () -> recipientKey);

            final Set<InetSocketAddress> senderAddressCandidates = sender.inetAddressCandidates();
            final Set<InetSocketAddress> recipientAddressCandidates = recipient.inetAddressCandidates();

            // send recipient's information to sender
            final UniteMessage senderUnite = UniteMessage.of(myNetworkId, senderKey, myPublicKey, myProofOfWork, recipientKey, recipientAddressCandidates);
            LOG.trace("Send Unite for peer `{}` to `{}`.", () -> senderKey, sender::publicInetAddress);
            ctx.write(new InetAddressedMessage<>(senderUnite, sender.publicInetAddress())).addListener(future -> {
                if (!future.isSuccess()) {
                    LOG.warn("Unable to send Unite for peer `{}` to `{}`", () -> senderKey, sender::publicInetAddress, future::cause);
                }
            });

            // send sender's information to recipient
            final UniteMessage recipientUnite = UniteMessage.of(myNetworkId, recipientKey, myPublicKey, myProofOfWork, senderKey, senderAddressCandidates);
            LOG.trace("Send Unite for peer `{}` to `{}`.", () -> recipientKey, recipient::publicInetAddress);
            ctx.write(new InetAddressedMessage<>(recipientUnite, recipient.publicInetAddress())).addListener(future -> {
                if (!future.isSuccess()) {
                    LOG.warn("Unable to send Unite for peer `{}` to `{}`", () -> recipientKey, recipient::publicInetAddress, future::cause);
                }
            });

            ctx.flush();
        }
    }
}
