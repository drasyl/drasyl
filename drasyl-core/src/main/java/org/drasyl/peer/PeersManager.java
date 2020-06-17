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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableMap;
import org.drasyl.event.Event;
import org.drasyl.event.Peer;
import org.drasyl.identity.Identity;
import org.drasyl.util.MapToPairArraySerializer;
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
import static org.drasyl.event.EventType.EVENT_PEER_DIRECT;
import static org.drasyl.event.EventType.EVENT_PEER_RELAY;
import static org.drasyl.event.EventType.EVENT_PEER_UNREACHABLE;

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
    @JsonSerialize(using = MapToPairArraySerializer.class)
    private final Map<Identity, PeerInformation> peers;
    private final Set<Identity> children;
    @JsonSerialize(using = MapToPairArraySerializer.class)
    private final Map<Identity, Identity> grandchildrenRoutes;
    private final Consumer<Event> eventConsumer;
    private Identity superPeer;

    public PeersManager(Consumer<Event> eventConsumer) {
        this(new ReentrantReadWriteLock(true), new HashMap<>(), new HashSet<>(), new HashMap<>(), null, eventConsumer);
    }

    PeersManager(ReadWriteLock lock,
                 Map<Identity, PeerInformation> peers,
                 Set<Identity> children,
                 Map<Identity, Identity> grandchildrenRoutes,
                 Identity superPeer,
                 Consumer<Event> eventConsumer) {
        this.lock = lock;
        this.peers = peers;
        this.children = children;
        this.grandchildrenRoutes = grandchildrenRoutes;
        this.superPeer = superPeer;
        this.eventConsumer = eventConsumer;
    }

    public Map<Identity, PeerInformation> getPeers() {
        try {
            lock.readLock().lock();

            return ImmutableMap.copyOf(peers);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void addPeerInformation(Identity identity,
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

    private void addInformationAndConditionalEventTrigger(Identity identity,
                                                          PeerInformation existingInformation,
                                                          PeerInformation peerInformation,
                                                          boolean created) {
        int existingPathCount = existingInformation.getPaths().size();
        existingInformation.add(peerInformation);
        int newPathCount = existingInformation.getPaths().size();

        if (existingPathCount == 0 && newPathCount > 0) {
            eventConsumer.accept(new Event(EVENT_PEER_DIRECT, new Peer(identity)));
        }
        else if (created && newPathCount == 0) {
            eventConsumer.accept(new Event(EVENT_PEER_RELAY, new Peer(identity)));
        }
    }

    public void removePeerInformation(Identity identity,
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

    private void removeInformationAndConditionalEventTrigger(Identity identity,
                                                             PeerInformation existingInformation,
                                                             PeerInformation peerInformation) {
        int existingPathCount = existingInformation.getPaths().size();
        existingInformation.remove(peerInformation);
        int newPathCount = existingInformation.getPaths().size();

        if (existingPathCount > 0 && newPathCount == 0) {
            if (identity.equals(superPeer) || superPeer == null) {
                eventConsumer.accept(new Event(EVENT_PEER_UNREACHABLE, new Peer(identity)));
            }
            else {
                eventConsumer.accept(new Event(EVENT_PEER_RELAY, new Peer(identity)));
            }
        }
    }

    @JsonIgnore
    public Map<Identity, PeerInformation> getChildrenAndGrandchildren() {
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

    public boolean isChildren(Identity identity) {
        requireNonNull(identity);

        try {
            lock.readLock().lock();

            return children.contains(identity);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void addChildren(Identity... identities) {
        requireNonNull(identities);

        try {
            lock.writeLock().lock();

            children.addAll(List.of(identities));
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void removeChildren(Identity... identities) {
        requireNonNull(identities);

        try {
            lock.writeLock().lock();

            children.removeAll(List.of(identities));
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public Map<Identity, Identity> getGrandchildrenRoutes() {
        try {
            lock.readLock().lock();

            return Map.copyOf(grandchildrenRoutes);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void addGrandchildrenRoute(Identity grandchildren, Identity children) {
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

    public void removeGrandchildrenRoute(Identity grandchildren) {
        requireNonNull(grandchildren);

        try {
            lock.writeLock().lock();

            grandchildrenRoutes.remove(grandchildren);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public PeerInformation getPeerInformation(Identity identity) {
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

    public Identity getIdentity(Identity identity) {
        try {
            lock.readLock().lock();

            Optional<Identity> search = peers.keySet().stream().filter(i -> i.equals(identity)).findFirst();
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
    public Pair<Identity, PeerInformation> getSuperPeer() {
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

    public void setSuperPeer(Identity identity) {
        requireNonNull(identity);

        try {
            lock.writeLock().lock();

            this.superPeer = identity;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isSuperPeer(Identity identity) {
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
     * Shortcut for call {@link #addPeerInformation(Identity, PeerInformation)} and {@link
     * #setSuperPeer(Identity)}.
     */
    public void addPeerInformationAndSetSuperPeer(Identity identity,
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
     * Shortcut for call {@link #addPeerInformation(Identity, PeerInformation)} and {@link
     * #addChildren(Identity...)}.
     */
    public void addPeerInformationAndAddChildren(Identity identity,
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
     * Shortcut for call {@link #unsetSuperPeer()} and {@link #removePeerInformation(Identity, PeerInformation)}.
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
     * Shortcut for call {@link #removeChildren(Identity...)} and {@link
     * #removePeerInformation(Identity, PeerInformation)}.
     *
     * @return
     */
    public void removeChildrenAndRemovePeerInformation(Identity identity,
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

    /**
     * Shortcut for call {@link #addPeerInformation(Identity, PeerInformation)} (Identity...)} and
     * {@link #addGrandchildrenRoute(Identity, Identity)}.
     *
     * @return
     */
    public void addPeerInformationAndAddGrandchildren(Identity grandchildren,
                                                      PeerInformation grandchildrenInformation,
                                                      Identity children) {
        requireNonNull(grandchildren);
        requireNonNull(grandchildrenInformation);
        requireNonNull(children);

        try {
            lock.writeLock().lock();

            boolean created = !peers.containsKey(grandchildren);
            PeerInformation existingInformation = peers.computeIfAbsent(grandchildren, i -> PeerInformation.of());
            addInformationAndConditionalEventTrigger(grandchildren, existingInformation, grandchildrenInformation, created);
            grandchildrenRoutes.put(grandchildren, children);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Shortcut for call {@link #removeGrandchildrenRoute(Identity)} and {@link
     * #removePeerInformation(Identity, PeerInformation)}.
     *
     * @return
     */
    public void removeGrandchildrenRouteAndRemovePeerInformation(Identity grandchildren,
                                                                 PeerInformation grandchildrenInformation) {
        requireNonNull(grandchildren);
        requireNonNull(grandchildrenInformation);

        try {
            lock.writeLock().lock();

            PeerInformation existingInformation = peers.computeIfAbsent(grandchildren, i -> PeerInformation.of());
            removeInformationAndConditionalEventTrigger(grandchildren, existingInformation, grandchildrenInformation);
            grandchildrenRoutes.remove(grandchildren);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    @JsonSerialize(using = MapToPairArraySerializer.class)
    public Map<Identity, PeerInformation> getChildren() {
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