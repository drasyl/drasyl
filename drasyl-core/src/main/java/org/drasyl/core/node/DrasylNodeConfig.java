/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.core.node;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.drasyl.core.common.tools.NetworkTool;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DrasylNodeConfig {
    //======================================== Config Paths ========================================
    private static final String DRASYL_IDENTITY_PATH = "drasyl.identity.path";
    private static final String DRASYL_USER_AGENT = "drasyl.user-agent";
    private static final String SERVER_ENABLED = "drasyl.server.enabled";
    private static final String SERVER_BIND_HOST = "drasyl.server.bind-host";
    private static final String SERVER_BIND_PORT = "drasyl.server.bind-port";
    private static final String DRASYL_SERVER_ENDPOINTS = "drasyl.server.endpoints";
    private static final String SERVER_IDLE_RETRIES = "drasyl.server.idle.retries";
    private static final String SERVER_IDLE_TIMEOUT = "drasyl.server.idle.timeout";
    private static final String SERVER_FLUSH_BUFFER_SIZE = "drasyl.server.flush-buffer-size";
    private static final String SERVER_SSL_ENABLED = "drasyl.server.ssl.enabled";
    private static final String SERVER_SSL_PROTOCOLS = "drasyl.server.ssl.protocols";
    private static final String SERVER_MAX_HANDSHAKE_TIMEOUT = "drasyl.server.max-handshake-timeout";
    private static final String SERVER_CHANNEL_INITIALIZER = "drasyl.server.channel-initializer";
    private static final String SERVER_MAX_CONTENT_LENGTH = "drasyl.server.max-content-length";
    private final Config config;
    //======================================= Config Values ========================================
    private final Path identityPath;
    private final String userAgent;
    private final String serverBindHost;
    private final boolean serverEnabled;
    private final int serverBindPort;
    private final int serverIdleRetries;
    private final Duration serverIdleTimeout;
    private final int serverFlushBufferSize;
    private final boolean serverSSLEnabled;
    private final List<String> serverSSLProtocols;
    private final Duration serverHandshakeTimeout;
    private final Set<String> serverEndpoints;
    private final String serverChannelInitializer;
    private final int serverMaxContentLength;

    /**
     * Creates a new config for a drasyl node.
     *
     * @param config config to be loaded
     * @throws ConfigException if the given config is invalid
     */
    public DrasylNodeConfig(Config config) {
        this.config = config;
        config.checkValid(ConfigFactory.defaultReference(), "drasyl");

        // init
        this.userAgent = config.getString(DRASYL_USER_AGENT);

        var idPath = config.getString(DRASYL_IDENTITY_PATH);
        this.identityPath = Paths.get(idPath);

        // Init server config
        this.serverEnabled = config.getBoolean(SERVER_ENABLED);
        this.serverBindHost = config.getString(SERVER_BIND_HOST);
        this.serverBindPort = config.getInt(SERVER_BIND_PORT);
        this.serverIdleRetries = config.getInt(SERVER_IDLE_RETRIES);
        this.serverIdleTimeout = config.getDuration(SERVER_IDLE_TIMEOUT);
        this.serverFlushBufferSize = config.getInt(SERVER_FLUSH_BUFFER_SIZE);
        this.serverHandshakeTimeout = config.getDuration(SERVER_MAX_HANDSHAKE_TIMEOUT);
        this.serverChannelInitializer = config.getString(SERVER_CHANNEL_INITIALIZER);
        this.serverMaxContentLength = (int) Math.min(config.getMemorySize(SERVER_MAX_CONTENT_LENGTH).toBytes(), Integer.MAX_VALUE);

        this.serverSSLEnabled = config.getBoolean(SERVER_SSL_ENABLED);
        this.serverSSLProtocols = config.getStringList(SERVER_SSL_PROTOCOLS);

        if (!config.getStringList(DRASYL_SERVER_ENDPOINTS).isEmpty()) {
            this.serverEndpoints = new HashSet<>(config.getStringList(DRASYL_SERVER_ENDPOINTS));
        }
        else {
            String scheme = serverSSLEnabled ? "wss" : "ws";
            this.serverEndpoints = NetworkTool.getAddresses().stream().map(a -> scheme + "://" + a + ":" + serverBindPort).collect(Collectors.toSet());
        }
    }

    @SuppressWarnings({ "java:S107" })
    DrasylNodeConfig(Config config,
                     Path identityPath,
                     String userAgent,
                     String serverBindHost,
                     boolean serverEnabled,
                     int serverBindPort,
                     int serverIdleRetries,
                     Duration serverIdleTimeout,
                     int serverFlushBufferSize,
                     boolean serverSSLEnabled,
                     List<String> serverSSLProtocols,
                     Duration serverHandshakeTimeout,
                     Set<String> serverEndpoints,
                     String serverChannelInitializer,
                     int serverMaxContentLength) {
        this.config = config;
        this.identityPath = identityPath;
        this.userAgent = userAgent;
        this.serverBindHost = serverBindHost;
        this.serverEnabled = serverEnabled;
        this.serverBindPort = serverBindPort;
        this.serverIdleRetries = serverIdleRetries;
        this.serverIdleTimeout = serverIdleTimeout;
        this.serverFlushBufferSize = serverFlushBufferSize;
        this.serverSSLEnabled = serverSSLEnabled;
        this.serverSSLProtocols = serverSSLProtocols;
        this.serverHandshakeTimeout = serverHandshakeTimeout;
        this.serverEndpoints = serverEndpoints;
        this.serverChannelInitializer = serverChannelInitializer;
        this.serverMaxContentLength = serverMaxContentLength;
    }

    public String getServerBindHost() {
        return serverBindHost;
    }

    public int getServerBindPort() {
        return serverBindPort;
    }

    public String getUserAgent() {
        return this.userAgent;
    }

    public Config getConfig() {
        return this.config;
    }

    public Path getIdentityPath() {
        return identityPath;
    }

    @Override
    public String toString() {
        return "NodeServerConfig [config=" + config.getConfig("drasyl") + "]";
    }

    public boolean isServerEnabled() {
        return serverEnabled;
    }

    public boolean getServerSSLEnabled() {
        return serverSSLEnabled;
    }

    public int getServerIdleRetries() {
        return serverIdleRetries;
    }

    public Duration getServerIdleTimeout() {
        return serverIdleTimeout;
    }

    public int getServerFlushBufferSize() {
        return serverFlushBufferSize;
    }

    public String[] getServerSSLProtocols() {
        return serverSSLProtocols.toArray(new String[0]);
    }

    public Duration getServerHandshakeTimeout() {
        return serverHandshakeTimeout;
    }

    public Set<String> getServerEndpoints() {
        return serverEndpoints;
    }

    public String getServerChannelInitializer() {
        return serverChannelInitializer;
    }

    public int getServerMaxContentLength() {
        return serverMaxContentLength;
    }
}
