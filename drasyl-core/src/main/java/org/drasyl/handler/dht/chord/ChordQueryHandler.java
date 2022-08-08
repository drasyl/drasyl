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

import static org.drasyl.util.FutureComposer.composeFuture;

/**
 * This handler performs a lookup in the Chord table once an outbound {@link ChordLookup} message is
 * written to the channel. Once the lookup is done, the write {@link Promise} is succeeded and an
 * inbound {@link ChordResponse} message is passed to the channel. On error, the {@link Promise} is
 * failed.
 * <p>
 * This class is based on <a href="https://github.com/ChuanXia/Chord">Chord implementation of Chuan
 * Xia</a>.
 */
@SuppressWarnings({ "java:S1192" })
public class ChordQueryHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ChordQueryHandler.class);
    private ChannelPromise promise;

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        if (promise != null) {
            promise.cancel(false);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) throws Exception {
        if (msg instanceof ChordLookup) {
            doLookup(ctx, ((ChordLookup) msg).getContact(), ((ChordLookup) msg).getId(), promise, ctx.pipeline().get(RmiClientHandler.class));
        }
        else {
            ctx.write(msg, promise);
        }
    }

    private void doLookup(final ChannelHandlerContext ctx,
                          final DrasylAddress contact,
                          final long id,
                          final ChannelPromise promise, RmiClientHandler client) {
        if (this.promise == null) {
            this.promise = promise;
            checkContactAlive(ctx, contact, id, promise, client);
        }
        else {
            promise.tryFailure(new ChordException("Another Chord lookup is in progress. Please try again later."));
        }
    }

    private void checkContactAlive(final ChannelHandlerContext ctx,
                                   final DrasylAddress contact,
                                   final long id,
                                   final ChannelPromise promise,
                                   final RmiClientHandler client) {
        LOG.debug("checkContactAlive?");
        final ChordService service = client.lookup("ChordService", ChordService.class, contact);
        service.keep().addListener((FutureListener<Void>) future -> {
            if (future.isSuccess()) {
                LOG.debug("checkContactAlive = true");
                // now check
                checkContactStable(ctx, contact, id, promise, client);
            }
            else {
                promise.setFailure(new ChordException("Contact node " + contact + " does not answer."));
            }
        });
    }

    private void checkContactStable(final ChannelHandlerContext ctx,
                                    final DrasylAddress contact,
                                    final long id,
                                    final ChannelPromise promise,
                                    final RmiClientHandler client) {
        LOG.debug("checkContactStable?");

        final ChordService service = client.lookup("ChordService", ChordService.class, contact);
        composeFuture().chain(service.yourPredecessor())
                .chain(future -> composeFuture().chain(service.yourSuccessor())
                        .chain(future1 -> {
                            final DrasylAddress predecessor = future.getNow();
                            final DrasylAddress successor = future1.getNow();
                            if (predecessor == null || successor == null) {
                                this.promise = null;
                                promise.setFailure(new ChordException("Contact node " + contact + " is disconnected. Please try an other contact node."));
                                return composeFuture(false);
                            }

                            return composeFuture(predecessor.equals(contact) == successor.equals(contact));
                        }))
                .finish(ctx.executor())
                .addListener((FutureListener<Boolean>) future -> {
                    if (future.isSuccess()) {
                        LOG.debug("checkContactStable = true");
                        // do actual lookup
                        doActualLookup(ctx, contact, id, promise, client);
                    }
                    else {
                        this.promise = null;
                        promise.tryFailure(new ChordException("Contact node " + contact + " is not stable. Please try again later."));
                    }
                });
    }

    private void doActualLookup(final ChannelHandlerContext ctx,
                                final DrasylAddress contact,
                                final long id,
                                final ChannelPromise promise,
                                final RmiClientHandler client) {
        final ChordService service = client.lookup("ChordService", ChordService.class, contact);
        service.findSuccessor(id).addListener((FutureListener<DrasylAddress>) future -> {
            if (future.isSuccess()) {
                ctx.fireChannelRead(ChordResponse.of(id, future.getNow()));
                this.promise = null;
                promise.trySuccess();
            }
            else {
                this.promise = null;
                promise.tryFailure(future.cause());
            }
        });
    }
}
