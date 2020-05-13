package org.drasyl.core.client;

import io.netty.channel.EventLoopGroup;
import org.drasyl.core.node.DrasylNodeConfig;
import org.drasyl.core.node.Messenger;
import org.drasyl.core.node.PeersManager;
import org.drasyl.core.node.identity.IdentityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents the link between <code>DrasylNode</code> and the super peer. It is
 * responsible for maintaining the connection to the super peer and updates the data of the super
 * peer in <code>PeersManager</code>.
 */
public class SuperPeerClient implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClient.class);
    private final DrasylNodeConfig config;
    private final EventLoopGroup workerGroup;
    private final IdentityManager identityManager;
    private final Messenger messenger;
    private final PeersManager peersManager;

    public SuperPeerClient(DrasylNodeConfig config,
                           IdentityManager identityManager,
                           PeersManager peersManager,
                           Messenger messenger,
                           EventLoopGroup workerGroup) {
        this.identityManager = identityManager;
        this.messenger = messenger;
        this.peersManager = peersManager;
        this.config = config;
        this.workerGroup = workerGroup;
    }

    public void open() throws SuperPeerClientException {
        // FIXME: implement
    }

    @Override
    public void close() {
        // FIXME: implement
    }
}
