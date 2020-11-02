package org.drasyl.pipeline;

import org.drasyl.event.Event;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.address.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.identity.IdentityManager.POW_DIFFICULTY;

/**
 * This handler filters out all messages received with invalid proof of work.
 */
public class InvalidProofOfWorkFilter extends SimpleInboundHandler<Message, Event, Address> {
    public static final String INVALID_PROOF_OF_WORK_FILTER = "INVALID_PROOF_OF_WORK_FILTER";
    private static final Logger LOG = LoggerFactory.getLogger(InvalidProofOfWorkFilter.class);

    @Override
    protected void matchedEventTriggered(final HandlerContext ctx,
                                         final Event event,
                                         final CompletableFuture<Void> future) {
        ctx.fireEventTriggered(event, future);
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final Message msg,
                               final CompletableFuture<Void> future) {
        if (msg.getProofOfWork().isValid(msg.getSender(), POW_DIFFICULTY)) {
            ctx.fireRead(sender, msg, future);
        }
        else {
            LOG.trace("Message with invalid proof of work dropped: {}", msg);
            future.completeExceptionally(new Exception("Message with invalid proof of work dropped"));
        }
    }
}