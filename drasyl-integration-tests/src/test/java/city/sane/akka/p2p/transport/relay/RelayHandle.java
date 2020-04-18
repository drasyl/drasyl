package city.sane.akka.p2p.transport.relay;

import city.sane.relay.server.RelayServer;
import city.sane.relay.server.RelayServerException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.net.InetSocketAddress;
import java.net.URISyntaxException;

public class RelayHandle {
    private final Config config;
    RelayServer server;

    RelayHandle(int port) throws RelayServerException, URISyntaxException {
        this(InetSocketAddress.createUnresolved("localhost", port));
    }

    RelayHandle(InetSocketAddress remote) throws RelayServerException, URISyntaxException {
        StringBuilder builder = new StringBuilder();
        ConfigFactory.load("application.conf").entrySet().forEach(entry -> {
            builder.append(String.format("%s = %s\n", entry.getKey(), entry.getValue().render()));// ConfigRenderOptions.defaults().setJson(true)
        });
        builder.append(String.format("relay.port = %d\n", remote.getPort()));
        builder.append(String.format("relay.external_ip = %s\n", remote.getHostString()));


        this.config = ConfigFactory.parseString(builder.toString());

        server = new RelayServer(this.config);

    }

    void start() throws RelayServerException {
        server.open();
        server.awaitOpen();
    }

    void shutdown() throws RelayServerException {
        server.close();
        server.awaitClose();
    }

    public Config getConfig() {
        return config;
    }
}
