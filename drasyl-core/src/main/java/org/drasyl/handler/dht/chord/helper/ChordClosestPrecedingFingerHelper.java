package org.drasyl.handler.dht.chord.helper;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdToHex;
import static org.drasyl.handler.dht.chord.ChordUtil.computeRelativeChordId;
import static org.drasyl.handler.dht.chord.requester.ChordKeepRequester.keepRequest;
import static org.drasyl.util.FutureComposer.composeFuture;
import static org.drasyl.util.UnexecutableFutureComposer.composeUnexecutableFuture;

public final class ChordClosestPrecedingFingerHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordClosestPrecedingFingerHelper.class);

    private ChordClosestPrecedingFingerHelper() {
        // util class
    }

    public static FutureComposer<IdentityPublicKey> closestPrecedingFinger(final ChannelHandlerContext ctx,
                                                                           final long findid,
                                                                           final ChordFingerTable fingerTable) {
        LOG.debug("Find closest finger preceding `{}`.", chordIdToHex(findid));
        final long localId = chordId(ctx.channel().localAddress());
        final long findid_relative = computeRelativeChordId(findid, localId);

        // check from last item in finger table
        return recursive(ctx, findid, localId, findid_relative, Integer.SIZE, fingerTable);
    }

    private static FutureComposer<IdentityPublicKey> recursive(final ChannelHandlerContext ctx,
                                                               final long findid,
                                                               final long localId,
                                                               final long findid_relative,
                                                               final int i,
                                                               final ChordFingerTable fingerTable) {
        if (i == 0) {
            LOG.debug("We're closest to `{}`.", chordIdToHex(findid));
            return composeUnexecutableFuture((IdentityPublicKey) ctx.channel().localAddress()).compose(ctx.executor());
        }
        else {
            return composeFuture(ctx.executor(), fingerTable.get(i)).chain(ith_finger -> {
                if (ith_finger != null) {
                    final long ith_finger_id = chordId(ith_finger);
                    final long ith_finger_relative_id = computeRelativeChordId(ith_finger_id, localId);

                    // if its relative id is the closest, check if its alive
                    if (ith_finger_relative_id > 0 && ith_finger_relative_id < findid_relative) {
                        LOG.debug("{}th finger {} is closest preceding finger of {}.", i, chordIdToHex(ith_finger_id), chordIdToHex(findid));
                        LOG.debug("Check if it is still alive.");

                        return composeUnexecutableFuture()
                                .thenUnexecutable(keepRequest(ctx, ith_finger))
                                .chain2(future -> {
                                    //it is alive, return it
                                    if (future.isSuccess()) {
                                        LOG.debug("Peer is still alive.");
                                        return composeUnexecutableFuture(ith_finger);
                                    }
                                    // else, remove its existence from finger table
                                    else {
                                        LOG.warn("Peer is not alive. Remove it from finger table.");
                                        fingerTable.removePeer(ith_finger);
                                        return composeUnexecutableFuture(null);
                                    }
                                }).compose(ctx.executor());
                    }
                }
                return recursive(ctx, findid, localId, findid_relative, i - 1, fingerTable);
            });
        }
    }
}
