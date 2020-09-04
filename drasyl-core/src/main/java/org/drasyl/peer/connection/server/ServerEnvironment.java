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
package org.drasyl.peer.connection.server;

import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;

import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * This class encapsulates all information needed by a {@link ServerChannelInitializer}.
 */
public class ServerEnvironment {
    private final DrasylConfig config;
    private final Identity identity;
    private final PeersManager peersManager;
    private final BooleanSupplier acceptedNewConnectionsSupplier;
    private final Messenger messenger;
    private final Set<Endpoint> endpoints;
    private final PeerChannelGroup channelGroup;

    public ServerEnvironment(DrasylConfig config,
                             Identity identity,
                             PeersManager peersManager,
                             Messenger messenger,
                             Set<Endpoint> endpoints,
                             PeerChannelGroup channelGroup,
                             BooleanSupplier acceptedNewConnectionsSupplier) {
        this.config = config;
        this.identity = identity;
        this.peersManager = peersManager;
        this.messenger = messenger;
        this.endpoints = endpoints;
        this.channelGroup = channelGroup;
        this.acceptedNewConnectionsSupplier = acceptedNewConnectionsSupplier;
    }

    public Identity getIdentity() {
        return identity;
    }

    public PeersManager getPeersManager() {
        return peersManager;
    }

    public BooleanSupplier getAcceptNewConnectionsSupplier() {
        return acceptedNewConnectionsSupplier;
    }

    public boolean isDisconnectedFromSuperPeer() {
        return !config.isSuperPeerEnabled() || peersManager.getSuperPeerKey() != null;
    }

    public DrasylConfig getConfig() {
        return config;
    }

    public Messenger getMessenger() {
        return messenger;
    }

    public Set<Endpoint> getEndpoints() {
        return endpoints;
    }

    public PeerChannelGroup getChannelGroup() {
        return channelGroup;
    }
}