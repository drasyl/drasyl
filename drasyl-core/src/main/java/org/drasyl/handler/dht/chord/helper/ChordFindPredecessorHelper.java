package org.drasyl.handler.dht.chord.helper;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.handler.dht.chord.ChordUtil;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.relativeChordId;
import static org.drasyl.handler.dht.chord.helper.ChordClosestPrecedingFingerHelper.closestPrecedingFinger;
import static org.drasyl.handler.dht.chord.requester.ChordClosestRequester.requestClosest;
import static org.drasyl.handler.dht.chord.requester.ChordYourSuccessorRequester.requestSuccessor;
import static org.drasyl.util.FutureComposer.composeFuture;

public final class ChordFindPredecessorHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFindPredecessorHelper.class);

    private ChordFindPredecessorHelper() {
        // util class
    }

    public static FutureComposer<DrasylAddress> findPredecessor(final ChannelHandlerContext ctx,
                                                                final long findId,
                                                                final ChordFingerTable fingerTable) {
        LOG.debug("Find predecessor of `{}`", ChordUtil.chordIdHex(findId));
        final DrasylAddress myAddress = (DrasylAddress) ctx.channel().localAddress();
        final DrasylAddress mySuccessor = fingerTable.getSuccessor();
        final long findIdRelativeId = relativeChordId(findId, myAddress);
        long mySuccessorRelativeId;
        if (mySuccessor == null) {
            mySuccessorRelativeId = 0;
        }
        else {
            mySuccessorRelativeId = relativeChordId(mySuccessor, myAddress);
        }

        return recursive(ctx, findId, myAddress, findIdRelativeId, mySuccessorRelativeId, (DrasylAddress) ctx.channel().localAddress(), fingerTable);
    }

    private static FutureComposer<DrasylAddress> recursive(final ChannelHandlerContext ctx,
                                                           final long findId,
                                                           final DrasylAddress currentNode,
                                                           final long findIdRelativeId,
                                                           final long currentNodeSuccessorsRelativeId,
                                                           final DrasylAddress mostRecentlyAlive,
                                                           final ChordFingerTable fingerTable) {
        if (findIdRelativeId > 0 && findIdRelativeId <= currentNodeSuccessorsRelativeId) {
            return composeFuture(currentNode);
        }

        // if current node is local node, find my closest
        if (Objects.equals(currentNode, ctx.channel().localAddress())) {
            return findMyClosest(ctx, findId, currentNode, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive, fingerTable);
        }
        // else current node is remote node, sent request to it for its closest
        else {
            return findPeersClosest(ctx, findId, currentNode, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive, fingerTable);
        }
    }

    private static FutureComposer<DrasylAddress> findMyClosest(final ChannelHandlerContext ctx,
                                                               final long findId,
                                                               final DrasylAddress currentNode,
                                                               final long findIdRelativeId,
                                                               final long currentNodeSuccessorsRelativeId,
                                                               final DrasylAddress mostRecentlyAlive,
                                                               final ChordFingerTable fingerTable) {
        return closestPrecedingFinger(ctx, findId, fingerTable)
                .chain(future -> {
                    final DrasylAddress finger = future.getNow();
                    if (currentNode.equals(finger)) {
                        return composeFuture(finger);
                    }
                    else {
                        return recursive(ctx, findId, finger, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive, fingerTable);
                    }
                });
    }

    private static FutureComposer<DrasylAddress> findPeersClosest(final ChannelHandlerContext ctx,
                                                                  final long findId,
                                                                  final DrasylAddress currentNode,
                                                                  final long findIdRelativeId,
                                                                  final long currentNodeSuccessorsRelativeId,
                                                                  final DrasylAddress mostRecentlyAlive,
                                                                  final ChordFingerTable fingerTable) {
        return requestClosest(ctx, currentNode, findId)
                .chain(future -> {
                    final DrasylAddress closest = future.getNow();
                    // if fail to get response, set currentNode to most recently
                    if (closest == null) {
                        return requestSuccessor(ctx, mostRecentlyAlive)
                                .chain(future1 -> {
                                    final DrasylAddress mostRecentlysSuccessor = future1.getNow();
                                    if (mostRecentlysSuccessor == null) {
                                        return composeFuture((DrasylAddress) ctx.channel().localAddress());
                                    }
                                    return recursive(ctx, findId, mostRecentlyAlive, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive, fingerTable);
                                });
                    }

                    // if currentNode's closest is itself, return currentNode
                    else if (closest.equals(currentNode)) {
                        return composeFuture(closest);
                    }

                    // else currentNode's closest is other node "closest"
                    else {
                        // set currentNode as most recently alive
                        // ask "closest" for its successor
                        return requestClosestsSuccessor(ctx, findId, currentNode, fingerTable, closest);
                    }
                });
    }

    private static FutureComposer<DrasylAddress> requestClosestsSuccessor(final ChannelHandlerContext ctx,
                                                                          final long findId,
                                                                          final DrasylAddress currentNode,
                                                                          final ChordFingerTable fingerTable,
                                                                          final DrasylAddress closest) {
        return requestSuccessor(ctx, closest)
                .chain(future -> {
                    final DrasylAddress closestsSuccessor = future.getNow();
                    // if we can get its response, then "closest" must be our next currentNode
                    if (closestsSuccessor != null) {
                        if (currentNode.equals(closest)) {
                            return composeFuture(closest);
                        }

                        // compute relative ids for while loop judgement
                        final long closestSuccessorsRelativeId = relativeChordId(closestsSuccessor, closest);
                        final long findIdsRelativeId = relativeChordId(findId, chordId(closest));

                        return recursive(ctx, findId, closest, findIdsRelativeId, closestSuccessorsRelativeId, currentNode, fingerTable);
                    }
                    // else currentNode sticks, ask currentNode's successor
                    else {
                        return requestSuccessor(ctx, currentNode);
                    }
                });
    }
}
