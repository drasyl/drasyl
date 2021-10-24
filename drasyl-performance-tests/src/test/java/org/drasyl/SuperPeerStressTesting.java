/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import org.drasyl.annotation.NonNull;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.PeerEndpoint;
import org.drasyl.node.event.Event;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

import static java.time.Duration.ofSeconds;

@SuppressWarnings("InfiniteLoopStatement")
public class SuperPeerStressTesting extends AbstractBenchmark {
    private final Cache<IdentityPublicKey, DrasylNode> clients;

    public SuperPeerStressTesting(final long maxClients, final Duration shutdownAfter) {
        clients = CacheBuilder.newBuilder()
                .maximumSize(maxClients)
                .expireAfterWrite(shutdownAfter)
                .removalListener((RemovalListener<IdentityPublicKey, DrasylNode>) notification -> notification.getValue().shutdown())
                .build();
    }

    public static void main(final String[] args) throws DrasylException, IOException {
        final DrasylConfig baseConfig = DrasylConfig.newBuilder()
                .remoteSuperPeerEndpoints(Set.of(PeerEndpoint.of("udp://review-monitoring-md6yhe.env.drasyl.org")))
                .remoteEnabled(false)
                .build();

        final SuperPeerStressTesting creator = new SuperPeerStressTesting(100, ofSeconds(300));

        while (true) {
            creator.create(baseConfig).start();
        }
    }

    public DrasylNode create(final DrasylConfig baseConfig) throws DrasylException, IOException {
        final Identity identity = Identity.generateIdentity();

        final DrasylConfig config = DrasylConfig.newBuilder(baseConfig)
                .identity(identity)
                .intraVmDiscoveryEnabled(false)
                .build();
        final DrasylNode node = new DrasylNode(config) {
            @Override
            public void onEvent(final @NonNull Event event) {
                System.out.println(event);
            }
        };

        clients.put(identity.getIdentityPublicKey(), node);

        return node;
    }
}
