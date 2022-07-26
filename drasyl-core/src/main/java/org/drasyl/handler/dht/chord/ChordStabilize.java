package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.FutureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ChordStabilize extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ChordStabilize.class);
    private final ChordFingerTable fingerTable;

    public ChordStabilize(final ChordFingerTable fingerTable) {
        this.fingerTable = requireNonNull(fingerTable);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        scheduleAskSuccessorForPredecessor(ctx);
        ctx.fireChannelActive();
    }

    private void scheduleAskSuccessorForPredecessor(final ChannelHandlerContext ctx) {
        ctx.executor().schedule(() -> {
            LOG.debug("Ask successor for its predecessor and determine if we should update or delete our successor.");
            final IdentityPublicKey successor = fingerTable.getSuccessor();
            final Future<Void> voidFuture;
            if (successor == null || successor.equals(ctx.channel().localAddress())) {
                // Try to fill successor with candidates in finger table or even predecessor
                voidFuture = ChordUtil.fillSuccessor(ctx, fingerTable);//fill
            }
            else {
                voidFuture = ctx.executor().newSucceededFuture(null);
            }

            voidFuture.addListener((FutureListener<Void>) future -> {
                if (successor != null && !successor.equals(ctx.channel().localAddress())) {
                    LOG.debug("Check if successor has still us a predecessor.");

                    // try to get my successor's predecessor
                    FutureUtil.chainFuture(ChordUtil.requestYourPredecessor(ctx, successor), ctx.executor(), x -> {
                        // if bad connection with successor! delete successor
                        if (x == null) {
                            LOG.debug("Bad connection with successor. Delete successor from finger table.");
                            return fingerTable.deleteSuccessor(ctx);
                        }

                        // else if successor's predecessor is not itself
                        else if (!x.equals(successor)) {
                            if (x.equals(ctx.channel().localAddress())) {
                                LOG.debug("Successor has still us as predecessor. All fine.");
                            }
                            else {
                                LOG.debug("Successor's predecessor is {}.", x);
                            }
                            final long local_id = ChordUtil.hashSocketAddress((IdentityPublicKey) ctx.channel().localAddress());
                            final long successor_relative_id = ChordUtil.computeRelativeId(ChordUtil.hashSocketAddress(successor), local_id);
                            final long x_relative_id = ChordUtil.computeRelativeId(ChordUtil.hashSocketAddress(x), local_id);
                            if (x_relative_id > 0 && x_relative_id < successor_relative_id) {
                                LOG.debug("Successor's predecessor {} is closer then me. Use successor's predecessor as our new successor.", x);
                                return fingerTable.updateIthFinger(ctx, 1, x);
                            }
                            else {
                                return ctx.executor().newSucceededFuture(null);
                            }
                        }

                        // successor's predecessor is successor itself, then notify successor
                        else {
                            LOG.debug("Successor's predecessor is successor itself, notify successor to set us as his predecessor.");
                            return fingerTable.notify(ctx, successor);
                        }
                    }).addListener((FutureListener<Void>) future12 -> scheduleAskSuccessorForPredecessor(ctx));
                }
                else {
                    scheduleAskSuccessorForPredecessor(ctx);
                }
            });
        }, 500, MILLISECONDS);
    }
}
