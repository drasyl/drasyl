package org.drasyl.handler.dht.chord.helper;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.UnexecutableFutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.handler.dht.chord.ChordUtil.chordIdToHex;
import static org.drasyl.handler.dht.chord.helper.ChordFindPredecessorHelper.findPredecessor;
import static org.drasyl.handler.dht.chord.requester.ChordYourSuccessorRequester.yourSuccessorRequest;
import static org.drasyl.util.UnexecutableFutureComposer.composeUnexecutableFuture;

public final class ChordFindSuccessorHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFindSuccessorHelper.class);

    private ChordFindSuccessorHelper() {
        // util class
    }

    public static UnexecutableFutureComposer<IdentityPublicKey> findSuccessor(final ChannelHandlerContext ctx,
                                                                              final long id,
                                                                              final ChordFingerTable fingerTable) {
        LOG.debug("Find successor of `{}`.", chordIdToHex(id));

        // initialize return value as this node's successor (might be null)
        final IdentityPublicKey ret = fingerTable.getSuccessor();

        LOG.debug("Find successor of {} by asking id's predecessor for its successor.", chordIdToHex(id));

        return composeUnexecutableFuture()
                .then(findPredecessor(ctx, id, fingerTable))
                .chain(pre -> {
                    // if other node found, ask it for its successor
                    if (!pre.equals(ctx.channel().localAddress())) {
                        return composeUnexecutableFuture().then(yourSuccessorRequest(ctx, pre));
                    }
                    else {
                        return composeUnexecutableFuture(ret);
                    }
                })
                .map(ret1 -> {
                    if (ret1 == null) { // FIXME: oder cause?
                        return (IdentityPublicKey) ctx.channel().localAddress();
                    }
                    return ret1;
                });
    }
}
