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

import com.google.common.cache.CacheBuilder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.handler.remote.protocol.UniteMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.function.LongSupplier;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Extends {@link InternetDiscoverySuperPeerHandler} by performing a rendezvous on communication
 * between two children peers.
 *
 * @see TraversingInternetDiscoveryChildrenHandler
 */
public class TraversingInternetDiscoverySuperPeerHandler extends InternetDiscoverySuperPeerHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TraversingInternetDiscoverySuperPeerHandler.class);
    private final Map<Pair<IdentityPublicKey, IdentityPublicKey>, Boolean> uniteAttemptsCache;

    @SuppressWarnings("java:S107")
    TraversingInternetDiscoverySuperPeerHandler(final int myNetworkId,
                                                final IdentityPublicKey myPublicKey,
                                                final ProofOfWork myProofOfWork,
                                                final LongSupplier currentTime,
                                                final long pingIntervalMillis,
                                                final long pingTimeoutMillis,
                                                final Map<IdentityPublicKey, ChildrenPeer> childrenPeers,
                                                final Future<?> stalePeerCheckDisposable,
                                                final Map<Pair<IdentityPublicKey, IdentityPublicKey>, Boolean> uniteAttemptsCache) {
        super(myNetworkId, myPublicKey, myProofOfWork, currentTime, pingIntervalMillis, pingTimeoutMillis, childrenPeers, stalePeerCheckDisposable);
        this.uniteAttemptsCache = requireNonNull(uniteAttemptsCache);
    }

    public TraversingInternetDiscoverySuperPeerHandler(final int myNetworkId,
                                                       final IdentityPublicKey myPublicKey,
                                                       final ProofOfWork myProofOfWork,
                                                       final long pingIntervalMillis,
                                                       final long pingTimeoutMillis,
                                                       final long uniteMinIntervalMillis) {
        super(myNetworkId, myPublicKey, myProofOfWork, pingIntervalMillis, pingTimeoutMillis);
        if (uniteMinIntervalMillis > 0) {
            uniteAttemptsCache = CacheBuilder.newBuilder()
                    .maximumSize(1_000)
                    .expireAfterWrite(uniteMinIntervalMillis, MILLISECONDS)
                    .<Pair<IdentityPublicKey, IdentityPublicKey>, Boolean>build()
                    .asMap();
        }
        else {
            uniteAttemptsCache = null;
        }
    }

    @Override
    protected void relayMessage(final ChannelHandlerContext ctx,
                                final AddressedMessage<RemoteMessage, InetSocketAddress> msg,
                                final InetSocketAddress inetAddress) {
        super.relayMessage(ctx, msg, inetAddress);

        final IdentityPublicKey senderKey = msg.message().getSender();
        final IdentityPublicKey recipientKey = msg.message().getRecipient();
        if (shouldInitiateRendezvous(senderKey, recipientKey)) {
            initiateRendezvous(ctx, senderKey, recipientKey);
        }
    }

    private boolean shouldInitiateRendezvous(final IdentityPublicKey sender,
                                             final IdentityPublicKey recipient) {
        if (uniteAttemptsCache == null) {
            return true;
        }

        final Pair<IdentityPublicKey, IdentityPublicKey> key;
        if (sender.hashCode() > recipient.hashCode()) {
            key = Pair.of(sender, recipient);
        }
        else {
            key = Pair.of(recipient, sender);
        }
        return uniteAttemptsCache.putIfAbsent(key, TRUE) == null;
    }

    private void initiateRendezvous(final ChannelHandlerContext ctx,
                                    final IdentityPublicKey senderKey,
                                    final IdentityPublicKey recipientKey) {
        final ChildrenPeer sender = childrenPeers.get(senderKey);
        final ChildrenPeer recipient = childrenPeers.get(recipientKey);

        if (sender != null && recipient != null) {
            LOG.trace("The clients `{}` and `{}` wants to communicate with each other. Initiate rendezvous so that they try to establish a direct connecting.", () -> senderKey, () -> recipientKey);

            final InetSocketAddress senderAddress = sender.inetAddress();
            final InetSocketAddress recipientAddress = recipient.inetAddress();

            // send recipient's information to sender
            final UniteMessage senderUnite = UniteMessage.of(myNetworkId, senderKey, myPublicKey, myProofOfWork, recipientKey, recipientAddress);
            LOG.trace("Send Unite for peer `{}` to `{}`.", () -> senderKey, () -> senderAddress);
            ctx.write(new AddressedMessage<>(senderUnite, senderAddress)).addListener(future -> {
                if (!future.isSuccess()) {
                    LOG.warn("Unable to send Unite for peer `{}` to `{}`", () -> senderKey, () -> senderAddress, future::cause);
                }
            });

            // send sender's information to recipient
            final UniteMessage recipientUnite = UniteMessage.of(myNetworkId, recipientKey, myPublicKey, myProofOfWork, senderKey, senderAddress);
            LOG.trace("Send Unite for peer `{}` to `{}`.", () -> recipientKey, () -> recipientAddress);
            ctx.write(new AddressedMessage<>(recipientUnite, recipientAddress)).addListener(future -> {
                if (!future.isSuccess()) {
                    LOG.warn("Unable to send Unite for peer `{}` to `{}`", () -> recipientKey, () -> recipientAddress, future::cause);
                }
            });

            ctx.flush();
        }
    }
}
