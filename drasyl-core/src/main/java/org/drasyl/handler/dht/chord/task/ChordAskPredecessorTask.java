package org.drasyl.handler.dht.chord.task;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.handler.dht.chord.ChordService;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Ask predecessor thread that periodically asks for predecessor's keep-alive, and delete
 * predecessor if it's dead.
 * <p>
 * This class is based on <a href="https://github.com/ChuanXia/Chord">Chord implementation of Chuan
 * Xia</a>.
 */
public class ChordAskPredecessorTask extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ChordAskPredecessorTask.class);
    private final ChordFingerTable fingerTable;
    private final long checkIntervalMillis;
    private ScheduledFuture<?> askPredecessorTaskFuture;

    public ChordAskPredecessorTask(final ChordFingerTable fingerTable,
                                   final long checkIntervalMillis) {
        this.fingerTable = requireNonNull(fingerTable);
        this.checkIntervalMillis = requirePositive(checkIntervalMillis);
    }

    public ChordAskPredecessorTask(final ChordFingerTable fingerTable) {
        this(fingerTable, 500);
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
                final ChordService service = ctx.pipeline().get(RmiClientHandler.class).lookup("ChordService", ChordService.class, fingerTable.getPredecessor());
                service.keep().addListener((FutureListener<Void>) future -> {
                    if (!future.isSuccess()) {
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
        }, checkIntervalMillis, MILLISECONDS);
    }

    private void cancelAskPredecessorTask() {
        if (askPredecessorTaskFuture != null) {
            askPredecessorTaskFuture.cancel(false);
            askPredecessorTaskFuture = null;
        }
    }
}
