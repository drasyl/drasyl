package org.drasyl.handler.dht.chord.helper;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.handler.dht.chord.ChordUtil.chordIdToHex;
import static org.drasyl.handler.dht.chord.requester.ChordYourSuccessorRequester.yourSuccessorRequest;
import static org.drasyl.util.FutureUtil.chainFuture;
import static org.drasyl.util.FutureUtil.mapFuture;

public final class ChordFindSuccessorHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFindSuccessorHelper.class);

    private ChordFindSuccessorHelper() {
        // util class
    }

    public static Future<IdentityPublicKey> findSuccessor(final ChannelHandlerContext ctx,
                                                          final long id,
                                                          final ChordFingerTable fingerTable) {
        LOG.debug("Find successor of `{}`.", chordIdToHex(id));

        // initialize return value as this node's successor (might be null)
        final IdentityPublicKey ret = fingerTable.getSuccessor();

        LOG.debug("Find successor of {} by asking id's predecessor for its successor.", chordIdToHex(id));

        // find predecessor
        final Future<IdentityPublicKey> promise = chainFuture(ChordFindPredecessorHelper.findPredecessor(ctx, id, fingerTable), ctx.executor(), pre -> {
            // if other node found, ask it for its successor
            if (!pre.equals(ctx.channel().localAddress())) {
                return yourSuccessorRequest(ctx, pre);
            }
            else {
                return ctx.executor().newSucceededFuture(ret);
            }
        });

        // if ret is still null, set it as local node, return
        return mapFuture(promise, ctx.executor(), ret1 -> {
            if (ret1 == null) {
                return (IdentityPublicKey) ctx.channel().localAddress();
            }
            return ret1;
        });
    }
}
