/*
 * Copyright (c) 2020-2021.
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
package org.drasyl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import org.drasyl.annotation.NonNull;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.peer.Endpoint;

import java.time.Duration;

import static java.time.Duration.ofSeconds;

@SuppressWarnings("InfiniteLoopStatement")
public class SuperPeerStressTesting {
    private final Cache<CompressedPublicKey, DrasylNode> clients;

    public SuperPeerStressTesting(final long maxClients, final Duration shutdownAfter) {
        clients = CacheBuilder.newBuilder()
                .maximumSize(maxClients)
                .expireAfterWrite(shutdownAfter)
                .removalListener((RemovalListener<CompressedPublicKey, DrasylNode>) notification -> notification.getValue().shutdown())
                .build();
    }

    public static void main(final String[] args) throws DrasylException {
        final DrasylConfig baseConfig = DrasylConfig.newBuilder()
                .remoteSuperPeerEndpoint(Endpoint.of("udp://review-monitoring-md6yhe.env.drasyl.org"))
                .remoteEnabled(false)
                .build();

        final SuperPeerStressTesting creator = new SuperPeerStressTesting(100, ofSeconds(300));

        while (true) {
            creator.create(baseConfig).start();
        }
    }

    public DrasylNode create(final DrasylConfig baseConfig) throws DrasylException {
        final Identity identity = IdentityManager.generateIdentity();

        final DrasylConfig config = DrasylConfig.newBuilder(baseConfig)
                .identityPublicKey(identity.getPublicKey())
                .identityPrivateKey(identity.getPrivateKey())
                .identityProofOfWork(identity.getProofOfWork())
                .intraVmDiscoveryEnabled(false)
                .build();
        final DrasylNode node = new DrasylNode(config) {
            @Override
            public void onEvent(final @NonNull Event event) {
                System.out.println(event);
            }
        };

        clients.put(identity.getPublicKey(), node);

        return node;
    }
}
