package org.drasyl.handler.dht.chord.helper;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static org.drasyl.handler.dht.chord.ChordUtil.chordIdToHex;
import static org.drasyl.handler.dht.chord.helper.ChordFindPredecessorHelper.findPredecessor;
import static org.drasyl.handler.dht.chord.requester.ChordYourSuccessorRequester.yourSuccessorRequest;
import static org.drasyl.util.FutureComposer.composeFuture;

public final class ChordFindSuccessorHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFindSuccessorHelper.class);

    private ChordFindSuccessorHelper() {
        // util class
    }

    public static FutureComposer<IdentityPublicKey> findSuccessor(final ChannelHandlerContext ctx,
                                                                  final long id,
                                                                  final ChordFingerTable fingerTable) {
        LOG.debug("Find successor of `{}`.", chordIdToHex(id));

        // initialize return value as this node's successor (might be null)
        final IdentityPublicKey ret = fingerTable.getSuccessor();

        LOG.debug("Find successor of {} by asking id's predecessor for its successor.", chordIdToHex(id));

        return findPredecessor(ctx, id, fingerTable)
                .chain(future -> {
                    final IdentityPublicKey pre = future.getNow();
                    // if other node found, ask it for its successor
                    if (!Objects.equals(pre, ctx.channel().localAddress())) {
                        return yourSuccessorRequest(ctx, pre);
                    }
                    else {
                        return composeFuture(ret);
                    }
                })
                .chain(future -> {
                    final IdentityPublicKey ret1 = future.getNow();
                    if (ret1 == null) {
                        return composeFuture((IdentityPublicKey) ctx.channel().localAddress());
                    }
                    return composeFuture(ret1);
                });
    }
}
