/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.peer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import org.drasyl.event.Event;
import org.drasyl.event.Peer;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.Pair;
import org.drasyl.util.SetUtil;
import org.drasyl.util.Triple;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * This class contains information about other peers. This includes the public keys, available
 * interfaces, connections or relations (e.g. direct/relayed connection, super peer, child). Before
 * a relation is set for a peer, it must be ensured that its information is available. Likewise, the
 * information may not be removed from a peer if the peer still has a relation
 *
 * <p>
 * This class is optimized for concurrent access and is thread-safe.
 * </p>
 */
public class PeersManager {
    private final ReadWriteLock lock;
    private final Map<CompressedPublicKey, PeerInformation> peers;
    private final SetMultimap<CompressedPublicKey, Path> paths;
    private final Set<CompressedPublicKey> children;
    private final Consumer<Event> eventConsumer;
    private CompressedPublicKey superPeer;

    public PeersManager(final Consumer<Event> eventConsumer) {
        this(new ReentrantReadWriteLock(true), new HashMap<>(), HashMultimap.create(), new HashSet<>(), null, eventConsumer);
    }

    PeersManager(final ReadWriteLock lock,
                 final Map<CompressedPublicKey, PeerInformation> peers,
                 final SetMultimap<CompressedPublicKey, Path> paths,
                 final Set<CompressedPublicKey> children,
                 final CompressedPublicKey superPeer,
                 final Consumer<Event> eventConsumer) {
        this.lock = lock;
        this.peers = peers;
        this.paths = paths;
        this.children = children;
        this.superPeer = superPeer;
        this.eventConsumer = eventConsumer;
    }

    @Override
    public String toString() {
        try {
            lock.readLock().lock();

            return "PeersManager{" +
                    "peers=" + peers +
                    ", children=" + children +
                    ", eventConsumer=" + eventConsumer +
                    ", superPeer=" + superPeer +
                    '}';
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Map<CompressedPublicKey, PeerInformation> getPeers() {
        try {
            lock.readLock().lock();

            return Map.copyOf(peers);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Map<CompressedPublicKey, PeerInformation> getChildren() {
        try {
            lock.readLock().lock();

            // It is necessary to create a new HashMap because otherwise, this can raise a
            // ConcurrentModificationException.
            // See: https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/issues/77
            return new HashSet<>(children).stream().collect(Collectors.toMap(c -> c, peers::get));
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @return public key and information about Super Peer. If no Super Peer is defined, then
     * <code>null</code> is returned
     */
    public Triple<CompressedPublicKey, PeerInformation, Set<Path>> getSuperPeer() {
        try {
            lock.readLock().lock();

            if (superPeer == null) {
                return null;
            }
            else {
                final PeerInformation peerInformation = peers.getOrDefault(superPeer, PeerInformation.of());
                return Triple.of(superPeer, peerInformation, paths.get(superPeer));
            }
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public CompressedPublicKey getSuperPeerKey() {
        try {
            lock.readLock().lock();

            return superPeer;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Set<CompressedPublicKey> getChildrenKeys() {
        try {
            lock.readLock().lock();

            return Set.copyOf(children);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Pair<PeerInformation, Set<Path>> getPeer(final CompressedPublicKey publicKey) {
        requireNonNull(publicKey);

        try {
            lock.readLock().lock();

            final PeerInformation peerInformation = peers.get(publicKey);
            final Set<Path> myPaths = Set.copyOf(this.paths.get(publicKey));

            return Pair.of(Objects.requireNonNullElseGet(peerInformation, PeerInformation::of), myPaths);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void setPeerInformationAndAddPath(final CompressedPublicKey publicKey,
                                             final PeerInformation peerInformation,
                                             final Path path) {
        requireNonNull(publicKey);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            handlePeerStateTransition(
                    publicKey,
                    peers.get(publicKey),
                    paths.get(publicKey),
                    peerInformation,
                    SetUtil.merge(paths.get(publicKey), path)
            );
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    private void handlePeerStateTransition(final CompressedPublicKey publicKey,
                                           final PeerInformation existingInformation,
                                           final Set<Path> existingPaths,
                                           final PeerInformation newInformation,
                                           final Set<Path> newPaths) {
        final int existingPathCount;
        if (existingInformation == null) {
            existingPathCount = 0;
        }
        else {
            existingPathCount = existingPaths.size();
        }
        final int newPathCount = newPaths.size();
        peers.put(publicKey, PeerInformation.of(newInformation.getEndpoints()));
        paths.replaceValues(publicKey, newPaths);

        if (existingPathCount == 0 && newPathCount > 0) {
            eventConsumer.accept(new PeerDirectEvent(Peer.of(publicKey)));
        }
        else if ((existingInformation == null || existingPathCount > 0) && newPathCount == 0 &&
                ((!publicKey.equals(superPeer) && superPeer != null) ||
                        children.contains(publicKey))) {
            eventConsumer.accept(new PeerRelayEvent(Peer.of(publicKey)));
        }
    }

    public void setPeerInformation(final CompressedPublicKey publicKey,
                                   final PeerInformation peerInformation) {
        requireNonNull(publicKey);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            handlePeerStateTransition(
                    publicKey,
                    peers.get(publicKey),
                    paths.get(publicKey),
                    peerInformation,
                    paths.get(publicKey)
            );
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void addPeer(final CompressedPublicKey publicKey) {
        requireNonNull(publicKey);

        try {
            lock.writeLock().lock();

            final PeerInformation existingInformation = peers.get(publicKey);
            if (existingInformation == null) {
                handlePeerStateTransition(
                        publicKey,
                        null,
                        paths.get(publicKey),
                        PeerInformation.of(),
                        paths.get(publicKey)
                );
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void removePath(final CompressedPublicKey publicKey, final Path path) {
        requireNonNull(publicKey);
        requireNonNull(path);

        try {
            lock.writeLock().lock();

            handlePeerStateTransition(
                    publicKey,
                    peers.get(publicKey),
                    paths.get(publicKey),
                    peers.get(publicKey),
                    SetUtil.difference(paths.get(publicKey), path)
            );
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void unsetSuperPeer() {
        try {
            lock.writeLock().lock();

            superPeer = null;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void unsetSuperPeerAndRemovePath(final Path path) {
        requireNonNull(path);

        try {
            lock.writeLock().lock();

            if (superPeer != null) {
                handlePeerStateTransition(
                        superPeer,
                        peers.get(superPeer),
                        paths.get(superPeer),
                        peers.get(superPeer),
                        SetUtil.difference(paths.get(superPeer), path)
                );
                superPeer = null;

                // TODO: send PeerRelayEvent for all peers without direct connection? (https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/issues/72)
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void setPeerInformationAndAddPathAndSetSuperPeer(final CompressedPublicKey publicKey,
                                                            final PeerInformation peerInformation,
                                                            final Path path) {
        requireNonNull(publicKey);
        requireNonNull(peerInformation);
        requireNonNull(path);

        try {
            lock.writeLock().lock();

            handlePeerStateTransition(
                    publicKey,
                    peers.get(publicKey),
                    paths.get(publicKey),
                    peerInformation,
                    SetUtil.merge(paths.get(publicKey), path)
            );
            superPeer = publicKey;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void removeChildrenAndPath(final CompressedPublicKey publicKey,
                                      final Path path) {
        requireNonNull(publicKey);
        requireNonNull(path);

        try {
            lock.writeLock().lock();

            handlePeerStateTransition(
                    publicKey,
                    peers.get(publicKey),
                    paths.get(publicKey),
                    peers.get(publicKey),
                    SetUtil.difference(paths.get(publicKey), path)
            );
            children.remove(publicKey);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void setPeerInformationAndAddPathAndChildren(final CompressedPublicKey publicKey,
                                                        final PeerInformation peerInformation,
                                                        final Path path) {
        requireNonNull(publicKey);
        requireNonNull(peerInformation);
        requireNonNull(path);

        try {
            lock.writeLock().lock();

            handlePeerStateTransition(
                    publicKey,
                    peers.get(publicKey),
                    paths.get(publicKey),
                    peerInformation,
                    SetUtil.merge(paths.get(publicKey), path)
            );
            children.add(publicKey);
        }
        finally {
            lock.writeLock().unlock();
        }
    }
}