package org.drasyl.handler.dht.chord.task;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.FutureComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.computeRelativeChordId;
import static org.drasyl.handler.dht.chord.helper.ChordDeleteSuccessorHelper.deleteSuccessor;
import static org.drasyl.handler.dht.chord.helper.ChordFillSuccessorHelper.fillSuccessor;
import static org.drasyl.handler.dht.chord.requester.ChordIAmPreRequester.iAmPreRequest;
import static org.drasyl.handler.dht.chord.requester.ChordYourPredecessorRequester.yourPredecessorRequest;
import static org.drasyl.util.FutureComposer.composeFuture;
import static org.drasyl.util.UnexecutableFutureComposer.composeUnexecutableFuture;

/**
 * Stabilize thread that periodically asks successor for its predecessor and determine if current
 * node should update or delete its successor.
 */
public class ChordStabilizeTask extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ChordStabilizeTask.class);
    private final ChordFingerTable fingerTable;
    private ScheduledFuture<?> stabilizeTaskFuture;

    public ChordStabilizeTask(final ChordFingerTable fingerTable) {
        this.fingerTable = requireNonNull(fingerTable);
    }

    /*
     * Handler Events
     */

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            scheduleStabilizeTask(ctx);
        }
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        cancelStabilizeTask();
    }

    /*
     * Channel Events
     */

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        scheduleStabilizeTask(ctx);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        cancelStabilizeTask();
        ctx.fireChannelInactive();
    }

    /*
     * Stabilize Task
     */

    private void scheduleStabilizeTask(final ChannelHandlerContext ctx) {
        stabilizeTaskFuture = ctx.executor().schedule(() -> {
            LOG.debug("Ask successor for its predecessor and determine if we should update or delete our successor.");
            final IdentityPublicKey successor = fingerTable.getSuccessor();
            final FutureComposer<Void> voidFuture;
            if (successor == null || successor.equals(ctx.channel().localAddress())) {
                // Try to fill successor with candidates in finger table or even predecessor
                voidFuture = fillSuccessor(ctx, fingerTable).compose(ctx.executor());//fill
            }
            else {
                voidFuture = composeFuture(ctx.executor());
            }

            voidFuture.toFuture().addListener((FutureListener<Void>) future -> {
                if (successor != null && !successor.equals(ctx.channel().localAddress())) {
                    LOG.debug("Check if successor has still us a predecessor.");

                    // try to get my successor's predecessor
                    composeFuture(ctx.executor())
                            .then(yourPredecessorRequest(ctx, successor))
                            .chain(x -> {
                                // if bad connection with successor! delete successor
                                if (x == null) { // FIXME: oder cause?
                                    LOG.debug("Bad connection with successor. Delete successor from finger table.");
                                    return deleteSuccessor(ctx, fingerTable).compose(ctx.executor());
                                }

                                // else if successor's predecessor is not itself
                                else if (!x.equals(successor)) {
                                    if (x.equals(ctx.channel().localAddress())) {
                                        LOG.debug("Successor has still us as predecessor. All fine.");
                                    }
                                    else {
                                        LOG.debug("Successor's predecessor is {}.", x);
                                    }
                                    final long local_id = chordId(ctx.channel().localAddress());
                                    final long successor_relative_id = computeRelativeChordId(successor, local_id);
                                    final long x_relative_id = computeRelativeChordId(x, local_id);
                                    if (x_relative_id > 0 && x_relative_id < successor_relative_id) {
                                        LOG.debug("Successor's predecessor {} is closer then me. Use successor's predecessor as our new successor.", x);
                                        return fingerTable.updateIthFinger(ctx, 1, x).compose(ctx.executor());
                                    }
                                    else {
                                        return composeFuture(ctx.executor());
                                    }
                                }

                                // successor's predecessor is successor itself, then notify successor
                                else {
                                    LOG.debug("Successor's predecessor is successor itself, notify successor to set us as his predecessor.");
                                    if (!successor.equals(ctx.channel().localAddress())) {
                                        return composeUnexecutableFuture().then(iAmPreRequest(ctx, successor));
                                    }
                                    return composeFuture(ctx.executor());
                                }
                            })
                            .toFuture()
                            .addListener((FutureListener<Void>) future12 -> scheduleStabilizeTask(ctx));
                }
                else {
                    scheduleStabilizeTask(ctx);
                }
            });
        }, 500, MILLISECONDS);
    }

    private void cancelStabilizeTask() {
        if (stabilizeTaskFuture != null) {
            stabilizeTaskFuture.cancel(false);
            stabilizeTaskFuture = null;
        }
    }
}
