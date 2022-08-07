package org.drasyl.handler.dht.chord.helper;

import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.handler.dht.chord.ChordService;
import org.drasyl.handler.dht.chord.ChordUtil;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.relativeChordId;
import static org.drasyl.handler.dht.chord.helper.ChordClosestPrecedingFingerHelper.closestPrecedingFinger;
import static org.drasyl.util.FutureComposer.composeFuture;

/**
 * Helper class providing {@code n.find_successor(id)} functionality.
 * <p>
 * This class is based on <a href="https://github.com/ChuanXia/Chord">Chord implementation of Chuan
 * Xia</a>.
 */
public final class ChordFindPredecessorHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFindPredecessorHelper.class);

    private ChordFindPredecessorHelper() {
        // util class
    }

    public static FutureComposer<DrasylAddress> findPredecessor(final long findId,
                                                                final ChordFingerTable fingerTable,
                                                                final RmiClientHandler client) {
        LOG.debug("Find predecessor of `{}`", ChordUtil.chordIdHex(findId));
        final DrasylAddress myAddress = fingerTable.getLocalAddress();
        final DrasylAddress mySuccessor = fingerTable.getSuccessor();
        final long findIdRelativeId = relativeChordId(findId, myAddress);
        long mySuccessorRelativeId;
        if (mySuccessor == null) {
            mySuccessorRelativeId = 0;
        }
        else {
            mySuccessorRelativeId = relativeChordId(mySuccessor, myAddress);
        }

        return recursive(findId, myAddress, findIdRelativeId, mySuccessorRelativeId, fingerTable.getLocalAddress(), fingerTable, client);
    }

    private static FutureComposer<DrasylAddress> recursive(final long findId,
                                                           final DrasylAddress currentNode,
                                                           final long findIdRelativeId,
                                                           final long currentNodeSuccessorsRelativeId,
                                                           final DrasylAddress mostRecentlyAlive,
                                                           final ChordFingerTable fingerTable,
                                                           final RmiClientHandler client) {
        if (findIdRelativeId > 0 && findIdRelativeId <= currentNodeSuccessorsRelativeId) {
            return composeFuture(currentNode);
        }

        // if current node is local node, find my closest
        if (Objects.equals(currentNode, fingerTable.getLocalAddress())) {
            return findMyClosest(findId, currentNode, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive, fingerTable, client);
        }
        // else current node is remote node, sent request to it for its closest
        else {
            return findPeersClosest(findId, currentNode, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive, fingerTable, client);
        }
    }

    private static FutureComposer<DrasylAddress> findMyClosest(final long findId,
                                                               final DrasylAddress currentNode,
                                                               final long findIdRelativeId,
                                                               final long currentNodeSuccessorsRelativeId,
                                                               final DrasylAddress mostRecentlyAlive,
                                                               final ChordFingerTable fingerTable,
                                                               final RmiClientHandler client) {
        return closestPrecedingFinger(findId, fingerTable, client)
                .chain(future -> {
                    final DrasylAddress finger = future.getNow();
                    if (currentNode.equals(finger)) {
                        return composeFuture(finger);
                    }
                    else {
                        return recursive(findId, finger, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive, fingerTable, client);
                    }
                });
    }

    private static FutureComposer<DrasylAddress> findPeersClosest(final long findId,
                                                                  final DrasylAddress currentNode,
                                                                  final long findIdRelativeId,
                                                                  final long currentNodeSuccessorsRelativeId,
                                                                  final DrasylAddress mostRecentlyAlive,
                                                                  final ChordFingerTable fingerTable,
                                                                  final RmiClientHandler client) {
        return composeFuture().chain(client.lookup("ChordService", ChordService.class, currentNode).closest(findId))
                .chain(future -> {
                    final DrasylAddress closest = future.getNow();
                    // if fail to get response, set currentNode to most recently
                    if (closest == null) {
                        final ChordService service = client.lookup("ChordService", ChordService.class, mostRecentlyAlive);
                        return composeFuture().chain(service.yourSuccessor())
                                .chain(future1 -> {
                                    final DrasylAddress mostRecentlysSuccessor = future1.getNow();
                                    if (mostRecentlysSuccessor == null) {
                                        return composeFuture(fingerTable.getLocalAddress());
                                    }
                                    return recursive(findId, mostRecentlyAlive, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive, fingerTable, client);
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
                        return requestClosestsSuccessor(findId, currentNode, fingerTable, closest, client);
                    }
                });
    }

    private static FutureComposer<DrasylAddress> requestClosestsSuccessor(final long findId,
                                                                          final DrasylAddress currentNode,
                                                                          final ChordFingerTable fingerTable,
                                                                          final DrasylAddress closest,
                                                                          final RmiClientHandler client) {
        final ChordService service = client.lookup("ChordService", ChordService.class, closest);
        return composeFuture().chain(service.yourSuccessor())
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

                        return recursive(findId, closest, findIdsRelativeId, closestSuccessorsRelativeId, currentNode, fingerTable, client);
                    }
                    // else currentNode sticks, ask currentNode's successor
                    else {
                        final ChordService service2 = client.lookup("ChordService", ChordService.class, currentNode);
                        return composeFuture().chain(service2.yourSuccessor());
                    }
                });
    }
}
