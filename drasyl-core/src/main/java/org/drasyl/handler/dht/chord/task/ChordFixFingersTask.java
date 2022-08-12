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
import org.drasyl.handler.dht.chord.ChordUtil;
import org.drasyl.handler.dht.chord.DefaultChordService;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.RandomUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Handler class providing {@code n.fix_fingers()} functionality that periodically access a random
 * entry in finger table ensuring it is up-to-date.
 */
public class ChordFixFingersTask extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFixFingersTask.class);
    private final ChordFingerTable fingerTable;
    private final long checkIntervalMillis;
    private final RmiClientHandler client;
    private final DefaultChordService defaultChordService;
    private ScheduledFuture<?> fixFingersTask;

    public ChordFixFingersTask(final ChordFingerTable fingerTable,
                               final long checkIntervalMillis,
                               RmiClientHandler client,
                               final DefaultChordService defaultChordService) {
        this.fingerTable = requireNonNull(fingerTable);
        this.checkIntervalMillis = requirePositive(checkIntervalMillis);
        this.client = requireNonNull(client);
        this.defaultChordService = requireNonNull(defaultChordService);
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
            final int i = RandomUtil.randomInt(2, 32);
            final long id = ChordUtil.ithFingerStart(chordId(fingerTable.getLocalAddress()), i);
            LOG.debug("Refresh {}th finger: Find successor for id `{}` and check if it is still the same peer.", i, ChordUtil.chordIdHex(id));
            defaultChordService.composableFindSuccessor(id).then(future -> {
                final DrasylAddress ithFinger = future.getNow();
                LOG.debug("Successor for id `{}` is `{}`.", ChordUtil.chordIdHex(id), ithFinger);
                return fingerTable.updateIthFinger(i, ithFinger, client);
            }).finish(ctx.executor()).addListener((FutureListener<Void>) f -> scheduleFixFingersTask(ctx));
        }, checkIntervalMillis, MILLISECONDS);
    }

    private void cancelFixFingersTask() {
        if (fixFingersTask != null) {
            fixFingersTask.cancel(false);
            fixFingersTask = null;
        }
    }
}
