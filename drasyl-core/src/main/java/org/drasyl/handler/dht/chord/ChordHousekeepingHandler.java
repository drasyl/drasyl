/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.FutureListener;
import org.drasyl.util.RandomUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * This handler performs some housekeeping tasks keeping the Chord circle stable.
 */
public class ChordHousekeepingHandler extends ChannelInboundHandlerAdapter {
    public static final long DEFAULT_CHECK_INTERVAL = 500;
    private static final Logger LOG = LoggerFactory.getLogger(ChordHousekeepingHandler.class);
    private final long checkIntervalMillis;
    private final LocalChordNode localNode;
    private Future<?> askPredecessorTaskFuture;
    private Future<?> fixFingersTask;
    private Future<?> stabilizeTaskFuture;

    public ChordHousekeepingHandler(final long checkIntervalMillis,
                                    final LocalChordNode localNode) {
        this.checkIntervalMillis = requirePositive(checkIntervalMillis);
        this.localNode = requireNonNull(localNode);
    }

    public ChordHousekeepingHandler(final LocalChordNode localNode) {
        this(DEFAULT_CHECK_INTERVAL, localNode);
    }

    /*
     * Handler Events
     */

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            scheduleAskPredecessorTask(ctx);
            scheduleFixFingersTask(ctx);
            scheduleStabilizeTask(ctx);
        }
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        cancelAskPredecessorTask();
        cancelFixFingersTask();
        cancelStabilizeTask();
    }

    /*
     * Channel Events
     */

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        scheduleAskPredecessorTask(ctx);
        scheduleFixFingersTask(ctx);
        scheduleStabilizeTask(ctx);

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        cancelAskPredecessorTask();
        cancelFixFingersTask();
        cancelStabilizeTask();

        ctx.fireChannelInactive();
    }

    /*
     * Tasks
     */

    /**
     * Periodically check if our predecessor is still alive and removes it is dead.
     */
    private void scheduleAskPredecessorTask(final ChannelHandlerContext ctx) {
        askPredecessorTaskFuture = ctx.executor().schedule(() -> {
            LOG.trace("Check if our predecessor (if present) is still alive.");
            localNode.checkIfPredecessorIsAlive().addListener((FutureListener<Void>) future -> scheduleAskPredecessorTask(ctx));
        }, checkIntervalMillis, MILLISECONDS);
    }

    private void cancelAskPredecessorTask() {
        if (askPredecessorTaskFuture != null) {
            askPredecessorTaskFuture.cancel(false);
            askPredecessorTaskFuture = null;
        }
    }

    /**
     * Handler class providing {@code n.fix_fingers()} functionality that periodically access a
     * random entry in finger table ensuring it is up-to-date.
     */
    private void scheduleFixFingersTask(final ChannelHandlerContext ctx) {
        fixFingersTask = ctx.executor().schedule(() -> {
            // pick a random finger to test
            final int i = RandomUtil.randomInt(2, 32);
            localNode.fixFinger(i).addListener((FutureListener<Void>) future -> scheduleFixFingersTask(ctx));
        }, checkIntervalMillis, MILLISECONDS);
    }

    private void cancelFixFingersTask() {
        if (fixFingersTask != null) {
            fixFingersTask.cancel(false);
            fixFingersTask = null;
        }
    }

    /**
     * Handler class providing {@code n.stabilize()} functionality that periodically asks successor
     * for its predecessor and determine if current node should update or delete its successor.
     */
    private void scheduleStabilizeTask(final ChannelHandlerContext ctx) {
        stabilizeTaskFuture = ctx.executor().schedule(() -> {
            LOG.debug("Ask successor for its predecessor and determine if we should update or delete our successor.");
            localNode.stabilize().addListener((FutureListener<Void>) future -> scheduleStabilizeTask(ctx));
        }, checkIntervalMillis, MILLISECONDS);
    }

    private void cancelStabilizeTask() {
        if (stabilizeTaskFuture != null) {
            stabilizeTaskFuture.cancel(false);
            stabilizeTaskFuture = null;
        }
    }
}
