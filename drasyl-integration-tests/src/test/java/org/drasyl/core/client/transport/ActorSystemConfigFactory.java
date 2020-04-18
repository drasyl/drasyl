package org.drasyl.core.client.transport;

import com.google.common.collect.Streams;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Map;
import java.util.stream.Collectors;

public class ActorSystemConfigFactory {


    public static Config createTestActorSystemConfig(Config baseConfig, Map<String, String> params) {
        Map<String, String> baseParams = baseConfig.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().render()));

        baseParams.putAll(Map.of(
                "akka.p2p.relay.host", baseConfig.getString("relay.external_ip"),
                "akka.p2p.relay.port", baseConfig.getString("relay.port"),
                "akka.p2p.relay.channel", baseConfig.getString("relay.default_channel")
        ));

        return ConfigFactory.parseString(Streams.concat(baseParams.entrySet().stream(), params.entrySet().stream())
                .map(entry ->String.format("%s = %s\n", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("\n")));
    }

}
