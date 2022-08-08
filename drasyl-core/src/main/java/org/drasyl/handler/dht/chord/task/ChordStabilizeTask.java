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
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.relativeChordId;
import static org.drasyl.handler.dht.chord.MyChordService.SERVICE_NAME;
import static org.drasyl.handler.dht.chord.helper.ChordDeleteSuccessorHelper.deleteSuccessor;
import static org.drasyl.handler.dht.chord.helper.ChordFillSuccessorHelper.fillSuccessor;
import static org.drasyl.util.FutureComposer.composeFuture;
import static org.drasyl.util.FutureComposer.composeSucceededFuture;
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
            final RmiClientHandler client = ctx.pipeline().get(RmiClientHandler.class);
            if (successor == null || successor.equals(fingerTable.getLocalAddress())) {
                // Try to fill successor with candidates in finger table or even predecessor
                voidFuture = fillSuccessor(fingerTable, client);//fill
            }
            else {
                voidFuture = composeSucceededFuture();
            }

            voidFuture.finish(ctx.executor()).addListener((FutureListener<Void>) future -> {
                if (successor != null && !successor.equals(fingerTable.getLocalAddress())) {
                    LOG.debug("Check if successor has still us a predecessor.");

                    // try to get my successor's predecessor
                    final ChordService service = client.lookup(SERVICE_NAME, ChordService.class, successor);
                    composeFuture(service.yourPredecessor()).then(future2 -> {
                        // if bad connection with successor! delete successor
                        DrasylAddress x = future2.getNow();
                        if (x == null) {
                            LOG.debug("Bad connection with successor. Delete successor from finger table.");
                            return deleteSuccessor(fingerTable, client, SERVICE_NAME);
                        }

                        // else if successor's predecessor is not itself
                        else if (!x.equals(successor)) {
                            if (x.equals(fingerTable.getLocalAddress())) {
                                LOG.debug("Successor has still us as predecessor. All fine.");
                            }
                            else {
                                LOG.debug("Successor's predecessor is {}.", x);
                            }
                            final long localId = chordId(fingerTable.getLocalAddress());
                            final long successorRelativeId = relativeChordId(successor, localId);
                            final long xRelativeId = relativeChordId(x, localId);
                            if (xRelativeId > 0 && xRelativeId < successorRelativeId) {
                                LOG.debug("Successor's predecessor {} is closer then me. Use successor's predecessor as our new successor.", x);
                                return fingerTable.updateIthFinger(1, x, client);
                            }
                            else {
                                return composeSucceededFuture();
                            }
                        }

                        // successor's predecessor is successor itself, then notify successor
                        else {
                            LOG.debug("Successor's predecessor is successor itself, notify successor to set us as his predecessor.");
                            if (!successor.equals(fingerTable.getLocalAddress())) {
                                return composeFuture(service.iAmPre());
                            }
                            return composeSucceededFuture();
                        }
                    }).finish(ctx.executor()).addListener((FutureListener<Void>) future12 -> scheduleStabilizeTask(ctx));
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
