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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * This class contains information about other peers. This includes the identities, public keys,
 * available interfaces, connections or relations (e.g. direct/relayed connection, super peer,
 * child, grandchild). Before a relation is set for a peer, it must be ensured that its information
 * is available. Likewise, the information may not be removed from a peer if the peer still has a
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

    public void addPeerInformation(CompressedPublicKey identity,
                                   PeerInformation peerInformation) {
        requireNonNull(identity);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            boolean created = !peers.containsKey(identity);
            PeerInformation existingInformation = peers.computeIfAbsent(identity, i -> PeerInformation.of());
            addInformationAndConditionalEventTrigger(identity, existingInformation, peerInformation, created);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    private void addInformationAndConditionalEventTrigger(CompressedPublicKey identity,
                                                          PeerInformation existingInformation,
                                                          PeerInformation peerInformation,
                                                          boolean created) {
        int existingPathCount = existingInformation.getPaths().size();
        existingInformation.add(peerInformation);
        int newPathCount = existingInformation.getPaths().size();

        if (existingPathCount == 0 && newPathCount > 0) {
            eventConsumer.accept(new PeerDirectEvent(new Peer(identity)));
        }
        else if (created && newPathCount == 0) {
            eventConsumer.accept(new PeerRelayEvent(new Peer(identity)));
        }
    }

    public void removePeerInformation(CompressedPublicKey identity,
                                      PeerInformation peerInformation) {
        requireNonNull(identity);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            PeerInformation existingInformation = peers.computeIfAbsent(identity, i -> PeerInformation.of());
            removeInformationAndConditionalEventTrigger(identity, existingInformation, peerInformation);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    private void removeInformationAndConditionalEventTrigger(CompressedPublicKey identity,
                                                             PeerInformation existingInformation,
                                                             PeerInformation peerInformation) {
        int existingPathCount = existingInformation.getPaths().size();
        existingInformation.remove(peerInformation);
        int newPathCount = existingInformation.getPaths().size();

        if (existingPathCount > 0 && newPathCount == 0) {
            if (identity.equals(superPeer) || superPeer == null) {
                eventConsumer.accept(new PeerUnreachableEvent(new Peer(identity)));
            }
            else {
                eventConsumer.accept(new PeerRelayEvent(new Peer(identity)));
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

    public boolean isChildren(CompressedPublicKey identity) {
        requireNonNull(identity);

        try {
            lock.readLock().lock();

            return children.contains(identity);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void addChildren(CompressedPublicKey... identities) {
        requireNonNull(identities);

        try {
            lock.writeLock().lock();

            children.addAll(List.of(identities));
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void removeChildren(CompressedPublicKey... identities) {
        requireNonNull(identities);

        try {
            lock.writeLock().lock();

            children.removeAll(List.of(identities));
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

    public PeerInformation getPeerInformation(CompressedPublicKey identity) {
        requireNonNull(identity);

        try {
            lock.readLock().lock();

            peers.computeIfAbsent(identity, i -> PeerInformation.of());
            return peers.get(identity);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public CompressedPublicKey getIdentity(CompressedPublicKey identity) {
        try {
            lock.readLock().lock();

            Optional<CompressedPublicKey> search = peers.keySet().stream().filter(i -> i.equals(identity)).findFirst();
            if (search.isPresent()) {
                return search.get();
            }
            else {
                return identity;
            }
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns identity and information about Super Peer. If no Super Peer is defined, then
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
                PeerInformation peerInformation = peers.computeIfAbsent(superPeer, i -> PeerInformation.of());
                return Pair.of(superPeer, peerInformation);
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

    public void setSuperPeer(CompressedPublicKey identity) {
        requireNonNull(identity);

        try {
            lock.writeLock().lock();

            this.superPeer = identity;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isSuperPeer(CompressedPublicKey identity) {
        requireNonNull(identity);

        try {
            lock.readLock().lock();

            return Objects.equals(superPeer, identity);
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
    public void addPeerInformationAndSetSuperPeer(CompressedPublicKey identity,
                                                  PeerInformation peerInformation) {
        requireNonNull(identity);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            boolean created = !peers.containsKey(identity);
            PeerInformation existingInformation = peers.computeIfAbsent(identity, i -> PeerInformation.of());
            addInformationAndConditionalEventTrigger(identity, existingInformation, peerInformation, created);
            superPeer = identity;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Shortcut for call {@link #addPeerInformation(CompressedPublicKey, PeerInformation)} and
     * {@link #addChildren(CompressedPublicKey...)}.
     */
    public void addPeerInformationAndAddChildren(CompressedPublicKey identity,
                                                 PeerInformation peerInformation) {
        requireNonNull(identity);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            boolean created = !peers.containsKey(identity);
            PeerInformation existingInformation = peers.computeIfAbsent(identity, i -> PeerInformation.of());
            addInformationAndConditionalEventTrigger(identity, existingInformation, peerInformation, created);
            children.add(identity);
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
    public void removeChildrenAndRemovePeerInformation(CompressedPublicKey identity,
                                                       PeerInformation peerInformation) {
        requireNonNull(identity);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            PeerInformation existingInformation = peers.computeIfAbsent(identity, i -> PeerInformation.of());
            removeInformationAndConditionalEventTrigger(identity, existingInformation, peerInformation);
            children.remove(identity);
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