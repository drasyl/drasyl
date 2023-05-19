/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.sdo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class NetworkConfig {
    public final Config config;

    public NetworkConfig(final Config config) {
        this.config = Objects.requireNonNull(config);
    }

    public static NetworkConfig parseString(final String s) {
        return new NetworkConfig(ConfigFactory.parseString(s).resolve().getObject("network").atPath("network"));
    }

    public static NetworkConfig parseFile(final File file) {
        return new NetworkConfig(ConfigFactory.parseFile(file).resolve().getObject("network").atPath("network"));
    }

    @Override
    public String toString() {
        return config.root().render(ConfigRenderOptions.concise());
    }

    public Map<String, Object> getNode(final DrasylAddress address) {
        final ConfigList list = config.getList("network.nodes");
        for (ConfigValue value : list) {
            final Map<String, Object> attributes = (Map<String, Object>) value.unwrapped();
            final String nodeAddress = (String) attributes.get("address");
            final IdentityPublicKey nodeKey = IdentityPublicKey.of(nodeAddress);
            if (address.equals(nodeKey)) {
                return attributes;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public boolean isNode(final DrasylAddress address) {
        return getNode(address) != null;
    }

    public List<Map<String, Object>> getConnectionsFor(final DrasylAddress address) {
        final List<Map<String, Object>> connections = new ArrayList<>();
        final ConfigList list = config.getList("network.connections");
        for (ConfigValue value : list) {
            final Map<String, Object> attributes = (Map<String, Object>) value.unwrapped();
            final String fromAddress = (String) attributes.get("from");
            final IdentityPublicKey fromKey = IdentityPublicKey.of(fromAddress);
            final String toAddress = (String) attributes.get("to");
            final IdentityPublicKey toKey = IdentityPublicKey.of(toAddress);
            if (address.equals(toKey) || address.equals(fromKey)) {
                connections.add(attributes);
            }
        }
        return connections;
    }
}
