package org.drasyl.handler.dht.chord.task;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.relativeChordId;
import static org.drasyl.handler.dht.chord.helper.ChordDeleteSuccessorHelper.deleteSuccessor;
import static org.drasyl.handler.dht.chord.helper.ChordFillSuccessorHelper.fillSuccessor;
import static org.drasyl.handler.dht.chord.requester.ChordIAmPreRequester.iAmPreRequest;
import static org.drasyl.handler.dht.chord.requester.ChordYourPredecessorRequester.requestPredecessor;
import static org.drasyl.util.FutureComposer.composeFuture;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Handler class providing {@code n.stabilize()} functionality that periodically asks successor for
 * its predecessor and determine if current node should update or delete its successor.
 * <p>
 * This class is based on <a href="https://github.com/ChuanXia/Chord">Chord implementation of Chuan
 * Xia</a>.
 */
public class ChordStabilizeTask extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ChordStabilizeTask.class);
    private final ChordFingerTable fingerTable;
    private final long checkIntervalMillis;
    private ScheduledFuture<?> stabilizeTaskFuture;

    public ChordStabilizeTask(final ChordFingerTable fingerTable, final long checkIntervalMillis) {
        this.fingerTable = requireNonNull(fingerTable);
        this.checkIntervalMillis = requirePositive(checkIntervalMillis);
    }

    public ChordStabilizeTask(final ChordFingerTable fingerTable) {
        this(fingerTable, 500);
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
            final DrasylAddress successor = fingerTable.getSuccessor();
            final FutureComposer<Void> voidFuture;
            if (successor == null || successor.equals(ctx.channel().localAddress())) {
                // Try to fill successor with candidates in finger table or even predecessor
                voidFuture = fillSuccessor(ctx, fingerTable);//fill
            }
            else {
                voidFuture = composeFuture();
            }

            voidFuture.finish(ctx.executor()).addListener((FutureListener<Void>) future -> {
                if (successor != null && !successor.equals(ctx.channel().localAddress())) {
                    LOG.debug("Check if successor has still us a predecessor.");

                    // try to get my successor's predecessor
                    requestPredecessor(ctx, successor)
                            .chain(future2 -> {
                                // if bad connection with successor! delete successor
                                DrasylAddress x = future2.getNow();
                                if (x == null) {
                                    LOG.debug("Bad connection with successor. Delete successor from finger table.");
                                    return deleteSuccessor(ctx, fingerTable);
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
                                    final long successor_relative_id = relativeChordId(successor, local_id);
                                    final long x_relative_id = relativeChordId(x, local_id);
                                    if (x_relative_id > 0 && x_relative_id < successor_relative_id) {
                                        LOG.debug("Successor's predecessor {} is closer then me. Use successor's predecessor as our new successor.", x);
                                        return fingerTable.updateIthFinger(ctx, 1, x);
                                    }
                                    else {
                                        return composeFuture();
                                    }
                                }

                                // successor's predecessor is successor itself, then notify successor
                                else {
                                    LOG.debug("Successor's predecessor is successor itself, notify successor to set us as his predecessor.");
                                    if (!successor.equals(ctx.channel().localAddress())) {
                                        return iAmPreRequest(ctx, successor);
                                    }
                                    return composeFuture();
                                }
                            })
                            .finish(ctx.executor())
                            .addListener((FutureListener<Void>) future12 -> scheduleStabilizeTask(ctx));
                }
                else {
                    scheduleStabilizeTask(ctx);
                }
            });
        }, checkIntervalMillis, MILLISECONDS);
    }

    private void cancelStabilizeTask() {
        if (stabilizeTaskFuture != null) {
            stabilizeTaskFuture.cancel(false);
            stabilizeTaskFuture = null;
        }
    }
}
