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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.SucceededFuture;
import org.drasyl.handler.rmi.RmiCaller;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.relativeChordId;
import static org.drasyl.handler.dht.chord.helper.ChordClosestPrecedingFingerHelper.closestPrecedingFinger;

public class MyChordService implements ChordService {
    private static final Logger LOG = LoggerFactory.getLogger(MyChordService.class);
    @RmiCaller
    private DrasylAddress caller;
    //private ChannelHandlerContext ctx;
    private final ChordFingerTable fingerTable;
    private final RmiClientHandler client;
    private final EventLoopGroup group = new NioEventLoopGroup();

    public MyChordService(final ChordFingerTable fingerTable, RmiClientHandler client) {
        this.fingerTable = requireNonNull(fingerTable);
        this.client = requireNonNull(client);
    }

    @Override
    public Future<Void> keep() {
        return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, null);
    }

    @Override
    public Future<DrasylAddress> yourPredecessor() {
        if (fingerTable.hasPredecessor()) {
            return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, fingerTable.getPredecessor());
        }
        else {
            return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, null);
        }
    }

    @Override
    public Future<DrasylAddress> yourSuccessor() {
        if (fingerTable.hasSuccessor()) {
            return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, fingerTable.getSuccessor());
        }
        else {
            return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, null);
        }
    }

    @Override
    public Future<Void> iAmPre() {
        final DrasylAddress newPredecessorCandidate = caller;
        LOG.debug("Notified by `{}`.", newPredecessorCandidate);
        if (!fingerTable.hasPredecessor() || fingerTable.getLocalAddress().equals(fingerTable.getPredecessor())) {
            LOG.info("Set predecessor `{}`.", newPredecessorCandidate);
            fingerTable.setPredecessor(newPredecessorCandidate);
            // FIXME: wieso hier nicht checken, ob er als geeigneter fingers dient?
        }
        else {
            final long oldpre_id = chordId(fingerTable.getPredecessor());
            final long local_relative_id = relativeChordId(fingerTable.getLocalAddress(), oldpre_id);
            final long newpre_relative_id = relativeChordId(newPredecessorCandidate, oldpre_id);
            if (newpre_relative_id > 0 && newpre_relative_id < local_relative_id) {
                LOG.info("Set predecessor `{}`.", newPredecessorCandidate);
                fingerTable.setPredecessor(newPredecessorCandidate);
            }
        }

        return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, null);
    }

    @Override
    public Future<DrasylAddress> closest(final long id) {
        return closestPrecedingFinger(id, fingerTable, client).finish(group.next());
    }
}
