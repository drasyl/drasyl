package org.drasyl.handler.dht.chord.task;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.dht.chord.requester.ChordKeepRequester.keepRequest;
import static org.drasyl.util.UnexecutableFutureComposer.composeUnexecutableFuture;

/**
 * Ask predecessor thread that periodically asks for predecessor's keep-alive, and delete
 * predecessor if it's dead.
 */
public class ChordAskPredecessorTask extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ChordAskPredecessorTask.class);
    private final ChordFingerTable fingerTable;
    private ScheduledFuture<?> askPredecessorTaskFuture;

    public ChordAskPredecessorTask(final ChordFingerTable fingerTable) {
        this.fingerTable = requireNonNull(fingerTable);
    }

    /*
     * Handler Events
     */

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            scheduleAskPredecessorTask(ctx);
        }
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        cancelAskPredecessorTask();
    }

    /*
     * Channel Events
     */

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        scheduleAskPredecessorTask(ctx);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        cancelAskPredecessorTask();
        ctx.fireChannelInactive();
    }

    /*
     * Ask Predecessor Task
     */

    private void scheduleAskPredecessorTask(final ChannelHandlerContext ctx) {
        askPredecessorTaskFuture = ctx.executor().schedule(() -> {
            if (fingerTable.hasPredecessor()) {
                LOG.debug("Check if our predecessor is still alive.");
                composeUnexecutableFuture().then(keepRequest(ctx, fingerTable.getPredecessor())).compose(ctx.executor()).toFuture().addListener((FutureListener<Void>) future -> {
                    if (future.cause() != null) { // FIXME: oder NULL?
                        // timeout
                        LOG.info("Our predecessor is not longer alive. Clear predecessor.");
                        fingerTable.removePredecessor();
                    }
                    scheduleAskPredecessorTask(ctx);
                });
            }
            else {
                scheduleAskPredecessorTask(ctx);
            }
        }, 500, MILLISECONDS);
    }

    private void cancelAskPredecessorTask() {
        if (askPredecessorTaskFuture != null) {
            askPredecessorTaskFuture.cancel(false);
            askPredecessorTaskFuture = null;
        }
    }
}
