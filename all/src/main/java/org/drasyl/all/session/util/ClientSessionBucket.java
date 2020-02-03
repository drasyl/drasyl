/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.session.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.drasyl.all.models.SessionChannel;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

import org.drasyl.all.models.SessionUID;
import org.drasyl.all.session.Session;
import org.drasyl.all.models.Pair;
import io.netty.channel.ChannelId;

/**
 * This class is responsible for mapping remote systems, the corresponding
 * channels (vice versa) and the local and network-wide view.
 *
 * <p>
 * This class is optimized for concurrent access and is thread-safe.
 * </p>
 *
 * <p>
 * <i>Note: This class has been optimized for speed because the method
 * {@link #aggregateChannelMembers(String)} are executed at each
 * {@link org.drasyl.all.messages.ForwardableMessage ForwardableMessage}
 * among others. Since inverse maps are required, twice as much memory is used
 * as for a simple map, but a key can be found in O(1), even in the inverse
 * map.</i>
 * </p>
 */
public class ClientSessionBucket {
    private ReadWriteLock lock = new ReentrantReadWriteLock(true);
    /**
     * Saves all known channels of the network with the corresponding remote systems
     * (ID's). channel-&gt;{id,id}
     */
    private final SetMultimap<SessionChannel, SessionUID> networkChannelClients;
    /**
     * Saves all known remote systems (ID's) of the network with the corresponding
     * channels. ID-&gt;{channel,channel}
     */
    private final SetMultimap<SessionUID, SessionChannel> networkClientChannels;
    /**
     * Saves all connected local remote systems with the corresponding
     * {@link Session} reference.
     */
    private final Map<SessionUID, Session> localClientSessions;
    private final SessionUID localRelayUID;
    private final BiMap<ChannelId, Session> initializedSessions;

    /**
     * Creates a new {@link ClientSessionBucket} object.
     *
     * @param relayUID the ID of the this relay server
     * @throws NullPointerException if relayUID is null
     */
    public ClientSessionBucket(SessionUID relayUID) {
        this(relayUID, HashMultimap.create(), HashMultimap.create(), new HashMap<>(), HashBiMap.create());
    }

    ClientSessionBucket(SessionUID localRelayID, SetMultimap<SessionChannel, SessionUID> ntwrkChannel,
                        SetMultimap<SessionUID, SessionChannel> ntwrkClients, Map<SessionUID, Session> localClients, BiMap<ChannelId, Session> initializedChannels) {
        this.localRelayUID = Objects.requireNonNull(localRelayID);
        this.networkChannelClients = Objects.requireNonNull(ntwrkChannel);
        this.networkClientChannels = Objects.requireNonNull(ntwrkClients);
        this.localClientSessions = Objects.requireNonNull(localClients);
        this.initializedSessions = Objects.requireNonNull(initializedChannels);
    }

    /**
     * Adds a local client session to the bucket.
     *
     * @param clientUID       session UID of the client
     * @param client          {@link Session} reference
     * @param sessionChannels client channels
     * @throws NullPointerException if any given parameter is null
     */
    public void addLocalClientSession(SessionUID clientUID, Session client, SessionChannel... sessionChannels) {
        addLocalClientSession(clientUID, client, Arrays.asList(sessionChannels));
    }

    /**
     * Adds a local client session to the bucket.
     *
     * @param clientUID       session UID of the client
     * @param client          {@link Session} reference
     * @param sessionChannels client channels
     * @throws NullPointerException if any given parameter is null
     */
    public void addLocalClientSession(SessionUID clientUID, Session client, Collection<SessionChannel> sessionChannels) {
        Objects.requireNonNull(clientUID);
        Objects.requireNonNull(client);
        Objects.requireNonNull(sessionChannels);
        try {
            lock.writeLock().lock();
            localClientSessions.put(clientUID, client);

            sessionChannels.forEach(channel -> {
                networkClientChannels.put(clientUID, channel);
                networkChannelClients.put(channel, clientUID);
            });
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Adds a remote client UID to the bucket.
     *
     * @param clientUID       session UID of the client
     * @param sessionChannels client channels
     * @throws NullPointerException if any given parameter is null
     */
    public void addRemoteClient(SessionUID clientUID, Collection<SessionChannel> sessionChannels) {
        Objects.requireNonNull(clientUID);
        Objects.requireNonNull(sessionChannels);
        try {
            lock.writeLock().lock();

            sessionChannels.forEach(channel -> {
                networkClientChannels.put(clientUID, channel);
                networkChannelClients.put(channel, clientUID);
            });
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns an immutable set of the given client's channels.
     *
     * @param clientUID session UID of the client
     * @return client channels or empty collection
     * @throws NullPointerException if clientUID is null
     */
    public Set<SessionChannel> getChannelsFromClientUID(SessionUID clientUID) {
        Objects.requireNonNull(clientUID);
        try {
            lock.readLock().lock();

            return ImmutableSet.copyOf(Optional.ofNullable(networkClientChannels.get(clientUID)).orElse(Set.of()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns an immutable set of the UIDs of the given channel's clients.
     *
     * @param sessionChannel channel name
     * @return channel clients or empty collection
     * @throws NullPointerException if channel is null
     */
    public Set<SessionUID> getClientUIDsFromChannel(SessionChannel sessionChannel) {
        Objects.requireNonNull(sessionChannel);
        try {
            lock.readLock().lock();

            return ImmutableSet.copyOf(Optional.ofNullable(networkChannelClients.get(sessionChannel)).orElse(Set.of()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the {@link Session} reference of a remote system.
     *
     * @param clientUID the session UID of the client session to return
     * @return {@link Session} reference or null if not found
     * @throws NullPointerException if clientUID is null
     */
    public Session getLocalClientSession(SessionUID clientUID) {
        Objects.requireNonNull(clientUID);
        try {
            lock.readLock().lock();

            return localClientSessions.get(clientUID);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @return Returns an immutable map with all known remote clients and
     * corresponding channels
     */
    public Map<SessionUID, Set<SessionChannel>> getRemoteChannels() {
        try {
            lock.readLock().lock();

            return ImmutableMap.copyOf(Multimaps.asMap(networkClientChannels));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @return Returns an immutable set of all local client sessions.
     */
    public Set<Session> getLocalClientSessions() {
        try {
            lock.readLock().lock();

            return ImmutableSet.copyOf(localClientSessions.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @return Returns an immutable set of the client UIDs of all local clients.
     */
    public Set<SessionUID> getLocalClientUIDs() {
        try {
            lock.readLock().lock();

            return ImmutableSet.copyOf(localClientSessions.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @return Returns an immutable set of all remote client UIDs.
     */
    public Set<SessionUID> getRemoteClientUIDs() {
        try {
            lock.readLock().lock();
            return Sets.difference(networkClientChannels.keySet(), localClientSessions.keySet()).immutableCopy();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Aggregates all remote members of the subscribed channels of a client.
     *
     * @param clientUID session UID of the client
     * @return immutable set of remote channel members or empty set
     * @throws NullPointerException if clientUID is null
     */
    public Set<SessionUID> aggregateRemoteChannelMembers(SessionUID clientUID) {
        Objects.requireNonNull(clientUID);
        try {
            lock.readLock().lock();
            Set<SessionUID> remoteClientUIDs = new HashSet<>();

            if (networkClientChannels.containsKey(clientUID)) {
                networkClientChannels.get(clientUID).forEach(channel -> {
                    if (networkChannelClients.containsKey(channel)) {
                        remoteClientUIDs.addAll(networkChannelClients.get(channel));
                    }
                });
            }

            return Sets.difference(remoteClientUIDs, localClientSessions.keySet()).immutableCopy();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Aggregates all local members of the subscribed channels of a client.
     *
     * @param clientUID session UID of the client
     * @return immutable map of local channel members or empty map
     * @throws NullPointerException if clientUID is null
     */
    public Map<SessionUID, Session> aggregateLocalChannelMembers(SessionUID clientUID) {
        Objects.requireNonNull(clientUID);
        try {
            lock.readLock().lock();
            Map<SessionUID, Session> localChannelMembers = new HashMap<>();

            if (networkClientChannels.containsKey(clientUID)) {
                networkClientChannels.get(clientUID).forEach(channel -> {
                    if (networkChannelClients.containsKey(channel)) {
                        networkChannelClients.get(channel).stream().filter(localClientSessions::containsKey)
                                .forEach(channelMember -> localChannelMembers.put(channelMember,
                                        localClientSessions.get(channelMember)));
                    }
                });
            }

            return ImmutableMap.copyOf(localChannelMembers);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Aggregates all members of the subscribed channels of a client.
     *
     * <p>
     * If you want both local and remote members, you should use this method instead
     * of {@link #aggregateLocalChannelMembers(String)} and
     * {@link #aggregateRemoteChannelMembers(String)}.
     * </p>
     *
     * @param clientUID session UID of the client
     * @return pair of channel members, left element is an immutable map of the
     * local client sessions, right element is an immutable set of the
     * remote client UIDs
     * @throws NullPointerException if clientUID is null
     */
    public Pair<Map<SessionUID, Session>, Set<SessionUID>> aggregateChannelMembers(SessionUID clientUID) {
        Objects.requireNonNull(clientUID);
        try {
            lock.readLock().lock();
            Map<SessionUID, Session> localChannelMembers = new HashMap<>();
            Set<SessionUID> remoteChannelMembers = new HashSet<>();

            if (networkClientChannels.containsKey(clientUID)) {
                networkClientChannels.get(clientUID).forEach(channel -> {
                    if (networkChannelClients.containsKey(channel)) {
                        networkChannelClients.get(channel).forEach(channelMember -> {
                            if (localClientSessions.containsKey(channelMember)) {
                                localChannelMembers.put(channelMember, localClientSessions.get(channelMember));
                            } else {
                                remoteChannelMembers.add(channelMember);
                            }
                        });
                    }
                });
            }

            return Pair.of(ImmutableMap.copyOf(localChannelMembers), ImmutableSet.copyOf(remoteChannelMembers));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @return an immutable set of all clients in the network, both remote and local
     * ones.
     */
    public Set<SessionUID> getClientUIDs() {
        try {
            lock.readLock().lock();
            return ImmutableSet.copyOf(networkClientChannels.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Removes a client from the bucket.
     *
     * @param clientUID the client UID to remove
     * @throws NullPointerException if clientUID is null
     */
    public void removeClient(SessionUID clientUID) {
        Objects.requireNonNull(clientUID);
        try {
            lock.writeLock().lock();
            localClientSessions.remove(clientUID);

            if (networkClientChannels.containsKey(clientUID)) {
                networkClientChannels.get(clientUID)
                        .forEach(channel -> networkChannelClients.remove(channel, clientUID));
            }

            networkClientChannels.removeAll(clientUID);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes a collection of clients from the bucket.
     *
     * @param clientUIDs the client UIDs to remove
     * @throws NullPointerException if clientUIDs is null
     */
    public void removeClients(Collection<SessionUID> clientUIDs) {
        Objects.requireNonNull(clientUIDs);
        try {
            lock.writeLock().lock();
            localClientSessions.keySet().removeAll(clientUIDs);
            networkClientChannels.keySet().removeAll(clientUIDs);
            networkChannelClients.values().removeAll(clientUIDs);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes a client session from the bucket of local client sessions.
     *
     * @param clientUID the session UID of the client session to remove
     * @throws NullPointerException if clientUID is null
     */
    public void transferLocalToRemote(SessionUID clientUID) {
        Objects.requireNonNull(clientUID);
        try {
            lock.writeLock().lock();
            localClientSessions.remove(clientUID);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Puts a new client in the bucket of local clients.
     *
     * @param clientUID the session UID of the client session to add
     * @param client    the new client session to add
     * @throws NullPointerException if any given parameter is null
     */
    public void transferRemoteToLocal(SessionUID clientUID, Session client) {
        Objects.requireNonNull(clientUID);
        Objects.requireNonNull(client);
        try {
            lock.writeLock().lock();
            if (networkClientChannels.containsKey(clientUID)) {
                localClientSessions.put(clientUID, client);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * @return an immutable set of all initialized channels.
     */
    public Set<ChannelId> getInitializedChannels() {
        try {
            lock.readLock().lock();
            return ImmutableSet.copyOf(initializedSessions.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Moves a {@link Session} from pending into the initialized bucket.
     *
     * @param session session object
     */
    public void initializeSession(Session session) {
        try {
            lock.writeLock().lock();
            initializedSessions.putIfAbsent(session.getChannelId(), session);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Adds an initialized session to the bucket.
     *
     * @param session session to add
     * @throws NullPointerException if channelId is null
     */
    public void addInitializedSession(Session session) {
        Objects.requireNonNull(session);
        try {
            lock.writeLock().lock();
            initializedSessions.put(session.getChannelId(), session);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes an initialized session from the bucket.
     *
     * @param channelId channel to remove
     * @throws NullPointerException if channelId is null
     */
    public void removeInitializedSession(ChannelId channelId) {
        Objects.requireNonNull(channelId);
        try {
            lock.writeLock().lock();
            initializedSessions.remove(channelId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns a {@link Session} object with the given {@link ChannelId}.
     *
     * @param channelId {@link ChannelId} of the {@link Session}
     * @return {@link Session} or null if not found
     * @throws NullPointerException if channelId is null
     */
    public Session getSession(ChannelId channelId) {
        try {
            lock.readLock().lock();

            return initializedSessions.get(channelId);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(networkChannelClients, networkClientChannels, localClientSessions, localRelayUID);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ClientSessionBucket) {
            ClientSessionBucket b2 = (ClientSessionBucket) o;

            return Objects.equals(networkChannelClients, b2.networkChannelClients)
                    && Objects.equals(networkClientChannels, b2.networkClientChannels)
                    && Objects.equals(localClientSessions, b2.localClientSessions)
                    && Objects.equals(localRelayUID, b2.localRelayUID);
        }

        return false;
    }
}
