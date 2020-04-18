package city.sane.akka.p2p.transport.direct;

import com.typesafe.config.Config;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;

public class DirectP2PTransportChannelProperties {
    private final int listenPort;

    private final List<String> initialPeers;

    DirectP2PTransportChannelProperties(int listenPort, List<String> initialPeers) {
        this.listenPort = listenPort;
        this.initialPeers = initialPeers;
    }

    public DirectP2PTransportChannelProperties(Config config) {
        this(
                config.getInt("direct.listen-port"),
                config.getStringList("direct.initial-peers")
        );
    }

    public int getListenPort() {
        return listenPort;
    }

    public List<InetSocketAddress> getInitialPeers() {
        return initialPeers.stream()
              .map(addressString -> {
            if (addressString.contains(":")) {
                String[] parts = addressString.split(":");
                return InetSocketAddress.createUnresolved(parts[0], Integer.parseInt(parts[1]));
            } else {
                return InetSocketAddress.createUnresolved(addressString, listenPort);
            }
        }).collect(Collectors.toList());
    }


}
