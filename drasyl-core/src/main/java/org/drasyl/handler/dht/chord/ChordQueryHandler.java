package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.FutureListener;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.handler.dht.chord.requester.ChordFindSuccessorRequester.findSuccessorRequest;
import static org.drasyl.handler.dht.chord.requester.ChordKeepRequester.keepRequest;
import static org.drasyl.handler.dht.chord.requester.ChordYourPredecessorRequester.yourPredecessorRequest;
import static org.drasyl.handler.dht.chord.requester.ChordYourSuccessorRequester.yourSuccessorRequest;
import static org.drasyl.util.FutureComposer.composeFuture;

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
            doLookup(ctx, ((ChordLookup) msg).getContact(), ((ChordLookup) msg).getId(), promise);
        }
        else {
            ctx.write(msg, promise);
        }
    }

    private void doLookup(final ChannelHandlerContext ctx,
                          final IdentityPublicKey contact,
                          final long id,
                          final ChannelPromise promise) {
        if (this.promise == null) {
            this.promise = promise;
            checkContactAlive(ctx, contact, id, promise);
        }
        else {
            promise.tryFailure(new Exception("Another Chord lookup is in progress. Please try again later."));
        }
    }

    private void checkContactAlive(final ChannelHandlerContext ctx,
                                   final IdentityPublicKey contact,
                                   final long id,
                                   final ChannelPromise promise) {
        LOG.debug("checkContactAlive?");
        keepRequest(ctx, contact).finish(ctx.executor()).addListener((FutureListener<Void>) future -> {
            if (future.isSuccess()) {
                LOG.debug("checkContactAlive = true");
                // now check
                checkContactStable(ctx, contact, id, promise);
            }
            else {
                promise.setFailure(new Exception("Contact node " + contact + " does not answer."));
            }
        });
    }

    private void checkContactStable(final ChannelHandlerContext ctx,
                                    final IdentityPublicKey contact,
                                    final long id,
                                    final ChannelPromise promise) {
        LOG.debug("checkContactStable?");
        yourPredecessorRequest(ctx, contact)
                .chain(future -> yourSuccessorRequest(ctx, contact)
                        .chain(future1 -> {
                            final IdentityPublicKey pred_addr = future.getNow();
                            final IdentityPublicKey succ_addr = future1.getNow();
                            if (pred_addr == null || succ_addr == null) {
                                this.promise = null;
                                promise.setFailure(new Exception("Contact node " + contact + " is disconnected. Please try an other contact node."));
                                return composeFuture(false);
                            }

                            final boolean pred = pred_addr.equals(contact);
                            final boolean succ = succ_addr.equals(contact);

                            return composeFuture(pred == succ);
                        }))
                .finish(ctx.executor())
                .addListener((FutureListener<Boolean>) future -> {
                    if (future.isSuccess()) {
                        LOG.debug("checkContactStable = true");
                        // do actual lookup
                        doActualLookup(ctx, contact, id, promise);
                    }
                    else {
                        this.promise = null;
                        promise.tryFailure(new Exception("Contact node " + contact + " is not stable. Please try again later."));
                    }
                });
    }

    private void doActualLookup(final ChannelHandlerContext ctx,
                                final IdentityPublicKey contact,
                                final long id,
                                final ChannelPromise promise) {
        findSuccessorRequest(ctx, id, contact).finish(ctx.executor()).addListener((FutureListener<IdentityPublicKey>) future -> {
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
