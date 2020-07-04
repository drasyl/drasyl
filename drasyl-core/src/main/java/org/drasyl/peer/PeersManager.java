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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.SetMultimap;
import org.drasyl.event.Event;
import org.drasyl.event.Peer;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.event.PeerUnreachableEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.Pair;
import org.drasyl.util.SetUtil;
import org.drasyl.util.Triple;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * This class contains information about other peers. This includes the public keys, available
 * interfaces, connections or relations (e.g. direct/relayed connection, super peer, child,
 * grandchild). Before a relation is set for a peer, it must be ensured that its information is
 * available. Likewise, the information may not be removed from a peer if the peer still has a
 * relation
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
    private final Map<CompressedPublicKey, CompressedPublicKey> grandchildrenRoutes;
    private final Consumer<Event> eventConsumer;
    private CompressedPublicKey superPeer;

    public PeersManager(Consumer<Event> eventConsumer) {
        this(new ReentrantReadWriteLock(true), new HashMap<>(), HashMultimap.create(), new HashSet<>(), new HashMap<>(), null, eventConsumer);
    }

    PeersManager(ReadWriteLock lock,
                 Map<CompressedPublicKey, PeerInformation> peers,
                 SetMultimap<CompressedPublicKey, Path> paths,
                 Set<CompressedPublicKey> children,
                 Map<CompressedPublicKey, CompressedPublicKey> grandchildrenRoutes,
                 CompressedPublicKey superPeer,
                 Consumer<Event> eventConsumer) {
        this.lock = lock;
        this.peers = peers;
        this.paths = paths;
        this.children = children;
        this.grandchildrenRoutes = grandchildrenRoutes;
        this.superPeer = superPeer;
        this.eventConsumer = eventConsumer;
    }

    @Override
    public String toString() {
        return "PeersManager{" +
                "peers=" + peers +
                ", children=" + children +
                ", grandchildrenRoutes=" + grandchildrenRoutes +
                ", eventConsumer=" + eventConsumer +
                ", superPeer=" + superPeer +
                '}';
    }

    public Map<CompressedPublicKey, PeerInformation> getPeers() {
        try {
            lock.readLock().lock();

            return ImmutableMap.copyOf(peers);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Map<CompressedPublicKey, PeerInformation> getChildrenAndGrandchildren() {
        try {
            lock.readLock().lock();

            return ImmutableMap.copyOf(peers.entrySet().stream()
                    .filter(e -> children.contains(e.getKey()) || grandchildrenRoutes.containsKey(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            );
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Map<CompressedPublicKey, CompressedPublicKey> getGrandchildrenRoutes() {
        try {
            lock.readLock().lock();

            return Map.copyOf(grandchildrenRoutes);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns public key and information about Super Peer. If no Super Peer is defined, then
     * <code>null</code> is returned.
     *
     * @return
     */
    public Triple<CompressedPublicKey, PeerInformation, Set<Path>> getSuperPeer() {
        try {
            lock.readLock().lock();

            if (superPeer == null) {
                return null;
            }
            else {
                PeerInformation peerInformation = peers.getOrDefault(superPeer, PeerInformation.of());
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

            return children;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Pair<PeerInformation, Set<Path>> getPeer(CompressedPublicKey publicKey) {
        requireNonNull(publicKey);

        try {
            lock.readLock().lock();

            PeerInformation peerInformation = peers.get(publicKey);
            Set<Path> myPaths = this.paths.get(publicKey);
            if (peerInformation != null) {
                return Pair.of(peerInformation, myPaths);
            }
            else {
                return Pair.of(PeerInformation.of(), myPaths);
            }
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void setPeerInformationAndAddPath(CompressedPublicKey publicKey,
                                             PeerInformation peerInformation,
                                             Path path) {
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

    private void handlePeerStateTransition(CompressedPublicKey publicKey,
                                           PeerInformation existingInformation,
                                           Set<Path> existingPaths,
                                           PeerInformation newInformation,
                                           Set<Path> newPaths) {
        int existingPathCount;
        if (existingInformation == null) {
            existingPathCount = 0;
        }
        else {
            existingPathCount = existingPaths.size();
        }
        int newPathCount = newPaths.size();
        peers.put(publicKey, PeerInformation.of(newInformation.getEndpoints()));
        paths.replaceValues(publicKey, newPaths);

        if (existingPathCount == 0 && newPathCount > 0) {
            eventConsumer.accept(new PeerDirectEvent(new Peer(publicKey)));
        }
        else if ((existingInformation == null || existingPathCount > 0) && newPathCount == 0) {
            if (publicKey.equals(superPeer) || superPeer == null) {
                eventConsumer.accept(new PeerUnreachableEvent(new Peer(publicKey)));
            }
            else {
                eventConsumer.accept(new PeerRelayEvent(new Peer(publicKey)));
            }
        }
    }

    public void setPeerInformation(CompressedPublicKey publicKey,
                                   PeerInformation peerInformation) {
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

    public void removePath(CompressedPublicKey publicKey, Path path) {
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

    public void addGrandchildrenRoute(CompressedPublicKey grandchildren,
                                      CompressedPublicKey children) {
        requireNonNull(grandchildren);
        requireNonNull(children);

        try {
            lock.writeLock().lock();

            grandchildrenRoutes.put(grandchildren, children);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void removeGrandchildrenRoute(CompressedPublicKey grandchildren) {
        requireNonNull(grandchildren);

        try {
            lock.writeLock().lock();

            grandchildrenRoutes.remove(grandchildren);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void unsetSuperPeer() {
        try {
            lock.writeLock().lock();

            superPeer = null;

            // FIXME: events for all peers :O
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void unsetSuperPeerAndRemovePath(Path path) {
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

                // FIXME: events for all peers :O
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void setPeerInformationAndAddPathAndSetSuperPeer(CompressedPublicKey publicKey,
                                                            PeerInformation peerInformation,
                                                            Path path) {
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

    public void removeChildrenAndPath(CompressedPublicKey publicKey,
                                      Path path) {
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

    public void setPeerInformationAndAddPathAndChildren(CompressedPublicKey publicKey,
                                                        PeerInformation peerInformation,
                                                        Path path) {
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