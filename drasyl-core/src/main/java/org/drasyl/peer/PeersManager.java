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

import com.google.common.collect.ImmutableMap;
import org.drasyl.event.Event;
import org.drasyl.event.Peer;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.event.PeerUnreachableEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    private final Set<CompressedPublicKey> children;
    private final Map<CompressedPublicKey, CompressedPublicKey> grandchildrenRoutes;
    private final Consumer<Event> eventConsumer;
    private CompressedPublicKey superPeer;

    public PeersManager(Consumer<Event> eventConsumer) {
        this(new ReentrantReadWriteLock(true), new HashMap<>(), new HashSet<>(), new HashMap<>(), null, eventConsumer);
    }

    PeersManager(ReadWriteLock lock,
                 Map<CompressedPublicKey, PeerInformation> peers,
                 Set<CompressedPublicKey> children,
                 Map<CompressedPublicKey, CompressedPublicKey> grandchildrenRoutes,
                 CompressedPublicKey superPeer,
                 Consumer<Event> eventConsumer) {
        this.lock = lock;
        this.peers = peers;
        this.children = children;
        this.grandchildrenRoutes = grandchildrenRoutes;
        this.superPeer = superPeer;
        this.eventConsumer = eventConsumer;
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

    public void addPeerInformation(CompressedPublicKey publicKey,
                                   PeerInformation peerInformation) {
        requireNonNull(publicKey);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            boolean created = !peers.containsKey(publicKey);
            PeerInformation existingInformation = peers.computeIfAbsent(publicKey, key -> PeerInformation.of());
            addInformationAndConditionalEventTrigger(publicKey, existingInformation, peerInformation, created);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    private void addInformationAndConditionalEventTrigger(CompressedPublicKey publicKey,
                                                          PeerInformation existingInformation,
                                                          PeerInformation peerInformation,
                                                          boolean created) {
        int existingPathCount = existingInformation.getPaths().size();
        existingInformation.add(peerInformation);
        int newPathCount = existingInformation.getPaths().size();

        if (existingPathCount == 0 && newPathCount > 0) {
            eventConsumer.accept(new PeerDirectEvent(new Peer(publicKey)));
        }
        else if (created && newPathCount == 0) {
            eventConsumer.accept(new PeerRelayEvent(new Peer(publicKey)));
        }
    }

    public void removePeerInformation(CompressedPublicKey publicKey,
                                      PeerInformation peerInformation) {
        requireNonNull(publicKey);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            PeerInformation existingInformation = peers.computeIfAbsent(publicKey, key -> PeerInformation.of());
            removeInformationAndConditionalEventTrigger(publicKey, existingInformation, peerInformation);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    private void removeInformationAndConditionalEventTrigger(CompressedPublicKey publicKey,
                                                             PeerInformation existingInformation,
                                                             PeerInformation peerInformation) {
        int existingPathCount = existingInformation.getPaths().size();
        existingInformation.remove(peerInformation);
        int newPathCount = existingInformation.getPaths().size();

        if (existingPathCount > 0 && newPathCount == 0) {
            if (publicKey.equals(superPeer) || superPeer == null) {
                eventConsumer.accept(new PeerUnreachableEvent(new Peer(publicKey)));
            }
            else {
                eventConsumer.accept(new PeerRelayEvent(new Peer(publicKey)));
            }
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

    public boolean isChildren(CompressedPublicKey publicKey) {
        requireNonNull(publicKey);

        try {
            lock.readLock().lock();

            return children.contains(publicKey);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void addChildren(CompressedPublicKey... publicKeys) {
        requireNonNull(publicKeys);

        try {
            lock.writeLock().lock();

            children.addAll(List.of(publicKeys));
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void removeChildren(CompressedPublicKey... publicKeys) {
        requireNonNull(publicKeys);

        try {
            lock.writeLock().lock();

            children.removeAll(List.of(publicKeys));
        }
        finally {
            lock.writeLock().unlock();
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

    public PeerInformation getPeerInformation(CompressedPublicKey publicKey) {
        requireNonNull(publicKey);

        try {
            lock.readLock().lock();

            PeerInformation peerInformation = peers.get(publicKey);
            if (peerInformation != null) {
                return peerInformation;
            }
            else {
                return PeerInformation.of();
            }
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
    public Pair<CompressedPublicKey, PeerInformation> getSuperPeer() {
        try {
            lock.readLock().lock();

            if (superPeer == null) {
                return null;
            }
            else {
                PeerInformation peerInformation = peers.get(superPeer);
                if (peerInformation != null) {
                    return Pair.of(superPeer, peerInformation);
                }
                else {
                    return Pair.of(superPeer, PeerInformation.of());
                }
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

    public void setSuperPeer(CompressedPublicKey publicKey) {
        requireNonNull(publicKey);

        try {
            lock.writeLock().lock();

            this.superPeer = publicKey;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isSuperPeer(CompressedPublicKey publicKey) {
        requireNonNull(publicKey);

        try {
            lock.readLock().lock();

            return Objects.equals(superPeer, publicKey);
        }
        finally {
            lock.readLock().unlock();
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

    /**
     * Shortcut for call {@link #addPeerInformation(CompressedPublicKey, PeerInformation)} and
     * {@link #setSuperPeer(CompressedPublicKey)}.
     */
    public void addPeerInformationAndSetSuperPeer(CompressedPublicKey publicKey,
                                                  PeerInformation peerInformation) {
        requireNonNull(publicKey);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            boolean created = !peers.containsKey(publicKey);
            PeerInformation existingInformation = peers.computeIfAbsent(publicKey, i -> PeerInformation.of());
            addInformationAndConditionalEventTrigger(publicKey, existingInformation, peerInformation, created);
            superPeer = publicKey;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Shortcut for call {@link #addPeerInformation(CompressedPublicKey, PeerInformation)} and
     * {@link #addChildren(CompressedPublicKey...)}.
     */
    public void addPeerInformationAndChildren(CompressedPublicKey publicKey,
                                              PeerInformation peerInformation) {
        requireNonNull(publicKey);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            boolean created = !peers.containsKey(publicKey);
            PeerInformation existingInformation = peers.computeIfAbsent(publicKey, i -> PeerInformation.of());
            addInformationAndConditionalEventTrigger(publicKey, existingInformation, peerInformation, created);
            children.add(publicKey);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Shortcut for call {@link #unsetSuperPeer()} and {@link #removePeerInformation(CompressedPublicKey,
     * PeerInformation)}.
     *
     * @return
     */
    public void unsetSuperPeerAndRemovePeerInformation(PeerInformation peerInformation) {
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            if (superPeer != null) {
                PeerInformation existingInformation = peers.computeIfAbsent(superPeer, i -> PeerInformation.of());
                removeInformationAndConditionalEventTrigger(superPeer, existingInformation, peerInformation);
                superPeer = null;
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Shortcut for call {@link #removeChildren(CompressedPublicKey...)} and {@link
     * #removePeerInformation(CompressedPublicKey, PeerInformation)}.
     *
     * @return
     */
    public void removeChildrenAndPeerInformation(CompressedPublicKey publicKey,
                                                 PeerInformation peerInformation) {
        requireNonNull(publicKey);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            PeerInformation existingInformation = peers.computeIfAbsent(publicKey, i -> PeerInformation.of());
            removeInformationAndConditionalEventTrigger(publicKey, existingInformation, peerInformation);
            children.remove(publicKey);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public Map<CompressedPublicKey, PeerInformation> getChildren() {
        try {
            lock.readLock().lock();

            return ImmutableMap.copyOf(peers.entrySet().stream()
                    .filter(e -> children.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            );
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
}