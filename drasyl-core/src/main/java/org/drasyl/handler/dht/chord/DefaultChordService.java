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
import static org.drasyl.handler.dht.chord.ChordUtil.relativeChordId;
import static org.drasyl.handler.rmi.RmiUtil.OBJECT_MAPPER;
import static org.drasyl.util.FutureComposer.composeFuture;
import static org.drasyl.util.FutureComposer.composeSucceededFuture;

/**
 * This class is based on <a href="https://github.com/ChuanXia/Chord">Chord implementation of Chuan
 * Xia</a>.
 */
public class DefaultChordService implements ChordService {
    static {
        OBJECT_MAPPER.addMixIn(IdentityPublicKey.class, IdentityPublicKeyMixin.class);
        OBJECT_MAPPER.addMixIn(DrasylAddress.class, DrasylAddressMixin.class);
    }

    public static final String SERVICE_NAME = "ChordService";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultChordService.class);
    private final ChordFingerTable fingerTable;
    private final RmiClientHandler client;
    private final EventLoopGroup group = new NioEventLoopGroup();
    @RmiCaller
    private DrasylAddress caller;

    public DefaultChordService(final ChordFingerTable fingerTable, RmiClientHandler client) {
        this.fingerTable = requireNonNull(fingerTable);
        this.client = requireNonNull(client);
    }

    @Override
    public Future<Void> checkAlive() {
        return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, null);
    }

    @Override
    public Future<DrasylAddress> getPredecessor() {
        if (fingerTable.hasPredecessor()) {
            return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, fingerTable.getPredecessor());
        }
        else {
            return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, null);
        }
    }

    @Override
    public Future<DrasylAddress> getSuccessor() {
        if (fingerTable.hasSuccessor()) {
            return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, fingerTable.getSuccessor());
        }
        else {
            return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, null);
        }
    }

    @Override
    public Future<Void> offerAsPredecessor() {
        LOG.debug("Notified by `{}`.", caller);
        if (!fingerTable.hasPredecessor() || fingerTable.getLocalAddress().equals(fingerTable.getPredecessor())) {
            LOG.info("Set predecessor `{}`.", caller);
            fingerTable.setPredecessor(caller);
            // FIXME: wieso hier nicht checken, ob er als geeigneter fingers dient?
        }
        else {
            final long oldpre_id = chordId(fingerTable.getPredecessor());
            final long local_relative_id = relativeChordId(fingerTable.getLocalAddress(), oldpre_id);
            final long newpre_relative_id = relativeChordId(caller, oldpre_id);
            if (newpre_relative_id > 0 && newpre_relative_id < local_relative_id) {
                LOG.info("Set predecessor `{}`.", caller);
                fingerTable.setPredecessor(caller);
            }
        }

        return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, null);
    }

    /*
     * {@code n.find_predecessor(id)}
     */

    @Override
    public Future<DrasylAddress> findSuccessor(long id) {
        return composableFindSuccessor(id).finish(group.next());
    }

    public FutureComposer<DrasylAddress> composableFindSuccessor(final long id) {
        LOG.debug("Find successor of `{}`.", chordIdHex(id));

        // initialize return value as this node's successor (might be null)
        final DrasylAddress ret = fingerTable.getSuccessor();

        LOG.debug("Find successor of {} by asking id's predecessor for its successor.", chordIdHex(id));

        return findPredecessor(id).then(future -> {
            final DrasylAddress pre = future.getNow();
            // if other node found, ask it for its successor
            if (!Objects.equals(pre, fingerTable.getLocalAddress())) {
                if (pre != null) {
                    final ChordService service = client.lookup(SERVICE_NAME, ChordService.class, pre);
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
                return composeSucceededFuture(fingerTable.getLocalAddress());
            }
            return composeSucceededFuture(ret1);
        });
    }

    /*
     * deleteSuccessor
     */

    public FutureComposer<Void> deleteSuccessor() {
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
        deleteFromIthToFirstFinger(i);

        // if predecessor is successor, delete it
        if (fingerTable.hasPredecessor() && Objects.equals(fingerTable.getPredecessor(), fingerTable.getSuccessor())) {
            fingerTable.removePredecessor();
        }

        // try to fill successor
        return fillSuccessor().then(() -> {
            final DrasylAddress successor2 = fingerTable.getSuccessor();

            // if successor is still null or local node,
            // and the predecessor is another node, keep asking
            // it's predecessor until find local node's new successor
            if ((successor2 == null || fingerTable.getLocalAddress().equals(successor2)) && fingerTable.hasPredecessor() && !fingerTable.getLocalAddress().equals(fingerTable.getPredecessor())) {
                final DrasylAddress predecessor = fingerTable.getPredecessor();

                // update successor
                return findNewSuccessor(predecessor, successor2).then(() -> fingerTable.updateIthFinger(1, predecessor, client));
            }
            else {
                return composeSucceededFuture();
            }
        });
    }

    private FutureComposer<Void> deleteFromIthToFirstFinger(final int j) {
        return fingerTable.updateIthFinger(j, null, client).then(future -> {
            if (j > 1) {
                return deleteFromIthToFirstFinger(j - 1);
            }
            else {
                return fingerTable.updateIthFinger(j, null, client);
            }
        });
    }

    private FutureComposer<DrasylAddress> findNewSuccessor(final DrasylAddress peer,
                                                           final DrasylAddress successor) {
        final ChordService service = client.lookup(SERVICE_NAME, ChordService.class, peer);
        return composeFuture(service.getPredecessor()).then(future -> {
            DrasylAddress predecessor = future.getNow();
            if (predecessor == null) {
                return composeSucceededFuture(peer);
            }

            // if p's predecessor is node is just deleted,
            // or itself (nothing found in predecessor), or local address,
            // p is current node's new successor, break
            if (predecessor.equals(peer) || predecessor.equals(fingerTable.getLocalAddress()) || predecessor.equals(successor)) {
                return composeSucceededFuture(peer);
            }
            // else, keep asking
            else {
                return findNewSuccessor(predecessor, successor);
            }
        });
    }

    /*
     * {@code n.find_successor(id)}
     */

    public FutureComposer<DrasylAddress> findPredecessor(final long id) {
        LOG.debug("Find predecessor of `{}`", ChordUtil.chordIdHex(id));
        final DrasylAddress myAddress = fingerTable.getLocalAddress();
        final DrasylAddress mySuccessor = fingerTable.getSuccessor();
        final long findIdRelativeId = relativeChordId(id, myAddress);
        long mySuccessorRelativeId;
        if (mySuccessor == null) {
            mySuccessorRelativeId = 0;
        }
        else {
            mySuccessorRelativeId = relativeChordId(mySuccessor, myAddress);
        }

        return recursive(id, myAddress, findIdRelativeId, mySuccessorRelativeId, fingerTable.getLocalAddress());
    }

    @SuppressWarnings("java:S107")
    private FutureComposer<DrasylAddress> recursive(final long findId,
                                                    final DrasylAddress currentNode,
                                                    final long findIdRelativeId,
                                                    final long currentNodeSuccessorsRelativeId,
                                                    final DrasylAddress mostRecentlyAlive) {
        if (findIdRelativeId > 0 && findIdRelativeId <= currentNodeSuccessorsRelativeId) {
            return composeSucceededFuture(currentNode);
        }

        // if current node is local node, find my closest
        if (Objects.equals(currentNode, fingerTable.getLocalAddress())) {
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
        return composableClosestPrecedingFinger(findId).then(future -> {
            final DrasylAddress finger = future.getNow();
            if (currentNode.equals(finger)) {
                return composeSucceededFuture(finger);
            }
            else {
                return recursive(findId, finger, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive);
            }
        });
    }

    @SuppressWarnings("java:S107")
    private FutureComposer<DrasylAddress> findPeersClosest(final long findId,
                                                           final DrasylAddress currentNode,
                                                           final long findIdRelativeId,
                                                           final long currentNodeSuccessorsRelativeId,
                                                           final DrasylAddress mostRecentlyAlive) {
        final FutureComposer<DrasylAddress> lookupComposer;
        if (currentNode != null) {
            lookupComposer = composeFuture(client.lookup(SERVICE_NAME, ChordService.class, currentNode).findClosestFingerPreceding(findId));
        }
        else {
            lookupComposer = composeSucceededFuture(null);
        }
        return lookupComposer.then(future -> {
            final DrasylAddress closest = future.getNow();
            // if fail to get response, set currentNode to most recently
            if (closest == null) {
                final ChordService service = client.lookup(SERVICE_NAME, ChordService.class, mostRecentlyAlive);
                return composeFuture(service.getSuccessor()).then(future1 -> {
                    final DrasylAddress mostRecentlysSuccessor = future1.getNow();
                    if (mostRecentlysSuccessor == null) {
                        return composeSucceededFuture(fingerTable.getLocalAddress());
                    }
                    return recursive(findId, mostRecentlyAlive, findIdRelativeId, currentNodeSuccessorsRelativeId, mostRecentlyAlive);
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
                return requestClosestsSuccessor(findId, currentNode, closest);
            }
        });
    }

    private FutureComposer<DrasylAddress> requestClosestsSuccessor(final long findId,
                                                                   final DrasylAddress currentNode,
                                                                   final DrasylAddress closest) {
        final ChordService service = client.lookup(SERVICE_NAME, ChordService.class, closest);
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

                return recursive(findId, closest, findIdsRelativeId, closestSuccessorsRelativeId, currentNode);
            }
            // else currentNode sticks, ask currentNode's successor
            else {
                final ChordService service2 = client.lookup(SERVICE_NAME, ChordService.class, currentNode);
                return composeFuture(service2.getSuccessor());
            }
        });
    }

    /*
     * {@code n.closest_preceding_finger(id)}
     */

    @Override
    public Future<DrasylAddress> findClosestFingerPreceding(final long id) {
        return composableClosestPrecedingFinger(id).finish(group.next());
    }

    public FutureComposer<DrasylAddress> composableClosestPrecedingFinger(final long findId) {
        LOG.debug("Find closest finger preceding `{}`.", chordIdHex(findId));
        final long myId = chordId(fingerTable.getLocalAddress());
        final long findIdRelativeId = relativeChordId(findId, myId);

        // check from last item in finger table
        return checkFinger(myId, findId, findIdRelativeId, Integer.SIZE);
    }

    @SuppressWarnings("java:S3776")
    private FutureComposer<DrasylAddress> checkFinger(final long myId,
                                                      final long findId,
                                                      final long findIdRelative,
                                                      final int i) {
        if (i == 0) {
            LOG.debug("We're closest to `{}`.", chordIdHex(findId));
            return composeSucceededFuture(fingerTable.getLocalAddress());
        }
        else {
            return composeSucceededFuture(fingerTable.get(i)).then(future -> {
                final DrasylAddress ithFinger = future.getNow();
                if (ithFinger != null) {
                    // if its relative id is the closest, check if its alive
                    final long ithFingerId = chordId(ithFinger);
                    final long ithFingerRelativeId = relativeChordId(ithFingerId, myId);

                    if (ithFingerRelativeId > 0 && ithFingerRelativeId < findIdRelative) {
                        LOG.debug("{}th finger {} is closest preceding finger of {}.", i, chordIdHex(ithFingerId), chordIdHex(findId));
                        LOG.debug("Check if it is still alive.");

                        final ChordService service = client.lookup(SERVICE_NAME, ChordService.class, ithFinger);
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
                            return checkFinger(myId, findId, findIdRelative, i - 1);
                        });
                    }
                }
                return checkFinger(myId, findId, findIdRelative, i - 1);
            });
        }
    }

    /*
     * fillSuccessor
     */

    public FutureComposer<Void> fillSuccessor() {
        LOG.debug("Try to fill successor with candidates in finger table or even predecessor.");
        final DrasylAddress successor = fingerTable.getSuccessor();
        final FutureComposer<Void> future;
        if (successor == null || successor.equals(fingerTable.getLocalAddress())) {
            future = findSuccessorStartingFromIthFinger(2);
        }
        else {
            future = composeSucceededFuture();
        }

        return future.then(() -> {
            final DrasylAddress successor2 = fingerTable.getSuccessor();
            if ((successor2 == null || successor2.equals(fingerTable.getLocalAddress())) && fingerTable.hasPredecessor() && !fingerTable.getLocalAddress().equals(fingerTable.getPredecessor())) {
                return fingerTable.updateIthFinger(1, fingerTable.getPredecessor(), client);
            }
            else {
                return composeSucceededFuture();
            }
        });
    }

    private FutureComposer<Void> findSuccessorStartingFromIthFinger(final int i) {
        if (i <= Integer.SIZE) {
            final DrasylAddress ithFinger = fingerTable.get(i);
            if (ithFinger != null && !ithFinger.equals(fingerTable.getLocalAddress())) {
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
            return fingerTable.updateIthFinger(j, ithFinger, client).then(() -> updateFingersFromIthToFirstFinger(j - 1, ithFinger));
        }
        else {
            return composeSucceededFuture();
        }
    }

    @JsonDeserialize(as = IdentityPublicKey.class)
    public interface DrasylAddressMixin {
    }

    public interface IdentityPublicKeyMixin {
        @JsonValue
        String toString();

        @JsonCreator
        static DrasylAddress of(final String bytes) {
            // won't be called
            return null;
        }
    }
}
