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

package org.drasyl.pipeline;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.IdentityMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.WhoisMessage;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This handler processes inbound messages addressed to the local node.
 */
public class LoopbackInboundMessageSinkHandler extends SimpleInboundHandler<Message, Address> {
    public static final String LOOPBACK_INBOUND_MESSAGE_SINK_HANDLER = "LOOPBACK_INBOUND_MESSAGE_SINK_HANDLER";
    private final AtomicBoolean started;
    private final Set<Endpoint> endpoints;

    public LoopbackInboundMessageSinkHandler(final AtomicBoolean started,
                                             final Set<Endpoint> endpoints) {
        this.started = started;
        this.endpoints = endpoints;
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final Message msg,
                               final CompletableFuture<Void> future) {
        if (!ctx.identity().getPublicKey().equals(msg.getRecipient())) {
            future.completeExceptionally(new Exception("I'm not the recipient"));
        }
        else if (!started.get()) {
            future.completeExceptionally(new Exception("Node is not running"));
        }
        else if (msg instanceof ApplicationMessage) {
            ctx.peersManager().addPeer(msg.getSender());
            ctx.fireRead(sender, msg, future);
        }
        else if (msg instanceof WhoisMessage) {
            final WhoisMessage whoisMessage = (WhoisMessage) msg;
            ctx.peersManager().setPeerInformation(whoisMessage.getSender(),
                    whoisMessage.getPeerInformation());

            final CompressedPublicKey myPublicKey = ctx.identity().getPublicKey();
            final ProofOfWork myProofOfWork = ctx.identity().getProofOfWork();
            final PeerInformation myPeerInformation = PeerInformation.of(endpoints);
            final IdentityMessage identityMessage = new IdentityMessage(ctx.config().getNetworkId(), myPublicKey, myProofOfWork, whoisMessage.getSender(),
                    myPeerInformation, whoisMessage.getId());

            ctx.write(identityMessage.getRecipient(), identityMessage, future);
        }
        else if (msg instanceof IdentityMessage) {
            final IdentityMessage identityMessage = (IdentityMessage) msg;
            ctx.peersManager().setPeerInformation(identityMessage.getSender(),
                    identityMessage.getPeerInformation());
            future.complete(null);
        }
        else {
            future.completeExceptionally(new Exception("Unexpected message type " + msg.getClass().getSimpleName()));
        }
    }
}
