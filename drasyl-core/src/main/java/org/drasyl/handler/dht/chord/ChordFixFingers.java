package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.identity.IdentityPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Fixfingers thread that periodically access a random entry in finger table and fix it.
 */
public class ChordFixFingers extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFixFingers.class);
    private final Random random;
    private final ChordFingerTable fingerTable;
    private int counter;
    private ScheduledFuture<?> fixFingersTask;

    public ChordFixFingers(final ChordFingerTable fingerTable) {
        random = new Random();
        this.fingerTable = requireNonNull(fingerTable);
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
            final int i = /*random.nextInt(31)*/(counter++ % 31) + 2; // no randomness for debugging
            //final int i = 32;
            final long id = ChordUtil.ithStart(ChordUtil.hashSocketAddress((IdentityPublicKey) ctx.channel().localAddress()), i);
            LOG.debug("Refresh {}th finger: Find successor for id `{}` and check if it is still the same peer.", i, ChordUtil.longTo8DigitHex(id));
            ChordUtil.find_successor(ctx, id, fingerTable).addListener((FutureListener<IdentityPublicKey>) future -> {
                final IdentityPublicKey ithfinger = future.get();
                LOG.debug("Successor for id `{}` is `{}`.", ChordUtil.longTo8DigitHex(id), ithfinger);
                fingerTable.updateIthFinger(ctx, i, ithfinger).addListener((FutureListener<Void>) future1 -> scheduleFixFingersTask(ctx));
            });
        }, 500, MILLISECONDS);
    }

    private void cancelFixFingersTask() {
        if (fixFingersTask != null) {
            fixFingersTask.cancel(false);
            fixFingersTask = null;
        }
    }
}
