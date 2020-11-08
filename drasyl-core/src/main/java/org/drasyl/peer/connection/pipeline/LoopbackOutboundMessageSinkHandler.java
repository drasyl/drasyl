/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.peer.connection.pipeline;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.IdentityMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.WhoisMessage;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.SimpleOutboundHandler;
import org.drasyl.util.FutureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This handler processes outbound messages addressed to the local node.
 */
public class LoopbackOutboundMessageSinkHandler extends SimpleOutboundHandler<Message, CompressedPublicKey> {
    public static final String LOOPBACK_OUTBOUND_MESSAGE_SINK_HANDLER = "LOOPBACK_OUTBOUND_MESSAGE_SINK_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(LoopbackOutboundMessageSinkHandler.class);
    private final AtomicBoolean started;
    private final PeersManager peersManager;
    private final Set<Endpoint> endpoints;

    public LoopbackOutboundMessageSinkHandler(final AtomicBoolean started,
                                              final PeersManager peersManager,
                                              final Set<Endpoint> endpoints) {
        this.started = started;
        this.peersManager = peersManager;
        this.endpoints = endpoints;
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final CompressedPublicKey recipient,
                                final Message msg,
                                final CompletableFuture<Void> future) {
        if (!started.get()) {
            ctx.write(recipient, msg, future);
        }
        else if (!ctx.identity().getPublicKey().equals(recipient)) {
            ctx.write(recipient, msg, future);
        }
        else if (msg instanceof ApplicationMessage) {
            peersManager.addPeer(msg.getSender());
            FutureUtil.completeOnAllOf(future, ctx.pipeline().processInbound(msg));
        }
        else if (msg instanceof WhoisMessage) {
            final WhoisMessage whoisMessage = (WhoisMessage) msg;
            peersManager.setPeerInformation(whoisMessage.getSender(),
                    whoisMessage.getPeerInformation());

            final CompressedPublicKey myPublicKey = ctx.identity().getPublicKey();
            final ProofOfWork myProofOfWork = ctx.identity().getProofOfWork();
            final PeerInformation myPeerInformation = PeerInformation.of(endpoints);
            final IdentityMessage identityMessage = new IdentityMessage(ctx.config().getNetworkId(), myPublicKey, myProofOfWork, whoisMessage.getSender(),
                    myPeerInformation, whoisMessage.getId());

            FutureUtil.completeOnAllOf(future, ctx.pipeline().processOutbound(identityMessage.getRecipient(), identityMessage));
        }
        else if (msg instanceof IdentityMessage) {
            final IdentityMessage identityMessage = (IdentityMessage) msg;
            peersManager.setPeerInformation(identityMessage.getSender(),
                    identityMessage.getPeerInformation());
            future.complete(null);
        }
        else {
            LOG.debug("LoopbackMessageSinkHandler is not able to handle messages of type {}", msg.getClass().getSimpleName());
            ctx.write(recipient, msg, future);
        }
    }
}
