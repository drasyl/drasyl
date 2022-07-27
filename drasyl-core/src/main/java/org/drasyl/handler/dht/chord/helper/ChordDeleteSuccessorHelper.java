package org.drasyl.handler.dht.chord.helper;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static org.drasyl.handler.dht.chord.helper.ChordFillSuccessorHelper.fillSuccessor;
import static org.drasyl.handler.dht.chord.requester.ChordYourPredecessorRequester.yourPredecessorRequest;
import static org.drasyl.util.FutureUtil.chainFuture;

public final class ChordDeleteSuccessorHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordDeleteSuccessorHelper.class);

    private ChordDeleteSuccessorHelper() {
        // util class
    }

    public static Future<Void> deleteSuccessor(final ChannelHandlerContext ctx,
                                               final ChordFingerTable fingerTable) {
        final IdentityPublicKey successor = fingerTable.getSuccessor();

        //nothing to delete, just return
        if (successor == null) {
            return null;
        }

        // find the last existence of successor in the finger table
        int i;
        for (i = Integer.SIZE; i > 0; i--) {
            final IdentityPublicKey ithfinger = fingerTable.get(i);
            if (ithfinger != null && ithfinger.equals(successor)) {
                break;
            }
        }

        // delete it, from the last existence to the first one
        recursive(ctx, fingerTable, i);

        // if predecessor is successor, delete it
        if (fingerTable.hasPredecessor() && Objects.equals(fingerTable.getPredecessor(), fingerTable.getSuccessor())) {
            fingerTable.removePredecessor();
        }

        // try to fill successor
        return FutureUtil.chainFuture(fillSuccessor(ctx, fingerTable), ctx.executor(), unused -> {
            final IdentityPublicKey successor2 = fingerTable.getSuccessor();

            // if successor is still null or local node,
            // and the predecessor is another node, keep asking
            // it's predecessor until find local node's new successor
            if ((successor2 == null || ctx.channel().localAddress().equals(successor2)) && fingerTable.hasPredecessor() && !ctx.channel().localAddress().equals(fingerTable.getPredecessor())) {
                final IdentityPublicKey p = fingerTable.getPredecessor();

                final Future<Void> voidFuture = FutureUtil.mapFuture(recursive2(ctx, p, successor2), ctx.executor(), publicKey -> null);
                return chainFuture(voidFuture, ctx.executor(), publicKey -> {
                    // update successor
                    return fingerTable.updateIthFinger(ctx, 1, p);
                });
            }
            else {
                return ctx.executor().newSucceededFuture(null);
            }
        });
    }

    private static Future<Void> recursive(final ChannelHandlerContext ctx,
                                          final ChordFingerTable fingerTable,
                                          final int j) {
        return chainFuture(fingerTable.updateIthFinger(ctx, j, null), ctx.executor(), unused -> {
            if (j > 1) {
                return recursive(ctx, fingerTable, j - 1);
            }
            else {
                return fingerTable.updateIthFinger(ctx, j, null);
            }
        });
    }

    private static Future<IdentityPublicKey> recursive2(final ChannelHandlerContext ctx,
                                                        final IdentityPublicKey p,
                                                        final IdentityPublicKey successor) {
        return chainFuture(yourPredecessorRequest(ctx, p), ctx.executor(), p_pre -> {
            if (p_pre == null) {
                return ctx.executor().newSucceededFuture(p);
            }

            // if p's predecessor is node is just deleted,
            // or itself (nothing found in p), or local address,
            // p is current node's new successor, break
            if (p_pre.equals(p) || p_pre.equals(ctx.channel().localAddress()) || p_pre.equals(successor)) {
                return ctx.executor().newSucceededFuture(p);
            }

            // else, keep asking
            else {
                return recursive2(ctx, p_pre, successor);
            }
        });
    }
}
