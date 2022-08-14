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
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
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
public class LocalChordNode implements RemoteChordNode {
    public static final String SERVICE_NAME = "ChordService";
    private static final Logger LOG = LoggerFactory.getLogger(LocalChordNode.class);
    private final ChordFingerTable fingerTable;
    private final RmiClientHandler client;
    private final EventLoopGroup group = new NioEventLoopGroup();
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
        sb.append("PREDECESSOR:  " + predecessor + " " + (predecessor != null ? chordIdHex(predecessor) + " (" + chordIdPosition(predecessor) + ")" : ""));
        sb.append(System.lineSeparator());
        sb.append("FINGER TABLE:");
        sb.append(System.lineSeparator());

        // header
        sb.append("No.\tStart\t\t\tAddress\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\tId");
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
            LOG.info("Set predecessor `{}`.", caller);
            predecessor = caller;
            // FIXME: wieso hier nicht checken, ob er als geeigneter fingers dient?
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

    @Override
    public Future<DrasylAddress> findSuccessor(long id) {
        LOG.debug("Find successor of `{}`.", chordIdHex(id));

        // initialize return value as this node's successor (might be null)
        final DrasylAddress ret = fingerTable.getSuccessor();

        LOG.debug("Find successor of {} by asking id's predecessor for its successor.", chordIdHex(id));

        return findPredecessor(id).then(future -> {
            final DrasylAddress pre = future.getNow();
            // if other node found, ask it for its successor
            if (!Objects.equals(pre, localAddress)) {
                if (pre != null) {
                    final RemoteChordNode service = client.lookup(SERVICE_NAME, RemoteChordNode.class, pre);
                    return composeFuture(service.getSuccessor());
                }
                else {
                    return composeSucceededFuture(null);
                }
            }
            else {
                return composeSucceededFuture(ret);
            }
        }).then(future -> {
            final DrasylAddress ret1 = future.getNow();
            if (ret1 == null) {
                return composeSucceededFuture(localAddress);
            }
            return composeSucceededFuture(ret1);
        }).finish(group.next());
    }

    @Override
    public Future<Boolean> isStable() {
        // no blocking call necessary / return result immediately
        return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, predecessor == null && fingerTable.getSuccessor() == null || predecessor != null && fingerTable.getSuccessor() != null);
    }

    /**
     * Find predecessor for {@code id}.
     * <p>
     * {@code n.find_predecessor(id)}
     */
    private FutureComposer<DrasylAddress> findPredecessor(final long id) {
        LOG.debug("Find predecessor of `{}`", chordIdHex(id));
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
    private FutureComposer<DrasylAddress> findPredecessorRecursive(final long findId,
                                                                   final DrasylAddress currentNode,
                                                                   final long findIdRelativeId,
                                                                   final long currentNodeSuccessorsRelativeId,
                                                                   final DrasylAddress mostRecentlyAlive) {
        if (findIdRelativeId > 0 && findIdRelativeId <= currentNodeSuccessorsRelativeId) {
            return composeSucceededFuture(currentNode);
        }

        // if current node is local node, find my closest
        if (Objects.equals(currentNode, localAddress)) {
            return findMyClosest(findId, currentNode, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive);
        }
        // else current node is remote node, sent request to it for its closest
        else {
            return findPeersClosest(findId, currentNode, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive);
        }
    }

    @SuppressWarnings("java:S107")
    private FutureComposer<DrasylAddress> findMyClosest(final long findId,
                                                        final DrasylAddress currentNode,
                                                        final long findIdRelativeId,
                                                        final long currentNodeSuccessorsRelativeId,
                                                        final DrasylAddress mostRecentlyAlive) {
        LOG.debug("Find my closest.");
        return composeFuture(findClosestFingerPreceding(findId)).then(future -> {
            final DrasylAddress finger = future.getNow();
            if (currentNode.equals(finger)) {
                return composeSucceededFuture(finger);
            }
            else {
                return findPredecessorRecursive(findId, finger, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive);
            }
        });
    }

    @SuppressWarnings("java:S107")
    private FutureComposer<DrasylAddress> findPeersClosest(final long findId,
                                                           final DrasylAddress currentNode,
                                                           final long findIdRelativeId,
                                                           final long currentNodeSuccessorsRelativeId,
                                                           final DrasylAddress mostRecentlyAlive) {
        LOG.debug("Find peers closest.");
        final FutureComposer<DrasylAddress> lookupComposer;
        if (currentNode != null) {
            lookupComposer = composeFuture(client.lookup(SERVICE_NAME, RemoteChordNode.class, currentNode).findClosestFingerPreceding(findId));
        }
        else {
            lookupComposer = composeSucceededFuture(null);
        }
        return lookupComposer.then(future -> {
            final DrasylAddress closest = future.getNow();
            // if fail to get response, set currentNode to most recently
            if (closest == null) {
                final RemoteChordNode service = client.lookup(SERVICE_NAME, RemoteChordNode.class, mostRecentlyAlive);
                return composeFuture(service.getSuccessor()).then(future1 -> {
                    final DrasylAddress mostRecentlysSuccessor = future1.getNow();
                    if (mostRecentlysSuccessor == null) {
                        return composeSucceededFuture(localAddress);
                    }
                    return findPredecessorRecursive(findId, mostRecentlyAlive, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive);
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
                return requestClosestSuccessor(findId, currentNode, closest);
            }
        });
    }

    private FutureComposer<DrasylAddress> requestClosestSuccessor(final long findId,
                                                                  final DrasylAddress currentNode,
                                                                  final DrasylAddress closest) {
        final RemoteChordNode service = client.lookup(SERVICE_NAME, RemoteChordNode.class, closest);
        return composeFuture(service.getSuccessor()).then(future -> {
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
                final RemoteChordNode service2 = client.lookup(SERVICE_NAME, RemoteChordNode.class, currentNode);
                return composeFuture(service2.getSuccessor());
            }
        });
    }

    @Override
    public Future<DrasylAddress> findClosestFingerPreceding(final long id) {
        LOG.debug("Find closest finger preceding `{}`.", chordIdHex(id));

        // check from last item in finger table
        return checkFinger(id, Integer.SIZE).finish(group.next());
    }

    @SuppressWarnings("java:S3776")
    private FutureComposer<DrasylAddress> checkFinger(final long findId,
                                                      final int i) {
        if (i == 0) {
            LOG.debug("We're closest to `{}`.", chordIdHex(findId));
            return composeSucceededFuture(localAddress);
        }
        else {
            return composeSucceededFuture(fingerTable.get(i)).then(future -> {
                final DrasylAddress ithFinger = future.getNow();
                if (ithFinger != null) {
                    // if its relative id is the closest, check if its alive
                    final long ithFingerId = chordId(ithFinger);
                    final long ithFingerRelativeId = relativeChordId(ithFingerId, localAddress);

                    final long findIdRelative = relativeChordId(findId, localAddress);
                    if (ithFingerRelativeId > 0 && ithFingerRelativeId < findIdRelative) {
                        LOG.debug("{}th finger `{}` is closest preceding finger of id `{}`.", i, chordIdHex(ithFingerId), chordIdHex(findId));

                        if (localAddress.equals(ithFinger)) {
                            LOG.debug("That is me. Finger is for sure alive ;).");
                            return composeSucceededFuture(ithFinger);
                        }

                        LOG.debug("Check if it is still alive.");
                        final RemoteChordNode service = client.lookup(SERVICE_NAME, RemoteChordNode.class, ithFinger);
                        return composeFuture(service.checkAlive()).then(future2 -> {
                            //it is alive, return it
                            if (future2.isSuccess()) {
                                LOG.debug("Peer is still alive.");
                                return composeSucceededFuture(ithFinger);
                            }
                            // else, remove its existence from finger table
                            else {
                                LOG.warn("Peer `{}` is not alive. Remove it from finger table.", ithFinger);
                                fingerTable.removePeer(ithFinger);
                            }
                            return checkFinger(findId, i - 1);
                        });
                    }
                }
                return checkFinger(findId, i - 1);
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
            final RemoteChordNode service = client.lookup(SERVICE_NAME, RemoteChordNode.class, value);
            return composeFuture(service.offerAsPredecessor());
        }
        else {
            return composeSucceededFuture();
        }
    }

    /**
     * Join circle by contacting {@code contact}.
     */
    public Future<Void> join(final DrasylAddress contact) {
        final RemoteChordNode contactService = client.lookup(SERVICE_NAME, RemoteChordNode.class, contact);
        return composeFuture(contactService.findSuccessor(chordId(localAddress))).then(future -> {
            if (future.isSuccess()) {
                final DrasylAddress successor = future.getNow();
                LOG.info("Successor for id `{}` is `{}`.", chordIdHex(localAddress), successor);
                LOG.info("Set `{}` as our successor.", successor);
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
            final RemoteChordNode service = client.lookup(SERVICE_NAME, RemoteChordNode.class, predecessor);
            return service.checkAlive().addListener((FutureListener<Void>) future -> {
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
    @SuppressWarnings("java:S3776")
    public Future<Void> stabilize() {
        final DrasylAddress successor = fingerTable.getSuccessor();
        final FutureComposer<Void> voidFuture;
        if (successor == null || successor.equals(localAddress)) {
            // Try to fill successor with candidates in finger table or even predecessor
            voidFuture = fillSuccessor();//fill
        }
        else {
            voidFuture = composeSucceededFuture();
        }

        return voidFuture.then(future -> {
            if (successor != null && !successor.equals(localAddress)) {
                LOG.debug("Check if successor has still us a predecessor.");

                // try to get my successor's predecessor
                final RemoteChordNode service1 = client.lookup(SERVICE_NAME, RemoteChordNode.class, successor);
                return composeFuture(service1.getPredecessor()).then(future2 -> {
                    // if bad connection with successor! delete successor
                    DrasylAddress x = future2.getNow();
                    if (x == null) {
                        LOG.debug("Bad connection with successor `{}`. Delete successor from finger table.", successor);
                        return deleteSuccessor();
                    }

                    // else if successor's predecessor is not itself
                    else if (!x.equals(successor)) {
                        if (x.equals(localAddress)) {
                            LOG.debug("Successor has still us as predecessor. All fine.");
                        }
                        else {
                            LOG.debug("Successor's predecessor is {}.", x);
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
                            final RemoteChordNode service = client.lookup(SERVICE_NAME, RemoteChordNode.class, successor);
                            return composeFuture(service.offerAsPredecessor());
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
            return composableUpdateIthFinger(j, ithFinger, client).then(updateFingersFromIthToFirstFinger(j - 1, ithFinger));
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
                    // update successor
                    return findNewSuccessor(predecessor, successor2).then(composableUpdateIthFinger(1, predecessor, client));
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
        final RemoteChordNode service = client.lookup(SERVICE_NAME, RemoteChordNode.class, peer);
        return composeFuture(service.getPredecessor()).then(future -> {
            DrasylAddress predecessor = future.getNow();
            if (predecessor == null) {
                return composeSucceededFuture(peer);
            }

            // if p's predecessor is node is just deleted,
            // or itself (nothing found in predecessor), or local address,
            // p is current node's new successor, break
            if (predecessor.equals(peer) || predecessor.equals(localAddress) || predecessor.equals(successor)) {
                return composeSucceededFuture(peer);
            }
            // else, keep asking
            else {
                return findNewSuccessor(predecessor, successor);
            }
        });
    }

    public Future<Void> fixFinger(final int i) {
        final long id = ithFingerStart(localAddress, i);
        LOG.debug("Refresh {}th finger: Find successor for id `{}` and check if it is still the same peer.", i, chordIdHex(id));
        return composeFuture(findSuccessor(id)).then(future -> {
            if (future.isSuccess()) {
                final DrasylAddress ithFinger = future.getNow();
                LOG.debug("Successor for id `{}` is `{}`.", chordIdHex(id), ithFinger);
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
