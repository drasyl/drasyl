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
