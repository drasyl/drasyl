package org.drasyl.handler.dht.chord.helper;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.handler.dht.chord.ChordUtil;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.UnexecutableFutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.handler.dht.chord.ChordUtil.chordIdToHex;
import static org.drasyl.handler.dht.chord.helper.ChordClosestPrecedingFingerHelper.closestPrecedingFinger;
import static org.drasyl.handler.dht.chord.requester.ChordClosestRequester.closestRequest;
import static org.drasyl.handler.dht.chord.requester.ChordYourSuccessorRequester.yourSuccessorRequest;
import static org.drasyl.util.UnexecutableFutureComposer.composeUnexecutableFuture;

public final class ChordFindPredecessorHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFindPredecessorHelper.class);

    private ChordFindPredecessorHelper() {
        // util class
    }

    public static UnexecutableFutureComposer<IdentityPublicKey> findPredecessor(final ChannelHandlerContext ctx,
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

    private static UnexecutableFutureComposer<IdentityPublicKey> recursive(final ChannelHandlerContext ctx,
                                                                           final long findid,
                                                                           final IdentityPublicKey pre_n,
                                                                           final long findid_relative_id,
                                                                           final long n_successor_relative_id,
                                                                           final IdentityPublicKey most_recently_alive,
                                                                           final ChordFingerTable fingerTable) {
        if (findid_relative_id > 0 && findid_relative_id <= n_successor_relative_id) {
            return composeUnexecutableFuture(pre_n);
        }

        // if current node is local node, find my closest
        if (pre_n.equals(ctx.channel().localAddress())) {
            return composeUnexecutableFuture()
                    .then(closestPrecedingFinger(ctx, findid, fingerTable))
                    .chain(n -> {
                        if (pre_n.equals(n)) {
                            return composeUnexecutableFuture(n);
                        }
                        else {
                            return recursive(ctx, findid, n, findid_relative_id, n_successor_relative_id, most_recently_alive, fingerTable);
                        }
                    });
        }
        // else current node is remote node, sent request to it for its closest
        else {
            return composeUnexecutableFuture()
                    .then(closestRequest(ctx, pre_n, findid))
                    .chain(result -> {
                        // if fail to get response, set n to most recently
                        if (result == null) { // FIXME: oder cause?
                            return composeUnexecutableFuture()
                                    .then(yourSuccessorRequest(ctx, most_recently_alive))
                                    .chain(n_successor -> {
                                        if (n_successor == null) { // FIXME: oder cause?
                                            return composeUnexecutableFuture((IdentityPublicKey) ctx.channel().localAddress());
                                        }
                                        return recursive(ctx, findid, most_recently_alive, findid_relative_id, n_successor_relative_id, most_recently_alive, fingerTable);
                                    });
                        }

                        // if n's closest is itself, return n
                        else if (result.equals(pre_n)) {
                            return composeUnexecutableFuture(result);
                        }

                        // else n's closest is other node "result"
                        else {
                            // set n as most recently alive
                            // ask "result" for its successor
                            return composeUnexecutableFuture()
                                    .then(yourSuccessorRequest(ctx, result))
                                    .chain(n_successor -> {
                                        // if we can get its response, then "result" must be our next n
                                        if (n_successor != null) { // FIXME: oder cause?
                                            if (pre_n.equals(result)) {
                                                return composeUnexecutableFuture(result);
                                            }

                                            // compute relative ids for while loop judgement
                                            final long n_successor_relative_id2 = ChordUtil.computeRelativeChordId(ChordUtil.chordId(n_successor), ChordUtil.chordId(result));
                                            final long findid_relative_id2 = ChordUtil.computeRelativeChordId(findid, ChordUtil.chordId(result));

                                            return recursive(ctx, findid, result, findid_relative_id2, n_successor_relative_id2, pre_n, fingerTable);
                                        }
                                        // else n sticks, ask n's successor
                                        else {
                                            return composeUnexecutableFuture().then(yourSuccessorRequest(ctx, pre_n));
                                        }
                                    });
                        }
                    });
        }
    }
}
