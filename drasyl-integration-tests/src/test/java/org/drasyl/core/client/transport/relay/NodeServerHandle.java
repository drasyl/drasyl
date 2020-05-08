package org.drasyl.core.client.transport.relay;

import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.Messenger;
import org.drasyl.core.node.PeersManager;
import org.drasyl.core.node.identity.IdentityManager;
import org.drasyl.core.server.NodeServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.drasyl.core.server.NodeServerException;

import java.net.InetSocketAddress;

public class NodeServerHandle {
    private final Config config;
    NodeServer server;

    NodeServerHandle(IdentityManager identityManager,
                     PeersManager peersManager,
                     Messenger messenger, int port) throws DrasylException {
        this(identityManager, peersManager, messenger, InetSocketAddress.createUnresolved("localhost", port));
    }

    NodeServerHandle(IdentityManager identityManager,
                     PeersManager peersManager,
                     Messenger messenger, InetSocketAddress remote) throws DrasylException {
        StringBuilder builder = new StringBuilder();
        ConfigFactory.load("application.conf").entrySet().forEach(entry -> {
            builder.append(String.format("%s = %s\n", entry.getKey(), entry.getValue().render()));// ConfigRenderOptions.defaults().setJson(true)
        });
        builder.append(String.format("relay.port = %d\n", remote.getPort()));
        builder.append(String.format("relay.external_ip = %s\n", remote.getHostString()));


        this.config = ConfigFactory.parseString(builder.toString());

        // FIXME: Don't do this, use some shared threads!
        server = new NodeServer(identityManager, messenger, peersManager, this.config, new NioEventLoopGroup(), new NioEventLoopGroup(1));

    }

    void start() throws NodeServerException {
        server.open();
    }

    void shutdown() {
        server.close();
    }

    public Config getConfig() {
        return config;
    }
}
