/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
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
import static org.drasyl.util.FutureComposer.composeSucceededFuture;

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
                                                                final RmiClientHandler client,
                                                                final String serviceName) {
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

        return recursive(findId, myAddress, findIdRelativeId, mySuccessorRelativeId, fingerTable.getLocalAddress(), fingerTable, client, serviceName);
    }

    @SuppressWarnings("java:S107")
    private static FutureComposer<DrasylAddress> recursive(final long findId,
                                                           final DrasylAddress currentNode,
                                                           final long findIdRelativeId,
                                                           final long currentNodeSuccessorsRelativeId,
                                                           final DrasylAddress mostRecentlyAlive,
                                                           final ChordFingerTable fingerTable,
                                                           final RmiClientHandler client,
                                                           final String serviceName) {
        if (findIdRelativeId > 0 && findIdRelativeId <= currentNodeSuccessorsRelativeId) {
            return composeSucceededFuture(currentNode);
        }

        // if current node is local node, find my closest
        if (Objects.equals(currentNode, fingerTable.getLocalAddress())) {
            return findMyClosest(findId, currentNode, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive, fingerTable, client, serviceName);
        }
        // else current node is remote node, sent request to it for its closest
        else {
            return findPeersClosest(findId, currentNode, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive, fingerTable, client, serviceName);
        }
    }

    @SuppressWarnings("java:S107")
    private static FutureComposer<DrasylAddress> findMyClosest(final long findId,
                                                               final DrasylAddress currentNode,
                                                               final long findIdRelativeId,
                                                               final long currentNodeSuccessorsRelativeId,
                                                               final DrasylAddress mostRecentlyAlive,
                                                               final ChordFingerTable fingerTable,
                                                               final RmiClientHandler client,
                                                               final String serviceName) {
        return closestPrecedingFinger(findId, fingerTable, client, serviceName)
                .then(future -> {
                    final DrasylAddress finger = future.getNow();
                    if (currentNode.equals(finger)) {
                        return composeSucceededFuture(finger);
                    }
                    else {
                        return recursive(findId, finger, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive, fingerTable, client, serviceName);
                    }
                });
    }

    @SuppressWarnings("java:S107")
    private static FutureComposer<DrasylAddress> findPeersClosest(final long findId,
                                                                  final DrasylAddress currentNode,
                                                                  final long findIdRelativeId,
                                                                  final long currentNodeSuccessorsRelativeId,
                                                                  final DrasylAddress mostRecentlyAlive,
                                                                  final ChordFingerTable fingerTable,
                                                                  final RmiClientHandler client,
                                                                  final String serviceName) {
        final FutureComposer<DrasylAddress> lookupComposer;
        if (currentNode != null) {
            lookupComposer = composeFuture(client.lookup(serviceName, ChordService.class, currentNode).closest(findId));
        }
        else {
            lookupComposer = composeSucceededFuture((DrasylAddress) null);
        }
        return lookupComposer.then(future -> {
            final DrasylAddress closest = future.getNow();
            // if fail to get response, set currentNode to most recently
            if (closest == null) {
                final ChordService service = client.lookup(serviceName, ChordService.class, mostRecentlyAlive);
                return composeFuture(service.yourSuccessor())
                        .then(future1 -> {
                            final DrasylAddress mostRecentlysSuccessor = future1.getNow();
                            if (mostRecentlysSuccessor == null) {
                                return composeSucceededFuture(fingerTable.getLocalAddress());
                            }
                            return recursive(findId, mostRecentlyAlive, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive, fingerTable, client, serviceName);
                        });
            }

            // if currentNode's closest is itself, return currentNode
            else if (closest.equals(currentNode)) {
                return composeSucceededFuture(closest);
            }

            // else currentNode's closest is other node "closest"
            else {
                // set currentNode as most recently alive
                // ask "closest" for its successor
                return requestClosestsSuccessor(findId, currentNode, fingerTable, closest, client, serviceName);
            }
        });
    }

    private static FutureComposer<DrasylAddress> requestClosestsSuccessor(final long findId,
                                                                          final DrasylAddress currentNode,
                                                                          final ChordFingerTable fingerTable,
                                                                          final DrasylAddress closest,
                                                                          final RmiClientHandler client,
                                                                          final String serviceName) {
        final ChordService service = client.lookup(serviceName, ChordService.class, closest);
        return composeFuture(service.yourSuccessor())
                .then(future -> {
                    final DrasylAddress closestsSuccessor = future.getNow();
                    // if we can get its response, then "closest" must be our next currentNode
                    if (closestsSuccessor != null) {
                        if (currentNode.equals(closest)) {
                            return composeSucceededFuture(closest);
                        }

                        // compute relative ids for while loop judgement
                        final long closestSuccessorsRelativeId = relativeChordId(closestsSuccessor, closest);
                        final long findIdsRelativeId = relativeChordId(findId, chordId(closest));

                        return recursive(findId, closest, findIdsRelativeId, closestSuccessorsRelativeId, currentNode, fingerTable, client, serviceName);
                    }
                    // else currentNode sticks, ask currentNode's successor
                    else {
                        final ChordService service2 = client.lookup(serviceName, ChordService.class, currentNode);
                        return composeFuture(service2.yourSuccessor());
                    }
                });
    }
}
