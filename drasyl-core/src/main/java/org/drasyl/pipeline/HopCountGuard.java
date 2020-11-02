package org.drasyl.pipeline;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * This handler ensures that {@link Message}s do not infinitely circulate in the network. It
 * increments the hop counter of each outgoing message. If the limit of hops is reached, the message
 * is discarded. Otherwise the message can pass.
 */
public class HopCountGuard extends SimpleOutboundHandler<Message, CompressedPublicKey> {
    public static final String HOP_COUNT_GUARD = "HOP_COUNT_GUARD";
    private static final Logger LOG = LoggerFactory.getLogger(HopCountGuard.class);

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final CompressedPublicKey recipient,
                                final Message msg,
                                final CompletableFuture<Void> future) {
        if (msg.getHopCount() < ctx.config().getMessageHopLimit()) {
            // route message to next hop (node)
            msg.incrementHopCount();

            ctx.write(recipient, msg, future);
        }
        else {
            // too many hops, discard message
            if (LOG.isDebugEnabled()) {
                LOG.debug("Hop Count limit has been reached. End of lifespan of message has been reached. Discard message '{}'", msg);
            }

            future.completeExceptionally(new Exception("Hop Count limit has been reached. End of lifespan of message has been reached. Discard message."));
        }
    }
}
