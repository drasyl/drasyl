package org.drasyl.handler.dht.chord.task;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.handler.dht.chord.ChordUtil;
import org.drasyl.identity.IdentityPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdToHex;
import static org.drasyl.handler.dht.chord.helper.ChordFindSuccessorHelper.findSuccessor;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Fixfingers thread that periodically access a random entry in finger table and fix it.
 */
public class ChordFixFingersTask extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFixFingersTask.class);
    private final ChordFingerTable fingerTable;
    private final long checkIntervalMillis;
    private int counter;
    private ScheduledFuture<?> fixFingersTask;

    public ChordFixFingersTask(final ChordFingerTable fingerTable, final long checkIntervalMillis) {
        this.fingerTable = requireNonNull(fingerTable);
        this.checkIntervalMillis = requirePositive(checkIntervalMillis);
    }

    public ChordFixFingersTask(final ChordFingerTable fingerTable) {
        this(fingerTable, 500);
    }

    /*
     * Handler Events
     */

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            scheduleFixFingersTask(ctx);
        }
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        cancelFixFingersTask();
    }

    /*
     * Channel Events
     */

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        scheduleFixFingersTask(ctx);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        cancelFixFingersTask();
        ctx.fireChannelInactive();
    }

    /*
     * Fix Fingers Task
     */

    private void scheduleFixFingersTask(final ChannelHandlerContext ctx) {
        fixFingersTask = ctx.executor().schedule(() -> {
            // no randomness for debugging
            counter = ++counter % 31;
            final int i = counter + 2;
            final long id = ChordUtil.ithFingerStart(chordId((IdentityPublicKey) ctx.channel().localAddress()), i);
            LOG.debug("Refresh {}th finger: Find successor for id `{}` and check if it is still the same peer.", i, chordIdToHex(id));
            findSuccessor(ctx, id, fingerTable)
                    .chain(ithfinger -> {
                        LOG.debug("Successor for id `{}` is `{}`.", chordIdToHex(id), ithfinger);
                        return fingerTable.updateIthFinger(ctx, i, ithfinger);
                    })
                    .finish(ctx.executor())
                    .addListener((FutureListener<Void>) future1 -> scheduleFixFingersTask(ctx));
        }, checkIntervalMillis, MILLISECONDS);
    }

    private void cancelFixFingersTask() {
        if (fixFingersTask != null) {
            fixFingersTask.cancel(false);
            fixFingersTask = null;
        }
    }
}
