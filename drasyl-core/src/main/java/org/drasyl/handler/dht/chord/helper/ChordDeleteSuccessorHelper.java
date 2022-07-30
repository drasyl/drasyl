package org.drasyl.handler.dht.chord.helper;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;

import java.util.Objects;

import static org.drasyl.handler.dht.chord.helper.ChordFillSuccessorHelper.fillSuccessor;
import static org.drasyl.handler.dht.chord.requester.ChordYourPredecessorRequester.requestPredecessor;
import static org.drasyl.util.FutureComposer.composeFuture;

public final class ChordDeleteSuccessorHelper {
    private ChordDeleteSuccessorHelper() {
        // util class
    }

    public static FutureComposer<Void> deleteSuccessor(final ChannelHandlerContext ctx,
                                                       final ChordFingerTable fingerTable) {
        final DrasylAddress successor = fingerTable.getSuccessor();

        // nothing to delete, just return
        if (successor == null) {
            return composeFuture();
        }

        // find the last existence of successor in the finger table
        int i;
        for (i = Integer.SIZE; i > 0; i--) {
            final DrasylAddress ithFinger = fingerTable.get(i);
            if (ithFinger != null && ithFinger.equals(successor)) {
                break;
            }
        }

        // delete it, from the last existence to the first one
        deleteFromIthToFirstFinger(ctx, fingerTable, i);

        // if predecessor is successor, delete it
        if (fingerTable.hasPredecessor() && Objects.equals(fingerTable.getPredecessor(), fingerTable.getSuccessor())) {
            fingerTable.removePredecessor();
        }

        // try to fill successor
        return fillSuccessor(ctx, fingerTable)
                .chain(() -> {
                    final DrasylAddress successor2 = fingerTable.getSuccessor();

                    // if successor is still null or local node,
                    // and the predecessor is another node, keep asking
                    // it's predecessor until find local node's new successor
                    if ((successor2 == null || ctx.channel().localAddress().equals(successor2)) && fingerTable.hasPredecessor() && !ctx.channel().localAddress().equals(fingerTable.getPredecessor())) {
                        final DrasylAddress predecessor = fingerTable.getPredecessor();

                        return findNewSuccessor(ctx, predecessor, successor2)
                                // update successor
                                .chain(() -> fingerTable.updateIthFinger(ctx, 1, predecessor));
                    }
                    else {
                        return composeFuture();
                    }
                });
    }

    private static FutureComposer<Void> deleteFromIthToFirstFinger(final ChannelHandlerContext ctx,
                                                                   final ChordFingerTable fingerTable,
                                                                   final int j) {
        return fingerTable.updateIthFinger(ctx, j, null)
                .chain(() -> {
                    if (j > 1) {
                        return deleteFromIthToFirstFinger(ctx, fingerTable, j - 1);
                    }
                    else {
                        return fingerTable.updateIthFinger(ctx, j, null);
                    }
                });
    }

    private static FutureComposer<DrasylAddress> findNewSuccessor(final ChannelHandlerContext ctx,
                                                                  final DrasylAddress peer,
                                                                  final DrasylAddress successor) {
        return requestPredecessor(ctx, peer)
                .chain(future -> {
                    DrasylAddress predecessor = future.getNow();
                    if (predecessor == null) {
                        return composeFuture(peer);
                    }

                    // if p's predecessor is node is just deleted,
                    // or itself (nothing found in predecessor), or local address,
                    // p is current node's new successor, break
                    if (predecessor.equals(peer) || predecessor.equals(ctx.channel().localAddress()) || predecessor.equals(successor)) {
                        return composeFuture(peer);
                    }
                    // else, keep asking
                    else {
                        return findNewSuccessor(ctx, predecessor, successor);
                    }
                });
    }
}
