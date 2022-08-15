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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdHex;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdPosition;
import static org.drasyl.handler.dht.chord.LocalChordNode.SERVICE_NAME;

/**
 * This handler performs a lookup in the Chord table once an outbound {@link ChordLookup} message is
 * written to the channel. Once the lookup is done, the write {@link Promise} is succeeded and an
 * inbound {@link ChordResponse} message is passed to the channel. On error, the {@link Promise} is
 * failed.
 */
@SuppressWarnings({ "java:S1192" })
public class ChordLookupHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ChordLookupHandler.class);
    private final RmiClientHandler client;

    public ChordLookupHandler(final RmiClientHandler client) {
        this.client = requireNonNull(client);
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof ChordLookup) {
            final long id = ((ChordLookup) msg).getId();
            final DrasylAddress contact = ((ChordLookup) msg).getContact();
            final boolean doStableCheck = ((ChordLookup) msg).doStableCheck();

            final RemoteChordNode service = client.lookup(SERVICE_NAME, RemoteChordNode.class, contact);

            if (doStableCheck) {
                LOG.info("Check first if contact node `{}` is stable.", contact);
                doStableCheck(ctx, id, promise, service);
            }
            else {
                doLookup(ctx, id, promise, service);
            }
        }
        else {
            ctx.write(msg, promise);
        }
    }

    @SuppressWarnings("java:S5411")
    private static void doStableCheck(final ChannelHandlerContext ctx,
                                      final long id,
                                      final ChannelPromise promise,
                                      final RemoteChordNode service) {
        service.isStable().addListener((FutureListener<Boolean>) future -> {
            if (future.isSuccess()) {
                if (future.getNow()) {
                    LOG.info("Contact node is stable.");
                    if (!promise.isDone()) {
                        doLookup(ctx, id, promise, service);
                    }
                    else {
                        LOG.debug("Abort as Promise has been cancelled.");
                    }
                }
                else {
                    LOG.warn("Contact node is not stable.");
                    promise.tryFailure(new ChordException("Contact node is not stable. Please try again later."));
                }
            }
            else {
                LOG.warn("Unable to reach contact node:", future.cause());
                promise.tryFailure(new ChordException("Unable to reach contact node. Please try an other contact node.", future.cause()));
            }
        });
    }

    private static void doLookup(final ChannelHandlerContext ctx,
                                 final long id,
                                 final ChannelPromise promise,
                                 final RemoteChordNode service) {
        LOG.info("Do lookup for id `{}` ({}).", chordIdHex(id), chordIdPosition(id));

        service.findSuccessor(id).addListener((FutureListener<DrasylAddress>) future -> {
            if (future.isSuccess()) {
                LOG.debug("Lookup done. Id `{}` ({}) has resolved to address `{}` ({}).", chordIdHex(id), chordIdPosition(id), future.getNow(), chordIdPosition(future.getNow()));
                ctx.fireChannelRead(ChordResponse.of(id, future.getNow()));
                promise.trySuccess();
            }
            else {
                LOG.warn("Lookup failed:", future.cause());
                promise.tryFailure(future.cause());
            }
        });
    }
}
