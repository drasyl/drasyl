package org.drasyl.handler.dht.chord.helper;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.UnexecutableFutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.util.UnexecutableFutureComposer.composeUnexecutableFuture;

public final class ChordFillSuccessorHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFillSuccessorHelper.class);

    private ChordFillSuccessorHelper() {
        // util class
    }

    public static UnexecutableFutureComposer<Void> fillSuccessor(final ChannelHandlerContext ctx,
                                                                 final ChordFingerTable fingerTable) {
        LOG.debug("Try to fill successor with candidates in finger table or even predecessor.");
        final IdentityPublicKey successor = fingerTable.getSuccessor();
        final UnexecutableFutureComposer<Void> future;
        if (successor == null || successor.equals(ctx.channel().localAddress())) {
            future = recursive(ctx, 2, fingerTable);
        }
        else {
            future = composeUnexecutableFuture();
        }

        return composeUnexecutableFuture()
                .then(future)
                .chain(unused -> {
                    final IdentityPublicKey successor2 = fingerTable.getSuccessor();
                    if ((successor2 == null || successor2.equals(ctx.channel().localAddress())) && fingerTable.hasPredecessor() && !ctx.channel().localAddress().equals(fingerTable.getPredecessor())) {
                        return fingerTable.updateIthFinger(ctx, 1, fingerTable.getPredecessor());
                    }
                    else {
                        return composeUnexecutableFuture();
                    }
                });
    }

    private static UnexecutableFutureComposer<Void> recursive(final ChannelHandlerContext ctx,
                                                              final int i,
                                                              final ChordFingerTable fingerTable) {
        if (i <= Integer.SIZE) {
            final IdentityPublicKey ithfinger = fingerTable.get(i);
            if (ithfinger != null && !ithfinger.equals(ctx.channel().localAddress())) {
                return recursive2(ctx, i - 1, ithfinger, fingerTable);
            }
            else {
                return recursive(ctx, i + 1, fingerTable);
            }
        }
        else {
            return composeUnexecutableFuture();
        }
    }

    private static UnexecutableFutureComposer<Void> recursive2(final ChannelHandlerContext ctx,
                                                               final int j,
                                                               final IdentityPublicKey ithfinger,
                                                               final ChordFingerTable fingerTable) {
        if (j >= 1) {
            return composeUnexecutableFuture()
                    .then(fingerTable.updateIthFinger(ctx, j, ithfinger))
                    .chain(unused -> recursive2(ctx, j - 1, ithfinger, fingerTable));
        }
        else {
            return composeUnexecutableFuture();
        }
    }
}
