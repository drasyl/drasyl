package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Ask predecessor thread that periodically asks for predecessor's keep-alive, and delete
 * predecessor if it's dead.
 */
public class ChordAskPredecessor extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ChordAskPredecessor.class);
    private final ChordFingerTable fingerTable;
    private ScheduledFuture<?> askPredecessorTaskFuture;

    public ChordAskPredecessor(final ChordFingerTable fingerTable) {
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
                ChordUtil.requestKeep(ctx, fingerTable.getPredecessor()).addListener((FutureListener<Void>) future -> {
                    if (future.cause() != null) {
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
