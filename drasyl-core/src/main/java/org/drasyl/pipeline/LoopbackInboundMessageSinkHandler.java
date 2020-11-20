package org.drasyl.pipeline;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
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
    private final PeersManager peersManager;
    private final Set<Endpoint> endpoints;

    public LoopbackInboundMessageSinkHandler(final AtomicBoolean started,
                                             final PeersManager peersManager,
                                             final Set<Endpoint> endpoints) {
        this.started = started;
        this.peersManager = peersManager;
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
            peersManager.addPeer(msg.getSender());
            ctx.fireRead(sender, msg, future);
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

            ctx.write(identityMessage.getRecipient(), identityMessage, future);
        }
        else if (msg instanceof IdentityMessage) {
            final IdentityMessage identityMessage = (IdentityMessage) msg;
            peersManager.setPeerInformation(identityMessage.getSender(),
                    identityMessage.getPeerInformation());
            future.complete(null);
        }
        else {
            future.completeExceptionally(new Exception("Unexpected message type " + msg.getClass().getSimpleName()));
        }
    }
}
