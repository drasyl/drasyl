package org.drasyl.pipeline;

import org.drasyl.event.Event;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.address.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * This handler filters out all messages received from other networks.
 */
public class OtherNetworkFilter extends SimpleInboundHandler<Message, Event, Address> {
    public static final String OTHER_NETWORK_FILTER = "OTHER_NETWORK_FILTER";
    private static final Logger LOG = LoggerFactory.getLogger(OtherNetworkFilter.class);

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
        if (ctx.config().getNetworkId() == msg.getNetworkId()) {
            ctx.fireRead(sender, msg, future);
        }
        else {
            LOG.trace("Message from other network dropped: {}", msg);
            future.completeExceptionally(new Exception("Message from other network dropped"));
        }
    }
}
