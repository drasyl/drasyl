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
package org.drasyl.handler.dht.chord;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.SucceededFuture;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.handler.rmi.annotation.RmiCaller;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdHex;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdPosition;
import static org.drasyl.handler.dht.chord.ChordUtil.ithFingerStart;
import static org.drasyl.handler.dht.chord.ChordUtil.relativeChordId;
import static org.drasyl.util.FutureComposer.composeFailedFuture;
import static org.drasyl.util.FutureComposer.composeFuture;
import static org.drasyl.util.FutureComposer.composeSucceededFuture;

/**
 * Our local Chord node.
 * <p>
 * This class is based on <a href="https://github.com/ChuanXia/Chord">Chord implementation of Chuan
 * Xia</a>.
 */
@SuppressWarnings("unchecked")
public class LocalChordNode implements RemoteChordNode {
    public static final String BIND_NAME = RemoteChordNode.class.getSimpleName();
    private static final Logger LOG = LoggerFactory.getLogger(LocalChordNode.class);
    private final ChordFingerTable fingerTable;
    private final RmiClientHandler client;
    private final EventLoopGroup group = new DefaultEventLoopGroup(1);
    @SuppressWarnings("unused")
    @RmiCaller
    private DrasylAddress caller;
    private final DrasylAddress localAddress;
    private DrasylAddress predecessor;

    public LocalChordNode(final DrasylAddress localAddress,
                          final ChordFingerTable fingerTable,
                          final RmiClientHandler client) {
        this.localAddress = requireNonNull(localAddress);
        this.fingerTable = requireNonNull(fingerTable);
        this.client = requireNonNull(client);
    }

    public LocalChordNode(final DrasylAddress address, RmiClientHandler client) {
        this(address, new ChordFingerTable(address), client);
    }

    @SuppressWarnings({
            "StringBufferReplaceableByString",
            "StringConcatenationInsideStringBufferAppend"
    })
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("LOCAL:        " + localAddress + " " + chordIdHex(localAddress) + " (" + chordIdPosition(localAddress) + ")");
        sb.append(System.lineSeparator());
        sb.append("PREDECESSOR:  " + predecessor + " " + (predecessor != null ? (chordIdHex(predecessor) + " (" + chordIdPosition(predecessor) + ")") : ""));
        sb.append(System.lineSeparator());
        sb.append("FINGER TABLE:");
        sb.append(System.lineSeparator());
        sb.append(fingerTable.toString());

        return sb.toString();
    }

    @Override
    public Future<Void> checkAlive() {
        LOG.debug("checkAlive {}", caller);
        // NOOP / return result immediately
        return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, null);
    }

    @Override
    public Future<DrasylAddress> getPredecessor() {
        // no blocking call necessary / return result immediately
        if (predecessor != null) {
            return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, predecessor);
        }
        else {
            return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, null);
        }
    }

    @Override
    public Future<DrasylAddress> getSuccessor() {
        // no blocking call necessary / return result immediately
        if (fingerTable.hasSuccessor()) {
            return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, fingerTable.getSuccessor());
        }
        else {
            return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, null);
        }
    }

    @Override
    public Future<Void> offerAsPredecessor() {
        // no blocking call necessary / return result immediately
        LOG.debug("Notified by `{}`.", caller);
        if (predecessor == null || localAddress.equals(predecessor)) {
            LOG.info("Set predecessor to `{}`.", caller);
            predecessor = caller;
            // TODO: check if predecessor can improve our fingers?
        }
        else {
            final long localRelativeDd = relativeChordId(localAddress, predecessor);
            final long newPreRelativeId = relativeChordId(caller, predecessor);
            if (newPreRelativeId > 0 && newPreRelativeId < localRelativeDd) {
                LOG.info("Set predecessor `{}`.", caller);
                predecessor = caller;
            }
        }

        return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, null);
    }

    private FutureComposer<DrasylAddress> composableFindSuccessor(long id) {
        LOG.debug("Find successor of `{}` ({}) by asking id's predecessor for its successor.", () -> chordIdHex(id), () -> chordIdPosition(id));

        // initialize return value as this node's successor (might be null)
        final DrasylAddress ret = fingerTable.getSuccessor();

        return findPredecessor(id).then(future -> {
            final DrasylAddress pre = future.getNow();
            // if other node found, ask it for its successor
            if (!Objects.equals(pre, localAddress)) {
                if (pre != null) {
                    LOG.debug("Predecessor of `{}` ({}) is `{}` ({}).", () -> chordIdHex(id), () -> chordIdPosition(id), () -> pre, () -> chordIdPosition(pre));
                    return composeFuture(client.lookup(BIND_NAME, RemoteChordNode.class, pre).getSuccessor());
                }
                else {
                    LOG.debug("Request predecessor of id `{}` ({}) failed. Fail back to `{}` ({}).", () -> chordIdHex(id), () -> chordIdPosition(id), () -> localAddress);
                    return composeSucceededFuture(localAddress);
                }
            }
            else {
                return composeSucceededFuture(ret);
            }
        }).then(future -> {
            final DrasylAddress ret1 = future.getNow();
            if (ret1 == null) {
                LOG.debug("We're successor of `{}` ({}): `{}` ({})", () -> chordIdHex(id), () -> chordIdPosition(id), () -> localAddress, () -> chordIdPosition(localAddress));
                return composeSucceededFuture(localAddress);
            }
            else {
                LOG.debug("Successor of `{}` ({}) is `{}` ({})", () -> chordIdHex(id), () -> chordIdPosition(id), () -> ret1, () -> chordIdPosition(ret1));
                return composeSucceededFuture(ret1);
            }
        });
    }

    @Override
    public Future<DrasylAddress> findSuccessor(long id) {
        LOG.debug("findSuccessor({})", () -> chordIdHex(id));
        return composableFindSuccessor(id).finish(group.next());
    }

    @Override
    public Future<Boolean> isStable() {
        // no blocking call necessary / return result immediately
        return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, (predecessor == null && fingerTable.getSuccessor() == null) || (predecessor != null && fingerTable.getSuccessor() != null));
    }

    /**
     * Find predecessor for {@code id}.
     * <p>
     * {@code n.find_predecessor(id)}
     */
    private FutureComposer<DrasylAddress> findPredecessor(final long id) {
        LOG.debug("Find predecessor of `{}` ({}).", () -> chordIdHex(id), () -> chordIdPosition(id));
        final DrasylAddress myAddress = localAddress;
        final DrasylAddress mySuccessor = fingerTable.getSuccessor();
        final long findIdRelativeId = relativeChordId(id, myAddress);
        long mySuccessorRelativeId;
        if (mySuccessor == null) {
            mySuccessorRelativeId = 0;
        }
        else {
            mySuccessorRelativeId = relativeChordId(mySuccessor, myAddress);
        }

        return findPredecessorRecursive(id, myAddress, findIdRelativeId, mySuccessorRelativeId, localAddress);
    }

    @SuppressWarnings("java:S107")
    private FutureComposer<DrasylAddress> findPredecessorRecursive(final long id,
                                                                   final DrasylAddress currentNode,
                                                                   final long findIdRelativeId,
                                                                   final long currentNodeSuccessorsRelativeId,
                                                                   final DrasylAddress mostRecentlyAlive) {
        if (findIdRelativeId > 0 && findIdRelativeId <= currentNodeSuccessorsRelativeId) {
            LOG.debug("Predecessor of `{}` ({}) is `{}` ({})", () -> chordIdHex(id), () -> chordIdPosition(id), () -> currentNode, () -> chordIdPosition(currentNode));
            return composeSucceededFuture(currentNode);
        }

        // if current node is local node, find my closest
        if (Objects.equals(currentNode, localAddress)) {
            return findMyClosest(id, currentNode, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive);
        }
        // else current node is remote node, sent request to it for its closest
        else {
            return findPeersClosest(id, currentNode, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive);
        }
    }

    @SuppressWarnings("java:S107")
    private FutureComposer<DrasylAddress> findMyClosest(final long id,
                                                        final DrasylAddress currentNode,
                                                        final long findIdRelativeId,
                                                        final long currentNodeSuccessorsRelativeId,
                                                        final DrasylAddress mostRecentlyAlive) {
        LOG.debug("Find predecessor of `{}` ({}) by looking up my finger table.", () -> chordIdHex(id), () -> chordIdPosition(id));
        return composableFindClosestFingerPreceding(id).then(future -> {
            final DrasylAddress finger = future.getNow();
            if (currentNode.equals(finger)) {
                return composeSucceededFuture(finger);
            }
            else {
                LOG.debug("Closest finger preceding `{}` ({}) is `{}` ({}). Now ask finger to find predecessor.", () -> chordIdHex(id), () -> chordIdPosition(id), () -> finger, () -> chordIdPosition(finger));
                return findPredecessorRecursive(id, finger, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive);
            }
        });
    }

    @SuppressWarnings({ "java:S107", "java:S3776" })
    private FutureComposer<DrasylAddress> findPeersClosest(final long id,
                                                           final DrasylAddress currentNode,
                                                           final long findIdRelativeId,
                                                           final long currentNodeSuccessorsRelativeId,
                                                           final DrasylAddress mostRecentlyAlive) {
        LOG.debug("Find predecessor of `{}` ({}) by looking up the finger table of `{}` ({}).", () -> chordIdHex(id), () -> chordIdPosition(id), () -> currentNode, () -> currentNode != null ? chordIdPosition(currentNode) : currentNode);
        final FutureComposer<DrasylAddress> lookupComposer;
        if (currentNode != null) {
            lookupComposer = composeFuture(client.lookup(BIND_NAME, RemoteChordNode.class, currentNode).findClosestFingerPreceding(id));
        }
        else {
            lookupComposer = composeSucceededFuture(null);
        }
        return lookupComposer.then(future -> {
            final DrasylAddress closest = future.getNow();
            LOG.debug("`{}` ({}) returned `{}` ({}) as closest finger preceding `{}` ({}).", () -> currentNode, () -> currentNode != null ? chordIdPosition(currentNode) : currentNode, () -> closest, () -> closest != null ? chordIdPosition(closest) : null, () -> chordIdHex(id), () -> chordIdPosition(id));

            // if fail to get response, set currentNode to most recently
            if (closest == null) {
                LOG.debug("Got no response from `{}` ({}). Try to get successor of `{}` ({}).", () -> currentNode, () -> currentNode != null ? chordIdPosition(currentNode) : currentNode, () -> mostRecentlyAlive, () -> chordIdPosition(mostRecentlyAlive));
                return composeFuture(client.lookup(BIND_NAME, RemoteChordNode.class, mostRecentlyAlive).getSuccessor()).then(future1 -> {
                    final DrasylAddress mostRecentlySuccessor = future1.getNow();
                    if (mostRecentlySuccessor == null) {
                        LOG.debug("Got no response from `{}` ({}). Return us.", () -> mostRecentlyAlive, () -> chordIdPosition(mostRecentlyAlive), () -> localAddress);
                        return composeSucceededFuture(localAddress);
                    }
                    else {
                        LOG.debug("Find predecessor of `{}` ({}) by asking `{}` ({}).", () -> chordIdHex(id), () -> chordIdPosition(id), () -> mostRecentlySuccessor, () -> chordIdPosition(mostRecentlySuccessor));
                        return findPredecessorRecursive(id, mostRecentlyAlive, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive);
                    }
                });
            }

            // if currentNode's closest is itself, return closest
            else if (closest.equals(currentNode)) {
                return composeSucceededFuture(closest);
            }

            // else currentNode's closest is other node "closest"
            else {
                // set currentNode as most recently alive
                // ask "closest" for its successor
                return requestClosestSuccessor(id, currentNode, closest);
            }
        });
    }

    private FutureComposer<DrasylAddress> requestClosestSuccessor(final long findId,
                                                                  final DrasylAddress currentNode,
                                                                  final DrasylAddress closest) {
        return composeFuture(client.lookup(BIND_NAME, RemoteChordNode.class, closest).getSuccessor()).then(future -> {
            final DrasylAddress closestSuccessor = future.getNow();
            // if we can get its response, then "closest" must be our next currentNode
            if (closestSuccessor != null) {
                if (currentNode.equals(closest)) {
                    return composeSucceededFuture(closest);
                }

                // compute relative ids for while loop judgement
                final long closestSuccessorsRelativeId = relativeChordId(closestSuccessor, closest);
                final long findIdsRelativeId = relativeChordId(findId, chordId(closest));

                return findPredecessorRecursive(findId, closest, findIdsRelativeId, closestSuccessorsRelativeId, currentNode);
            }
            // else currentNode sticks, ask currentNode's successor
            else {
                return composeFuture(client.lookup(BIND_NAME, RemoteChordNode.class, currentNode).getSuccessor());
            }
        });
    }

    private FutureComposer<DrasylAddress> composableFindClosestFingerPreceding(final long id) {
        LOG.debug("Find closest finger preceding `{}` ({}). Go down starting from {}th finger.", () -> chordIdHex(id), () -> chordIdPosition(id), () -> Integer.SIZE);
        return findClosestFingerPrecedingRecursive(id, Integer.SIZE);
    }

    @Override
    public Future<DrasylAddress> findClosestFingerPreceding(final long id) {
        return composableFindClosestFingerPreceding(id).finish(group.next());
    }

    @SuppressWarnings({ "java:S1142", "java:S3776", "unchecked" })
    private FutureComposer<DrasylAddress> findClosestFingerPrecedingRecursive(final long id,
                                                                              final int i) {
        if (i == 0) {
            LOG.debug("Reached {}th finger. We're closest to `{}` ({}).", () -> i, () -> chordIdHex(id), () -> chordIdPosition(id));
            return composeSucceededFuture(localAddress);
        }
        else {
            return composeSucceededFuture(fingerTable.get(i)).then(future -> {
                final DrasylAddress ithFinger = future.getNow();
                if (ithFinger != null) {
                    // if its relative id is the closest, check if its alive
                    final long ithFingerId = chordId(ithFinger);
                    final long ithFingerRelativeId = relativeChordId(ithFingerId, localAddress);

                    final long findIdRelative = relativeChordId(id, localAddress);
                    if (ithFingerRelativeId > 0 && ithFingerRelativeId < findIdRelative) {
                        LOG.debug("{}th finger `{}` ({}) is closest to precede `{}` ({}).", () -> i, () -> ithFinger, () -> chordIdPosition(ithFinger), () -> chordIdHex(id), () -> chordIdPosition(id));

                        if (localAddress.equals(ithFinger)) {
                            LOG.debug("That is me. Finger is for sure alive ;).");
                            return composeSucceededFuture(ithFinger);
                        }

                        LOG.debug("Check if {}th finger `{}` ({}) is still alive.", () -> i, () -> ithFinger, () -> chordIdPosition(ithFinger));
                        return composeFuture(client.lookup(BIND_NAME, RemoteChordNode.class, ithFinger).checkAlive()).then(future2 -> {
                            //it is alive, return it
                            if (future2.isSuccess()) {
                                LOG.debug("{}th finger `{}` ({}) is still alive.", () -> i, () -> ithFinger, () -> chordIdPosition(ithFinger));
                                return composeSucceededFuture(ithFinger);
                            }
                            // else, remove its existence from finger table
                            else {
                                final int nextI = i - 1;
                                LOG.debug("{}th finger `{}` ({}) is no longer alive. Remote it from finger table and go to {}th finger.", () -> i, () -> ithFinger, () -> chordIdPosition(ithFinger), () -> nextI);
                                fingerTable.removePeer(ithFinger);
                                return findClosestFingerPrecedingRecursive(id, nextI);
                            }
                        });
                    }
                    else {
                        final int nextI = i - 1;
                        LOG.debug("{}th finger `{}` ({}) is not preceding `{}` ({}). Go to {}th finger.", () -> i, () -> ithFinger, () -> chordIdPosition(ithFinger), () -> chordIdHex(id), () -> chordIdPosition(id), () -> nextI);
                        return findClosestFingerPrecedingRecursive(id, nextI);
                    }
                }
                else {
                    final int nextI = i - 1;
                    LOG.debug("{}th finger does not exist. Go to {}th finger.", () -> i, () -> nextI);
                    return findClosestFingerPrecedingRecursive(id, nextI);
                }
            });
        }
    }

    /**
     * Updates the {@code i}th finger to point at {@code value}.
     *
     * @param i     finger to update
     * @param value new value
     */
    private FutureComposer<Void> composableUpdateIthFinger(final int i,
                                                           final DrasylAddress value,
                                                           final RmiClientHandler client) {
        // if the updated one is successor, notify the new successor
        if (fingerTable.updateIthFinger(i, value)) {
            return composeFuture(client.lookup(BIND_NAME, RemoteChordNode.class, value).offerAsPredecessor());
        }
        else {
            return composeSucceededFuture();
        }
    }

    /**
     * Join circle by contacting {@code contact}.
     */
    public Future<Void> join(final DrasylAddress contact) {
        return composeFuture(client.lookup(BIND_NAME, RemoteChordNode.class, contact).findSuccessor(chordId(localAddress))).then(future -> {
            if (future.isSuccess()) {
                final DrasylAddress successor = future.getNow();
                LOG.debug("Successor for our id `{}` is `{}`.", chordIdHex(localAddress), successor);
                LOG.info("Set successor to `{}`.", successor);
                return composableUpdateIthFinger(1, successor, client);
            }
            else {
                LOG.error("Failed to join DHT ring `{}`:", contact, future.cause());
                return composeFailedFuture(new ChordException("Failed to join DHT ring.", future.cause()));
            }
        }).finish(group.next());
    }

    /**
     * Returns a successfully completed future if our predecessor is alive or we do not have a
     * predecessor. Otherwise, the future will fail.
     */
    public Future<Void> checkIfPredecessorIsAlive() {
        if (predecessor != null) {
            return client.lookup(BIND_NAME, RemoteChordNode.class, predecessor).checkAlive().addListener((FutureListener<Void>) future -> {
                if (!future.isSuccess()) {
                    LOG.debug("Our predecessor `{}` is not longer alive. Clear predecessor.", predecessor);
                    predecessor = null;
                }
            });
        }
        else {
            return group.next().newSucceededFuture(null);
        }
    }

    /**
     * verify my immediate successor, and tell the successor about me.
     */
    @SuppressWarnings({ "java:S1142", "java:S3776" })
    public Future<Void> stabilize() {
        LOG.debug("stabilize()");
        final DrasylAddress successor = fingerTable.getSuccessor();
        final FutureComposer<Void> voidFuture;
        if (successor == null || successor.equals(localAddress)) {
            // Try to fill successor with candidates in finger table or even predecessor
            voidFuture = fillSuccessor();
        }
        else {
            voidFuture = composeSucceededFuture();
        }

        return voidFuture.then(future -> {
            if (successor != null && !successor.equals(localAddress)) {
                LOG.debug("Check if successor `{}` ({}) has still us a predecessor.", () -> successor, () -> chordIdPosition(successor));

                // try to get my successor's predecessor
                return composeFuture(client.lookup(BIND_NAME, RemoteChordNode.class, successor).getPredecessor()).then(future2 -> {
                    // if bad connection with successor! delete successor
                    DrasylAddress x = future2.getNow();
                    if (x == null) {
                        LOG.debug("Bad connection with successor `{}`. Delete successor from finger table.", successor);
                        return deleteSuccessor();
                    }

                    // else if successor's predecessor is not itself
                    else if (!x.equals(successor)) {
                        if (x.equals(localAddress)) {
                            LOG.debug("Successor `{}` ({}) has still us as predecessor. All fine.", () -> successor, () -> chordIdPosition(successor));
                        }
                        else {
                            LOG.debug("Successor's predecessor is `{}` ({}).", () -> x, () -> chordIdPosition(x));
                        }
                        final long localId = chordId(localAddress);
                        final long successorRelativeId = relativeChordId(successor, localId);
                        final long xRelativeId = relativeChordId(x, localId);
                        if (xRelativeId > 0 && xRelativeId < successorRelativeId) {
                            LOG.debug("Successor's predecessor {} is closer then me. Use successor's predecessor as our new successor.", x);
                            return composableUpdateIthFinger(1, x, client);
                        }
                        else {
                            return composeSucceededFuture();
                        }
                    }

                    // successor's predecessor is successor itself, then notify successor
                    else {
                        LOG.debug("Successor's predecessor is successor itself, notify successor to set us as his predecessor.");
                        if (!successor.equals(localAddress)) {
                            return composeFuture(client.lookup(BIND_NAME, RemoteChordNode.class, successor).offerAsPredecessor());
                        }
                        return composeSucceededFuture();
                    }
                });
            }
            else {
                return composeSucceededFuture();
            }
        }).finish(group.next());
    }

    @SuppressWarnings("java:S109")
    private FutureComposer<Void> fillSuccessor() {
        LOG.debug("Try to fill successor with candidates in finger table or even predecessor.");
        final DrasylAddress successor = fingerTable.getSuccessor();
        final FutureComposer<Void> future;
        if (successor == null || successor.equals(localAddress)) {
            future = findSuccessorStartingFromIthFinger(2);
        }
        else {
            future = composeSucceededFuture();
        }

        return future.then(() -> {
            final DrasylAddress successor2 = fingerTable.getSuccessor();
            if ((successor2 == null || successor2.equals(localAddress)) && predecessor != null && !localAddress.equals(predecessor)) {
                return composableUpdateIthFinger(1, predecessor, client);
            }
            else {
                return composeSucceededFuture();
            }
        });
    }

    private FutureComposer<Void> findSuccessorStartingFromIthFinger(final int i) {
        if (i <= Integer.SIZE) {
            final DrasylAddress ithFinger = fingerTable.get(i);
            if (ithFinger != null && !ithFinger.equals(localAddress)) {
                return updateFingersFromIthToFirstFinger(i - 1, ithFinger);
            }
            else {
                return findSuccessorStartingFromIthFinger(i + 1);
            }
        }
        else {
            return composeSucceededFuture();
        }
    }

    private FutureComposer<Void> updateFingersFromIthToFirstFinger(final int j,
                                                                   final DrasylAddress ithFinger) {
        if (j >= 1) {
            return composableUpdateIthFinger(j, ithFinger, client).then(() -> updateFingersFromIthToFirstFinger(j - 1, ithFinger));
        }
        else {
            return composeSucceededFuture();
        }
    }

    private FutureComposer<Void> deleteSuccessor() {
        final DrasylAddress successor = fingerTable.getSuccessor();

        // nothing to delete, just return
        if (successor == null) {
            return composeSucceededFuture();
        }

        // find the last existence of successor in the finger table
        int i;
        for (i = Integer.SIZE; i > 0; i--) {
            final DrasylAddress ithFinger = fingerTable.get(i);
            if (ithFinger != null && ithFinger.equals(successor)) {
                break;
            }
        }

        // delete it, from the last existence to the first one
        return deleteFromIthToFirstFinger(i).then(() -> {
            // if predecessor is successor, delete it
            if (predecessor != null && Objects.equals(predecessor, fingerTable.getSuccessor())) {
                predecessor = null;
            }

            // try to fill successor
            return fillSuccessor().then(() -> {
                final DrasylAddress successor2 = fingerTable.getSuccessor();

                // if successor is still null or local node,
                // and the predecessor is another node, keep asking
                // it's predecessor until find local node's new successor
                if ((successor2 == null || localAddress.equals(successor2)) && predecessor != null && !localAddress.equals(predecessor)) {
                    final DrasylAddress predecessor1 = predecessor;

                    // update successor
                    return findNewSuccessor(predecessor1, successor2).then(() -> composableUpdateIthFinger(1, predecessor1, client));
                }
                else {
                    return composeSucceededFuture();
                }
            });
        });
    }

    private FutureComposer<Void> deleteFromIthToFirstFinger(final int j) {
        return composableUpdateIthFinger(j, null, client).then(future -> {
            if (j > 1) {
                return deleteFromIthToFirstFinger(j - 1);
            }
            else {
                return composableUpdateIthFinger(j, null, client);
            }
        });
    }

    private FutureComposer<DrasylAddress> findNewSuccessor(final DrasylAddress peer,
                                                           final DrasylAddress successor) {
        return composeFuture(client.lookup(BIND_NAME, RemoteChordNode.class, peer).getPredecessor()).then(future -> {
            final DrasylAddress myPredecessor = future.getNow();
            if (myPredecessor == null) {
                return composeSucceededFuture(peer);
            }

            // if p's predecessor is node is just deleted,
            // or itself (nothing found in predecessor), or local address,
            // p is current node's new successor, break
            if (myPredecessor.equals(peer) || myPredecessor.equals(localAddress) || myPredecessor.equals(successor)) {
                return composeSucceededFuture(peer);
            }
            // else, keep asking
            else {
                return findNewSuccessor(myPredecessor, successor);
            }
        });
    }

    public Future<Void> fixFinger(final int i) {
        LOG.debug("fixFinger({})", i);
        final long id = ithFingerStart(localAddress, i);
        LOG.debug("Refresh {}th finger: Find successor for id `{}` ({}) and check if it is still the same peer.", () -> i, () -> chordIdHex(id), () -> chordIdPosition(id));
        return composableFindSuccessor(id).then(future -> {
            if (future.isSuccess()) {
                final DrasylAddress ithFinger = future.getNow();
                LOG.debug("Successor for id `{}` ({}) is `{}`.", () -> chordIdHex(id), () -> chordIdPosition(id), () -> ithFinger);
                return composableUpdateIthFinger(i, ithFinger, client);
            }
            else {
                // timeout
                return composeSucceededFuture();
            }
        }).finish(group.next());
    }

    @JsonDeserialize(as = IdentityPublicKey.class)
    public interface DrasylAddressMixin {
    }

    public interface IdentityPublicKeyMixin {
        @JsonValue
        String toString();

        @SuppressWarnings("unused")
        @JsonCreator
        static DrasylAddress of(final String bytes) {
            // won't be called
            return null;
        }
    }
}
