package org.drasyl;

import ch.qos.logback.classic.Level;
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

    public SuperPeerStressTesting(long maxClients, Duration shutdownAfter) {
        clients = CacheBuilder.newBuilder()
                .maximumSize(maxClients)
                .expireAfterWrite(shutdownAfter)
                .removalListener((RemovalListener<CompressedPublicKey, DrasylNode>) notification -> notification.getValue().shutdown())
                .build();
    }

    public static void main(String[] args) throws DrasylException {
        DrasylConfig baseConfig = DrasylConfig.newBuilder()
                .superPeerEndpoints(Set.of(Endpoint.of("wss://review-monitoring-md6yhe.env.drasyl.org")))
                .superPeerPublicKey(null)
                .serverEnabled(false)
                .loglevel(Level.INFO)
                .build();

        SuperPeerStressTesting creator = new SuperPeerStressTesting(100, ofSeconds(300));

        while (true) {
            creator.create(baseConfig).start();
        }
    }

    public DrasylNode create(DrasylConfig baseConfig) throws DrasylException {
        Identity identity = IdentityManager.generateIdentity();

        DrasylConfig config = DrasylConfig.newBuilder(baseConfig)
                .identityPublicKey(identity.getPublicKey())
                .identityPrivateKey(identity.getPrivateKey())
                .identityProofOfWork(identity.getProofOfWork())
                .intraVmDiscoveryEnabled(false)
                .loglevel(Level.TRACE)
                .build();
        DrasylNode node = new DrasylNode(config) {
            @Override
            public void onEvent(Event event) {
                System.out.println(event);
            }
        };

        clients.put(identity.getPublicKey(), node);

        return node;
    }
}