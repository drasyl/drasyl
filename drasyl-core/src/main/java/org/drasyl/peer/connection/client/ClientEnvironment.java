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
package org.drasyl.peer.connection.client;

import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;

import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * This class encapsulates all information needed by a {@link ClientChannelInitializer}.
 */
public class ClientEnvironment {
    private final DrasylConfig config;
    private final Identity identity;
    private final URI endpoint;
    private final Messenger messenger;

    private final PeerChannelGroup channelGroup;
    private final PeersManager peersManager;
    private final Consumer<Event> eventConsumer;
    private final CompressedPublicKey serverPublicKey;
    private final boolean joinAsChildren;
    private final short idleRetries;
    private final Duration idleTimeout;
    private final Duration handshakeTimeout;
    private final Consumer<CompressedPublicKey> peerCommunicationConsumer;

    public ClientEnvironment(DrasylConfig config,
                             Identity identity,
                             URI endpoint,
                             Messenger messenger,
                             PeerChannelGroup channelGroup,
                             PeersManager peersManager,
                             Consumer<Event> eventConsumer,
                             boolean joinAsChildren,
                             CompressedPublicKey serverPublicKey,
                             short idleRetries,
                             Duration idleTimeout,
                             Duration handshakeTimeout,
                             Consumer<CompressedPublicKey> peerCommunicationConsumer) {
        this.config = config;
        this.identity = identity;
        this.endpoint = endpoint;
        this.messenger = messenger;
        this.channelGroup = channelGroup;
        this.peersManager = peersManager;
        this.eventConsumer = eventConsumer;
        this.joinAsChildren = joinAsChildren;
        this.serverPublicKey = serverPublicKey;
        this.idleRetries = idleRetries;
        this.idleTimeout = idleTimeout;
        this.handshakeTimeout = handshakeTimeout;
        this.peerCommunicationConsumer = peerCommunicationConsumer;
    }

    public DrasylConfig getConfig() {
        return config;
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public Identity getIdentity() {
        return identity;
    }

    public Messenger getMessenger() {
        return messenger;
    }

    public PeersManager getPeersManager() {
        return peersManager;
    }

    public Consumer<Event> getEventConsumer() {
        return eventConsumer;
    }

    public boolean joinAsChildren() {
        return joinAsChildren;
    }

    public Duration getHandshakeTimeout() {
        return handshakeTimeout;
    }

    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    public short getIdleRetries() {
        return idleRetries;
    }

    public CompressedPublicKey getServerPublicKey() {
        return serverPublicKey;
    }

    public Consumer<CompressedPublicKey> getPeerCommunicationConsumer() {
        return peerCommunicationConsumer;
    }

    public PeerChannelGroup getChannelGroup() {
        return channelGroup;
    }
}
