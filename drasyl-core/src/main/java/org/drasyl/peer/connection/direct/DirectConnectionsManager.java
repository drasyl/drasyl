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
package org.drasyl.peer.connection.direct;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.IdentityManager;
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.MessengerException;
import org.drasyl.peer.Path;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.WhoisMessage;
import org.drasyl.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.time.Duration.ofSeconds;

/**
 * This class is responsible for establishing and managing direct connections with other drasyl
 * nodes.
 */
public class DirectConnectionsManager implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(DirectConnectionsManager.class);
    private final IdentityManager identityManager;
    private final PeersManager peersManager;
    private final AtomicBoolean opened;
    private final Messenger messenger;
    private final RequestPeerInformationCache requestPeerInformationCache;
    private Set<URI> endpoints;

    public DirectConnectionsManager(IdentityManager identityManager,
                                    PeersManager peersManager,
                                    Messenger messenger) {
        this(identityManager, peersManager, new AtomicBoolean(false), messenger, Set.of(), new RequestPeerInformationCache(1_000, ofSeconds(60)));
    }

    DirectConnectionsManager(IdentityManager identityManager,
                             PeersManager peersManager,
                             AtomicBoolean opened,
                             Messenger messenger,
                             Set<URI> endpoints,
                             RequestPeerInformationCache requestPeerInformationCache) {
        this.identityManager = identityManager;
        this.peersManager = peersManager;
        this.opened = opened;
        this.messenger = messenger;
        this.endpoints = endpoints;
        this.requestPeerInformationCache = requestPeerInformationCache;
    }

    public void open() {
        opened.set(true);
    }

    @Override
    public void close() {
        opened.set(false);
    }

    public void setEndpoints(Set<URI> endpoints) {
        this.endpoints = endpoints;
    }

    /**
     * This method notifies the {@link DirectConnectionsManager} that a direct connection with
     * <code>publicKey</code> occurred.
     *
     * @param publicKey
     */
    public void communicationOccurred(CompressedPublicKey publicKey) {
        if (opened.get()) {
            Pair<PeerInformation, Set<Path>> peer = peersManager.getPeer(publicKey);
            Set<Path> paths = peer.second();
            if (paths.isEmpty()) {
                requestPeerInformation(publicKey);
            }
        }
    }

    private void requestPeerInformation(CompressedPublicKey publicKey) {
        if (requestPeerInformationCache.add(publicKey)) {
            LOG.debug("Request information for Peer '{}'", publicKey);
            try {
                messenger.send(new WhoisMessage(publicKey, identityManager.getPublicKey(), PeerInformation.of(endpoints)));
            }
            catch (MessengerException e) {
                LOG.debug("Unable to request information for Peer '{}': {}", publicKey, e.getMessage());
            }
        }
    }
}