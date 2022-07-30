package org.drasyl.handler.dht.chord.helper;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.handler.dht.chord.ChordUtil;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static org.drasyl.handler.dht.chord.helper.ChordFindPredecessorHelper.findPredecessor;
import static org.drasyl.handler.dht.chord.requester.ChordYourSuccessorRequester.requestSuccessor;
import static org.drasyl.util.FutureComposer.composeFuture;

/**
 * Helper class providing {@code n.find_predecessor(id)} functionality.
 * <p>
 * This class is based on <a href="https://github.com/ChuanXia/Chord">Chord implementation of Chuan
 * Xia</a>.
 */
public final class ChordFindSuccessorHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFindSuccessorHelper.class);

    private ChordFindSuccessorHelper() {
        // util class
    }

    public static FutureComposer<DrasylAddress> findSuccessor(final ChannelHandlerContext ctx,
                                                              final long id,
                                                              final ChordFingerTable fingerTable) {
        LOG.debug("Find successor of `{}`.", ChordUtil.chordIdHex(id));

        // initialize return value as this node's successor (might be null)
        final DrasylAddress ret = fingerTable.getSuccessor();

        LOG.debug("Find successor of {} by asking id's predecessor for its successor.", ChordUtil.chordIdHex(id));

        return findPredecessor(ctx, id, fingerTable)
                .chain(future -> {
                    final DrasylAddress pre = future.getNow();
                    // if other node found, ask it for its successor
                    if (!Objects.equals(pre, ctx.channel().localAddress())) {
                        return requestSuccessor(ctx, pre);
                    }
                    else {
                        return composeFuture(ret);
                    }
                })
                .chain(future -> {
                    final DrasylAddress ret1 = future.getNow();
                    if (ret1 == null) {
                        return composeFuture((DrasylAddress) ctx.channel().localAddress());
                    }
                    return composeFuture(ret1);
                });
    }
}
