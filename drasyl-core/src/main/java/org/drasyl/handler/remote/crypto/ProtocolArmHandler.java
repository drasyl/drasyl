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
package org.drasyl.handler.remote.crypto;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.sodium.SessionPair;
import org.drasyl.handler.remote.protocol.ArmedProtocolMessage;
import org.drasyl.handler.remote.protocol.FullReadMessage;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.ExpiringMap;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ProtocolArmHandler extends MessageToMessageCodec<InetAddressedMessage<ArmedProtocolMessage>, InetAddressedMessage<FullReadMessage<?>>> {
    private static final Logger LOG = LoggerFactory.getLogger(ProtocolArmHandler.class);
    private final Identity myIdentity;
    private final Map<IdentityPublicKey, SessionPair> sessions;
    private final Crypto crypto;

    public ProtocolArmHandler(final Identity myIdentity,
                              final Crypto crypto,
                              final int maxSessionsCount,
                              final Duration expireAfter) {
        this.myIdentity = myIdentity;
        this.sessions = new ExpiringMap<>(maxSessionsCount, -1, expireAfter.toMillis());
        this.crypto = crypto;
    }

    public ProtocolArmHandler(final Identity myIdentity,
                              final int maxSessionsCount) {
        this(myIdentity, Crypto.INSTANCE, maxSessionsCount, Duration.ZERO);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        this.sessions.clear();

        super.channelInactive(ctx);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean acceptInboundMessage(final Object msg) {
        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof ArmedProtocolMessage) {
            final ArmedProtocolMessage armedMessage = ((InetAddressedMessage<ArmedProtocolMessage>) msg).content();

            return Objects.equals(myIdentity.getIdentityPublicKey(), armedMessage.getRecipient())
                    && !Objects.equals(myIdentity.getIdentityPublicKey(), armedMessage.getSender());
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean acceptOutboundMessage(final Object msg) {
        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof FullReadMessage) {
            final FullReadMessage<?> fullReadMessage = ((InetAddressedMessage<FullReadMessage<?>>) msg).content();

            return fullReadMessage.getRecipient() != null &&
                    Objects.equals(myIdentity.getIdentityPublicKey(), fullReadMessage.getSender()) &&
                    !Objects.equals(myIdentity.getIdentityPublicKey(), fullReadMessage.getRecipient());
        }

        return false;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final InetAddressedMessage<FullReadMessage<?>> msg,
                          final List<Object> out) throws Exception {
        final SessionPair session = getOrComputeSession((IdentityPublicKey) msg.content().getRecipient());
        final ArmedProtocolMessage armedMessage = msg.content().arm(ctx.alloc().ioBuffer(), crypto, session);
        out.add(msg.replace(armedMessage));
        LOG.trace("Armed protocol msg: {}", armedMessage);
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final InetAddressedMessage<ArmedProtocolMessage> msg,
                          final List<Object> out) throws Exception {
        final SessionPair session = getOrComputeSession((IdentityPublicKey) msg.content().getSender());
        final FullReadMessage<?> disarmedMessage = msg.content().disarm(crypto, session);
        out.add(msg.replace(disarmedMessage));
        LOG.trace("Disarmed protocol msg: {}", disarmedMessage);
    }

    private SessionPair getOrComputeSession(final IdentityPublicKey peer) throws CryptoException {
        Objects.requireNonNull(peer);
        SessionPair pair = sessions.get(peer); // NOSONAR
        if (pair == null) {
            pair = crypto.generateSessionKeyPair(myIdentity.getKeyAgreementKeyPair(), peer.getLongTimeKeyAgreementKey());
            sessions.put(peer, pair);
        }

        return pair;
    }
}
