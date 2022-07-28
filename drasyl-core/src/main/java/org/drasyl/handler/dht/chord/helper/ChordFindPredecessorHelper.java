package org.drasyl.handler.dht.chord.helper;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.handler.dht.chord.ChordUtil;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static org.drasyl.handler.dht.chord.ChordUtil.chordIdToHex;
import static org.drasyl.handler.dht.chord.helper.ChordClosestPrecedingFingerHelper.closestPrecedingFinger;
import static org.drasyl.handler.dht.chord.requester.ChordClosestRequester.closestRequest;
import static org.drasyl.handler.dht.chord.requester.ChordYourSuccessorRequester.yourSuccessorRequest;
import static org.drasyl.util.FutureComposer.composeFuture;

public final class ChordFindPredecessorHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFindPredecessorHelper.class);

    private ChordFindPredecessorHelper() {
        // util class
    }

    public static FutureComposer<IdentityPublicKey> findPredecessor(final ChannelHandlerContext ctx,
                                                                    final long findid,
                                                                    final ChordFingerTable fingerTable) {
        LOG.debug("Find predecessor of `{}`", chordIdToHex(findid));
        final IdentityPublicKey n = (IdentityPublicKey) ctx.channel().localAddress();
        final IdentityPublicKey n_successor = fingerTable.getSuccessor();
        long n_successor_relative_id = 0;
        if (n_successor != null) {
            n_successor_relative_id = ChordUtil.computeRelativeChordId(ChordUtil.chordId(n_successor), ChordUtil.chordId(n));
        }
        final long findid_relative_id = ChordUtil.computeRelativeChordId(findid, ChordUtil.chordId(n));

        return recursive(ctx, findid, n, findid_relative_id, n_successor_relative_id, (IdentityPublicKey) ctx.channel().localAddress(), fingerTable);
    }

    private static FutureComposer<IdentityPublicKey> recursive(final ChannelHandlerContext ctx,
                                                               final long findid,
                                                               final IdentityPublicKey pre_n,
                                                               final long findid_relative_id,
                                                               final long n_successor_relative_id,
                                                               final IdentityPublicKey most_recently_alive,
                                                               final ChordFingerTable fingerTable) {
        if (findid_relative_id > 0 && findid_relative_id <= n_successor_relative_id) {
            return composeFuture(pre_n);
        }

        // if current node is local node, find my closest
        if (Objects.equals(pre_n, ctx.channel().localAddress())) {
            return closestPrecedingFinger(ctx, findid, fingerTable)
                    .chain(future -> {
                        final IdentityPublicKey n = future.getNow();
                        if (pre_n.equals(n)) {
                            return composeFuture(n);
                        }
                        else {
                            return recursive(ctx, findid, n, findid_relative_id, n_successor_relative_id, most_recently_alive, fingerTable);
                        }
                    });
        }
        // else current node is remote node, sent request to it for its closest
        else {
            return closestRequest(ctx, pre_n, findid)
                    .chain(future -> {
                        final IdentityPublicKey result = future.getNow();
                        // if fail to get response, set n to most recently
                        if (result == null) {
                            return yourSuccessorRequest(ctx, most_recently_alive)
                                    .chain(future1 -> {
                                        final IdentityPublicKey n_successor = future1.getNow();
                                        if (n_successor == null) {
                                            return composeFuture((IdentityPublicKey) ctx.channel().localAddress());
                                        }
                                        return recursive(ctx, findid, most_recently_alive, findid_relative_id, n_successor_relative_id, most_recently_alive, fingerTable);
                                    });
                        }

                        // if n's closest is itself, return n
                        else if (result.equals(pre_n)) {
                            return composeFuture(result);
                        }

                        // else n's closest is other node "result"
                        else {
                            // set n as most recently alive
                            // ask "result" for its successor
                            return yourSuccessorRequest(ctx, result)
                                    .chain(future1 -> {
                                        final IdentityPublicKey n_successor = future1.getNow();
                                        // if we can get its response, then "result" must be our next n
                                        if (n_successor != null) {
                                            if (pre_n.equals(result)) {
                                                return composeFuture(result);
                                            }

                                            // compute relative ids for while loop judgement
                                            final long n_successor_relative_id2 = ChordUtil.computeRelativeChordId(ChordUtil.chordId(n_successor), ChordUtil.chordId(result));
                                            final long findid_relative_id2 = ChordUtil.computeRelativeChordId(findid, ChordUtil.chordId(result));

                                            return recursive(ctx, findid, result, findid_relative_id2, n_successor_relative_id2, pre_n, fingerTable);
                                        }
                                        // else n sticks, ask n's successor
                                        else {
                                            return yourSuccessorRequest(ctx, pre_n);
                                        }
                                    });
                        }
                    });
        }
    }
}
