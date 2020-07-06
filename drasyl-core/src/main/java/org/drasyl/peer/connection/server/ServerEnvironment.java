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
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;

import java.net.URI;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This class encapsulates all information needed by a {@link ServerChannelInitializer}.
 */
public class ServerEnvironment {
    private final DrasylConfig config;
    private final Supplier<Identity> identitySupplier;
    private final PeersManager peersManager;
    private final BooleanSupplier acceptedNewConnectionsSupplier;
    private final Consumer<CompressedPublicKey> peerCommunicationConsumer;
    private final Messenger messenger;
    private final Supplier<Set<URI>> endpointsSupplier;
    private final ServerChannelGroup channelGroup;

    public ServerEnvironment(DrasylConfig config,
                             Supplier<Identity> identitySupplier,
                             PeersManager peersManager,
                             Messenger messenger,
                             Supplier<Set<URI>> endpointsSupplier,
                             ServerChannelGroup channelGroup,
                             BooleanSupplier acceptedNewConnectionsSupplier,
                             Consumer<CompressedPublicKey> peerCommunicationConsumer) {
        this.config = config;
        this.identitySupplier = identitySupplier;
        this.peersManager = peersManager;
        this.messenger = messenger;
        this.endpointsSupplier = endpointsSupplier;
        this.channelGroup = channelGroup;
        this.acceptedNewConnectionsSupplier = acceptedNewConnectionsSupplier;
        this.peerCommunicationConsumer = peerCommunicationConsumer;
    }

    public Identity getIdentity() {
        return identitySupplier.get();
    }

    public PeersManager getPeersManager() {
        return peersManager;
    }

    public BooleanSupplier getAcceptNewConnectionsSupplier() {
        return acceptedNewConnectionsSupplier;
    }

    public DrasylConfig getConfig() {
        return config;
    }

    public Messenger getMessenger() {
        return messenger;
    }

    public Set<URI> getEndpoints() {
        return endpointsSupplier.get();
    }

    public ServerChannelGroup getChannelGroup() {
        return channelGroup;
    }

    public Consumer<CompressedPublicKey> getPeerCommunicationConsumer() {
        return peerCommunicationConsumer;
    }
}
