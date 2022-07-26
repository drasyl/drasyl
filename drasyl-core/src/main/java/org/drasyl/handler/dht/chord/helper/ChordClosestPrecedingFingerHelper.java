package org.drasyl.handler.dht.chord.helper;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdToHex;
import static org.drasyl.handler.dht.chord.ChordUtil.computeRelativeChordId;
import static org.drasyl.handler.dht.chord.requester.ChordKeepRequester.keepRequest;

public final class ChordClosestPrecedingFingerHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordClosestPrecedingFingerHelper.class);

    private ChordClosestPrecedingFingerHelper() {
        // util class
    }

    public static Future<IdentityPublicKey> closestPrecedingFinger(final ChannelHandlerContext ctx,
                                                                   final long findid,
                                                                   final ChordFingerTable fingerTable) {
        LOG.debug("Find closest finger preceding `{}`.", chordIdToHex(findid));
        final long localId = chordId(ctx.channel().localAddress());
        final long findid_relative = computeRelativeChordId(findid, localId);

        // check from last item in finger table
        return recursive(ctx, findid, localId, findid_relative, Integer.SIZE, fingerTable);
    }

    private static Future<IdentityPublicKey> recursive(final ChannelHandlerContext ctx,
                                                       final long findid,
                                                       final long localId,
                                                       final long findid_relative,
                                                       final int i,
                                                       final ChordFingerTable fingerTable) {
        if (i == 0) {
            LOG.debug("We're closest to `{}`.", chordIdToHex(findid));
            return ctx.executor().newSucceededFuture((IdentityPublicKey) ctx.channel().localAddress());
        }

        final IdentityPublicKey ith_finger = fingerTable.get(i);
        if (ith_finger != null) {
            final long ith_finger_id = chordId(ith_finger);
            final long ith_finger_relative_id = computeRelativeChordId(ith_finger_id, localId);

            // if its relative id is the closest, check if its alive
            if (ith_finger_relative_id > 0 && ith_finger_relative_id < findid_relative) {
                LOG.debug("{}th finger {} is closest preceding finger of {}.", i, chordIdToHex(ith_finger_id), chordIdToHex(findid));
                LOG.debug("Check if it is still alive.");
                final Promise<IdentityPublicKey> objectPromise = ctx.executor().newPromise();
                keepRequest(ctx, ith_finger).addListener((FutureListener<Void>) future -> {
                    //it is alive, return it
                    if (future.cause() == null) {
                        LOG.debug("Peer is still alive.");
                        objectPromise.setSuccess(ith_finger);
                    }

                    // else, remove its existence from finger table
                    else {
                        LOG.warn("Peer is not alive. Remove it from finger table.");
                        fingerTable.removePeer(ith_finger);
                        recursive(ctx, findid, localId, findid_relative, i - 1, fingerTable).addListener(new PromiseNotifier<>(objectPromise));
                    }
                });
                return objectPromise;
            }
        }
        return recursive(ctx, findid, localId, findid_relative, i - 1, fingerTable);
    }
}
