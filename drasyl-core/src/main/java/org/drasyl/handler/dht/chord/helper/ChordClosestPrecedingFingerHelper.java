package org.drasyl.handler.dht.chord.helper;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdHex;
import static org.drasyl.handler.dht.chord.ChordUtil.relativeChordId;
import static org.drasyl.handler.dht.chord.requester.ChordKeepRequester.keepRequest;
import static org.drasyl.util.FutureComposer.composeFuture;

public final class ChordClosestPrecedingFingerHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordClosestPrecedingFingerHelper.class);

    private ChordClosestPrecedingFingerHelper() {
        // util class
    }

    public static FutureComposer<DrasylAddress> closestPrecedingFinger(final ChannelHandlerContext ctx,
                                                                       final long findId,
                                                                       final ChordFingerTable fingerTable) {
        LOG.debug("Find closest finger preceding `{}`.", chordIdHex(findId));
        final long myId = chordId(ctx.channel().localAddress());
        final long findIdRelativeId = relativeChordId(findId, myId);

        // check from last item in finger table
        return checkFinger(ctx, myId, findId, findIdRelativeId, Integer.SIZE, fingerTable);
    }

    private static FutureComposer<DrasylAddress> checkFinger(final ChannelHandlerContext ctx,
                                                             final long myId,
                                                             final long findId,
                                                             final long findIdRelative,
                                                             final int i,
                                                             final ChordFingerTable fingerTable) {
        if (i == 0) {
            LOG.debug("We're closest to `{}`.", chordIdHex(findId));
            return composeFuture((DrasylAddress) ctx.channel().localAddress());
        }
        else {
            return composeFuture(fingerTable.get(i)).chain(future -> {
                final DrasylAddress ithFinger = future.getNow();
                if (ithFinger != null) {
                    // if its relative id is the closest, check if its alive
                    final long ithFingerId = chordId(ithFinger);
                    final long ithFingerRelativeId = relativeChordId(ithFingerId, myId);

                    if (ithFingerRelativeId > 0 && ithFingerRelativeId < findIdRelative) {
                        LOG.debug("{}th finger {} is closest preceding finger of {}.", i, chordIdHex(ithFingerId), chordIdHex(findId));
                        LOG.debug("Check if it is still alive.");

                        return keepRequest(ctx, ithFinger)
                                .chain(future2 -> {
                                    //it is alive, return it
                                    if (future2.isSuccess()) {
                                        LOG.debug("Peer is still alive.");
                                        return composeFuture(ithFinger);
                                    }
                                    // else, remove its existence from finger table
                                    else {
                                        LOG.warn("Peer `{}` is not alive. Remove it from finger table.", ithFinger);
                                        fingerTable.removePeer(ithFinger);
                                    }
                                    return checkFinger(ctx, myId, findId, findIdRelative, i - 1, fingerTable);
                                });
                    }
                }
                return checkFinger(ctx, myId, findId, findIdRelative, i - 1, fingerTable);
            });
        }
    }
}
