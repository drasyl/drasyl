package org.drasyl.handler.dht.chord.helper;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static org.drasyl.handler.dht.chord.helper.ChordFillSuccessorHelper.fillSuccessor;
import static org.drasyl.handler.dht.chord.requester.ChordYourPredecessorRequester.yourPredecessorRequest;
import static org.drasyl.util.FutureComposer.composeFuture;

public final class ChordDeleteSuccessorHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordDeleteSuccessorHelper.class);

    private ChordDeleteSuccessorHelper() {
        // util class
    }

    public static FutureComposer<Void> deleteSuccessor(final ChannelHandlerContext ctx,
                                                       final ChordFingerTable fingerTable) {
        final IdentityPublicKey successor = fingerTable.getSuccessor();

        //nothing to delete, just return
        if (successor == null) {
            return composeFuture();
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
        return fillSuccessor(ctx, fingerTable)
                .chain(unused -> {
                    final IdentityPublicKey successor2 = fingerTable.getSuccessor();

                    // if successor is still null or local node,
                    // and the predecessor is another node, keep asking
                    // it's predecessor until find local node's new successor
                    if ((successor2 == null || ctx.channel().localAddress().equals(successor2)) && fingerTable.hasPredecessor() && !ctx.channel().localAddress().equals(fingerTable.getPredecessor())) {
                        final IdentityPublicKey p = fingerTable.getPredecessor();

                        return recursive2(ctx, p, successor2)
                                .map(publicKey1 -> null)
                                .chain(publicKey -> {
                                    // update successor
                                    return fingerTable.updateIthFinger(ctx, 1, p);
                                });
                    }
                    else {
                        return composeFuture();
                    }
                });
    }

    private static FutureComposer<Void> recursive(final ChannelHandlerContext ctx,
                                                  final ChordFingerTable fingerTable,
                                                  final int j) {
        return fingerTable.updateIthFinger(ctx, j, null)
                .chain(unused -> {
                    if (j > 1) {
                        return recursive(ctx, fingerTable, j - 1);
                    }
                    else {
                        return fingerTable.updateIthFinger(ctx, j, null);
                    }
                });
    }

    private static FutureComposer<IdentityPublicKey> recursive2(final ChannelHandlerContext ctx,
                                                                final IdentityPublicKey p,
                                                                final IdentityPublicKey successor) {
        return yourPredecessorRequest(ctx, p)
                .chain(p_pre -> {
                    if (p_pre == null) { // FIXME: oder cause???
                        return FutureComposer.composeFuture(p);
                    }

                    // if p's predecessor is node is just deleted,
                    // or itself (nothing found in p), or local address,
                    // p is current node's new successor, break
                    if (p_pre.equals(p) || p_pre.equals(ctx.channel().localAddress()) || p_pre.equals(successor)) {
                        return FutureComposer.composeFuture(p);
                    }

                    // else, keep asking
                    else {
                        return recursive2(ctx, p_pre, successor);
                    }
                });
    }
}
