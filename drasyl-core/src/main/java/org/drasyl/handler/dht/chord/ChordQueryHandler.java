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

import static org.drasyl.handler.dht.chord.requester.ChordFindSuccessorRequester.findSuccessor;
import static org.drasyl.handler.dht.chord.requester.ChordYourSuccessorRequester.requestSuccessor;
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
            doLookup(ctx, ((ChordLookup) msg).getContact(), ((ChordLookup) msg).getId(), promise);
        }
        else {
            ctx.write(msg, promise);
        }
    }

    private void doLookup(final ChannelHandlerContext ctx,
                          final DrasylAddress contact,
                          final long id,
                          final ChannelPromise promise) {
        if (this.promise == null) {
            this.promise = promise;
            checkContactAlive(ctx, contact, id, promise);
        }
        else {
            promise.tryFailure(new ChordException("Another Chord lookup is in progress. Please try again later."));
        }
    }

    private void checkContactAlive(final ChannelHandlerContext ctx,
                                   final DrasylAddress contact,
                                   final long id,
                                   final ChannelPromise promise) {
        LOG.debug("checkContactAlive?");
        final ChordService service = ctx.pipeline().get(RmiClientHandler.class).lookup("ChordService", ChordService.class, contact);
        service.keep().addListener((FutureListener<Void>) future -> {
            if (future.isSuccess()) {
                LOG.debug("checkContactAlive = true");
                // now check
                checkContactStable(ctx, contact, id, promise);
            }
            else {
                promise.setFailure(new ChordException("Contact node " + contact + " does not answer."));
            }
        });
    }

    private void checkContactStable(final ChannelHandlerContext ctx,
                                    final DrasylAddress contact,
                                    final long id,
                                    final ChannelPromise promise) {
        LOG.debug("checkContactStable?");

        final ChordService service = ctx.pipeline().get(RmiClientHandler.class).lookup("ChordService", ChordService.class, contact);
        composeFuture().chain(service.yourPredecessor())
                .chain(future -> requestSuccessor(ctx, contact)
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
                        doActualLookup(ctx, contact, id, promise);
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
                                final ChannelPromise promise) {
        findSuccessor(ctx, id, contact).finish(ctx.executor()).addListener((FutureListener<DrasylAddress>) future -> {
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
