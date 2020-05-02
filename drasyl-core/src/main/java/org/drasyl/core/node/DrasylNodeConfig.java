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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DrasylNodeConfig {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylNodeConfig.class);
    //======================================== Config Paths ========================================
    private static final String DRASYL_IDENTITY_PATH = "drasyl.identity.path";
    private static final String DRASYL_USER_AGENT = "drasyl.user-agent";
    private static final String DRASYL_ENTRY_POINTS = "drasyl.entry_points";
    private static final String SERVER_BIND_HOST = "drasyl.server.bind-host";
    private static final String SERVER_BIND_PORT = "drasyl.server.bind-port";
    private static final String SERVER_IDLE_RETRIES = "drasyl.server.idle.retries";
    private static final String SERVER_IDLE_TIMEOUT = "drasyl.server.idle.timeout";
    private static final String SERVER_FLUSH_BUFFER_SIZE = "drasyl.server.flush_buffer_size";
    private static final String SERVER_SSL_ENABLED = "drasyl.server.ssl.enabled";
    private static final String SERVER_SSL_PROTOCOLS = "drasyl.server.ssl.protocols";
    private static final String SERVER_MAX_HANDSHAKE_TIMEOUT = "drasyl.server.max_handshake_timeout";
    private static final String SERVER_CHANNEL_INITIALIZER = "drasyl.server.channel_initializer";
    private static final String SERVER_MAX_CONTENT_LENGTH = "drasyl.server.max_content_length";
    private static final String SERVER_ENTRY_POINT = "drasyl.server.entry_point";
    private final Config config;
    //======================================= Config Values ========================================
    private final Path identityPath;
    private final String userAgent;
    private final String serverBindHost;
    private final int serverBindPort;
    private final int serverIdleRetries;
    private final Duration serverIdleTimeout;
    private final int serverFlushBufferSize;
    private final boolean serverSSLEnabled;
    private final List<String> serverSSLProtocols;
    private final Duration serverHandshakeTimeout;
    private final Set<URI> entryPoints;
    private final String serverChannelInitializer;
    private final int serverMaxContentLength;
    private final URI serverEntryPoint;

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
        if (idPath.equals("")) {
            this.identityPath = Paths.get("drasylNodeIdentity.json");
        }
        else {
            this.identityPath = Paths.get(idPath);
        }

        // Init server config
        this.serverBindHost = config.getString(SERVER_BIND_HOST);
        this.serverBindPort = config.getInt(SERVER_BIND_PORT);
        this.serverIdleRetries = config.getInt(SERVER_IDLE_RETRIES);
        this.serverIdleTimeout = config.getDuration(SERVER_IDLE_TIMEOUT);
        this.serverFlushBufferSize = config.getInt(SERVER_FLUSH_BUFFER_SIZE);
        this.serverHandshakeTimeout = config.getDuration(SERVER_MAX_HANDSHAKE_TIMEOUT);
        this.entryPoints = new HashSet<>();
        this.serverChannelInitializer = config.getString(SERVER_CHANNEL_INITIALIZER);
        this.serverMaxContentLength = (int) Math.min(config.getMemorySize(SERVER_MAX_CONTENT_LENGTH).toBytes(), Integer.MAX_VALUE);

        this.serverSSLEnabled = config.getBoolean(SERVER_SSL_ENABLED);
        this.serverSSLProtocols = config.getStringList(SERVER_SSL_PROTOCOLS);
        String protocol = serverSSLEnabled ? "wss://" : "ws://";

        if (config.hasPath(DRASYL_ENTRY_POINTS)) {
            config.getStringList(DRASYL_ENTRY_POINTS).forEach(uri -> this.entryPoints.add(URI.create(uri)));
        }
        else {
            this.entryPoints.add(URI.create(protocol + initExternalIP() + ":22527/"));
        }

        if (config.hasPath(SERVER_ENTRY_POINT)) {
            this.serverEntryPoint = URI.create(config.getString(SERVER_ENTRY_POINT));
        }
        else {
            this.serverEntryPoint = URI.create(protocol + initExternalIP() + ":22527/");
        }
    }

    @SuppressWarnings({ "java:S107" })
    DrasylNodeConfig(Config config,
                     Path identityPath,
                     String userAgent,
                     String serverBindHost,
                     int serverBindPort,
                     int serverIdleRetries,
                     Duration serverIdleTimeout,
                     int serverFlushBufferSize,
                     boolean serverSSLEnabled,
                     List<String> serverSSLProtocols,
                     Duration serverHandshakeTimeout,
                     Set<URI> entryPoints,
                     String serverChannelInitializer,
                     int serverMaxContentLength, URI serverEntryPoint) {
        this.config = config;
        this.identityPath = identityPath;
        this.userAgent = userAgent;
        this.serverBindHost = serverBindHost;
        this.serverBindPort = serverBindPort;
        this.serverIdleRetries = serverIdleRetries;
        this.serverIdleTimeout = serverIdleTimeout;
        this.serverFlushBufferSize = serverFlushBufferSize;
        this.serverSSLEnabled = serverSSLEnabled;
        this.serverSSLProtocols = serverSSLProtocols;
        this.serverHandshakeTimeout = serverHandshakeTimeout;
        this.entryPoints = entryPoints;
        this.serverChannelInitializer = serverChannelInitializer;
        this.serverMaxContentLength = serverMaxContentLength;
        this.serverEntryPoint = serverEntryPoint;
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

    private String initExternalIP() {
        String extIP = "localhost";
        try {
            extIP = NetworkTool.getExternalIPAddress();
        }
        catch (IOException e) {
            LOG.error("External IP request unsuccessful: ", e);
        }

        return extIP;
    }

    @Override
    public String toString() {
        return "NodeServerConfig [config=" + config.getConfig("drasyl") + "]";
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

    public Set<URI> getEntryPoints() {
        return entryPoints;
    }

    public String getServerChannelInitializer() {
        return serverChannelInitializer;
    }

    public int getServerMaxContentLength() {
        return serverMaxContentLength;
    }

    public URI getServerEntryPoint() {
        return serverEntryPoint;
    }
}
