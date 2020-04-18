package org.drasyl.core.client.transport.direct;

import com.typesafe.config.Config;

import java.nio.file.Path;

public class UnixSocketTransportChannelProperties {
    private final String sharedDir;

    UnixSocketTransportChannelProperties(String sharedDir) {
        this.sharedDir = sharedDir;

    }

    public UnixSocketTransportChannelProperties(Config config) {
        this(
                config.getString("hostsystem.shared-dir")
        );
    }

    public String getSharedDir() {
        return Path.of(System.getProperty("java.io.tmpdir"), sharedDir).toString();
    }

}
