package org.drasyl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.peer.Endpoint;

import java.time.Duration;
import java.util.Set;

import static java.time.Duration.ofSeconds;

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
                .superPeerEndpoints(Set.of(Endpoint.of("wss://review-monitoring-md6yhe.env.drasyl.org")))
                .serverEnabled(false)
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
            public void onEvent(final Event event) {
                System.out.println(event);
            }
        };

        clients.put(identity.getPublicKey(), node);

        return node;
    }
}