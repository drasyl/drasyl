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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DrasylNodeConfig {
    //======================================== Config Paths ========================================
    private static final String IDENTITY_PUBLIC_KEY = "drasyl.identity.public-key";
    private static final String IDENTITY_PRIVATE_KEY = "drasyl.identity.private-key";
    private static final String IDENTITY_PATH = "drasyl.identity.path";
    private static final String USER_AGENT = "drasyl.user-agent";
    private static final String MAX_CONTENT_LENGTH = "drasyl.max-content-length";
    private static final String FLUSH_BUFFER_SIZE = "drasyl.flush-buffer-size";
    private static final String SERVER_ENABLED = "drasyl.server.enabled";
    private static final String SERVER_BIND_HOST = "drasyl.server.bind-host";
    private static final String SERVER_BIND_PORT = "drasyl.server.bind-port";
    private static final String SERVER_ENDPOINTS = "drasyl.server.endpoints";
    private static final String SERVER_IDLE_RETRIES = "drasyl.server.idle.retries";
    private static final String SERVER_IDLE_TIMEOUT = "drasyl.server.idle.timeout";
    private static final String SERVER_SSL_ENABLED = "drasyl.server.ssl.enabled";
    private static final String SERVER_SSL_PROTOCOLS = "drasyl.server.ssl.protocols";
    private static final String SERVER_MAX_HANDSHAKE_TIMEOUT = "drasyl.server.max-handshake-timeout";
    private static final String SERVER_CHANNEL_INITIALIZER = "drasyl.server.channel-initializer";
    private static final String SUPER_PEER_ENDPOINTS = "drasyl.super-peer.endpoints";
    private static final String SUPER_PEER_PUBLIC_KEY = "drasyl.super-peer.public-key";
    private static final String SUPER_PEER_RETRY_DELAYS = "drasyl.super-peer.retry-delays";
    //======================================= Config Values ========================================
    private final String identityPublicKey;
    private final String identityPrivateKey;
    private final Path identityPath;
    private final String userAgent;
    private final String serverBindHost;
    private final boolean serverEnabled;
    private final int serverBindPort;
    private final int serverIdleRetries;
    private final Duration serverIdleTimeout;
    private final int flushBufferSize;
    private final boolean serverSSLEnabled;
    private final List<String> serverSSLProtocols;
    private final Duration serverHandshakeTimeout;
    private final Set<String> serverEndpoints;
    private final String serverChannelInitializer;
    private final int maxContentLength;
    private final Set<String> superPeerEndpoints;
    private final String superPeerPublicKey;
    private final List<Duration> superPeerRetryDelays;

    /**
     * Creates a new config for a drasyl node.
     *
     * @param config config to be loaded
     * @throws ConfigException if the given config is invalid
     */
    public DrasylNodeConfig(Config config) {
        config.checkValid(ConfigFactory.defaultReference(), "drasyl");

        // init
        this.userAgent = config.getString(USER_AGENT);

        // init identity config
        this.identityPublicKey = config.getString(IDENTITY_PUBLIC_KEY);
        this.identityPrivateKey = config.getString(IDENTITY_PRIVATE_KEY);
        this.identityPath = Paths.get(config.getString(IDENTITY_PATH));

        // Init server config
        this.serverEnabled = config.getBoolean(SERVER_ENABLED);
        this.serverBindHost = config.getString(SERVER_BIND_HOST);
        this.serverBindPort = config.getInt(SERVER_BIND_PORT);
        this.serverIdleRetries = config.getInt(SERVER_IDLE_RETRIES);
        this.serverIdleTimeout = config.getDuration(SERVER_IDLE_TIMEOUT);
        this.flushBufferSize = config.getInt(FLUSH_BUFFER_SIZE);
        this.serverHandshakeTimeout = config.getDuration(SERVER_MAX_HANDSHAKE_TIMEOUT);
        this.serverChannelInitializer = config.getString(SERVER_CHANNEL_INITIALIZER);
        this.maxContentLength = (int) Math.min(config.getMemorySize(MAX_CONTENT_LENGTH).toBytes(), Integer.MAX_VALUE);

        this.serverSSLEnabled = config.getBoolean(SERVER_SSL_ENABLED);
        this.serverSSLProtocols = config.getStringList(SERVER_SSL_PROTOCOLS);

        if (!config.getStringList(SERVER_ENDPOINTS).isEmpty()) {
            this.serverEndpoints = new HashSet<>(config.getStringList(SERVER_ENDPOINTS));
        }
        else {
            String scheme = serverSSLEnabled ? "wss" : "ws";
            this.serverEndpoints = NetworkTool.getAddresses().stream().map(a -> scheme + "://" + a + ":" + serverBindPort).collect(Collectors.toSet());
        }

        // Init super peer config
        this.superPeerEndpoints = new HashSet<>(config.getStringList(SUPER_PEER_ENDPOINTS));
        this.superPeerPublicKey = config.getString(SUPER_PEER_PUBLIC_KEY);
        this.superPeerRetryDelays = config.getDurationList(SUPER_PEER_RETRY_DELAYS);
    }

    @SuppressWarnings({ "java:S107" })
    DrasylNodeConfig(String identityPublicKey,
                     String identityPrivateKey,
                     Path identityPath,
                     String userAgent,
                     String serverBindHost,
                     boolean serverEnabled,
                     int serverBindPort,
                     int serverIdleRetries,
                     Duration serverIdleTimeout,
                     int flushBufferSize,
                     boolean serverSSLEnabled,
                     List<String> serverSSLProtocols,
                     Duration serverHandshakeTimeout,
                     Set<String> serverEndpoints,
                     String serverChannelInitializer,
                     int maxContentLength,
                     Set<String> superPeerEndpoints,
                     String superPeerPublicKey,
                     List<Duration> superPeerRetryDelays) {
        this.identityPublicKey = identityPublicKey;
        this.identityPrivateKey = identityPrivateKey;
        this.identityPath = identityPath;
        this.userAgent = userAgent;
        this.serverBindHost = serverBindHost;
        this.serverEnabled = serverEnabled;
        this.serverBindPort = serverBindPort;
        this.serverIdleRetries = serverIdleRetries;
        this.serverIdleTimeout = serverIdleTimeout;
        this.flushBufferSize = flushBufferSize;
        this.serverSSLEnabled = serverSSLEnabled;
        this.serverSSLProtocols = serverSSLProtocols;
        this.serverHandshakeTimeout = serverHandshakeTimeout;
        this.serverEndpoints = serverEndpoints;
        this.serverChannelInitializer = serverChannelInitializer;
        this.maxContentLength = maxContentLength;
        this.superPeerEndpoints = superPeerEndpoints;
        this.superPeerPublicKey = superPeerPublicKey;
        this.superPeerRetryDelays = superPeerRetryDelays;
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

    public String getIdentityPublicKey() {
        return identityPublicKey;
    }

    public String getIdentityPrivateKey() {
        return identityPrivateKey;
    }

    public Path getIdentityPath() {
        return identityPath;
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

    public int getFlushBufferSize() {
        return flushBufferSize;
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

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public Set<String> getSuperPeerEndpoints() {
        return superPeerEndpoints;
    }

    public boolean hasSuperPeer() {
        return !getSuperPeerEndpoints().isEmpty();
    }

    public String getSuperPeerPublicKey() {
        return superPeerPublicKey;
    }

    public List<Duration> getSuperPeerRetryDelays() {
        return superPeerRetryDelays;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identityPublicKey, identityPrivateKey, identityPath, userAgent, serverBindHost, serverEnabled, serverBindPort, serverIdleRetries, serverIdleTimeout, flushBufferSize, serverSSLEnabled, serverSSLProtocols, serverHandshakeTimeout, serverEndpoints, serverChannelInitializer, maxContentLength, superPeerEndpoints, superPeerPublicKey, superPeerRetryDelays);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DrasylNodeConfig that = (DrasylNodeConfig) o;
        return serverEnabled == that.serverEnabled &&
                serverBindPort == that.serverBindPort &&
                serverIdleRetries == that.serverIdleRetries &&
                flushBufferSize == that.flushBufferSize &&
                serverSSLEnabled == that.serverSSLEnabled &&
                maxContentLength == that.maxContentLength &&
                Objects.equals(identityPublicKey, that.identityPublicKey) &&
                Objects.equals(identityPrivateKey, that.identityPrivateKey) &&
                Objects.equals(identityPath, that.identityPath) &&
                Objects.equals(userAgent, that.userAgent) &&
                Objects.equals(serverBindHost, that.serverBindHost) &&
                Objects.equals(serverIdleTimeout, that.serverIdleTimeout) &&
                Objects.equals(serverSSLProtocols, that.serverSSLProtocols) &&
                Objects.equals(serverHandshakeTimeout, that.serverHandshakeTimeout) &&
                Objects.equals(serverEndpoints, that.serverEndpoints) &&
                Objects.equals(serverChannelInitializer, that.serverChannelInitializer) &&
                Objects.equals(superPeerEndpoints, that.superPeerEndpoints) &&
                Objects.equals(superPeerPublicKey, that.superPeerPublicKey) &&
                Objects.equals(superPeerRetryDelays, that.superPeerRetryDelays);
    }

    @Override
    public String toString() {
        return "DrasylNodeConfig{" +
                "identityPublicKey='" + identityPublicKey + '\'' +
                ", identityPrivateKey='" + maskSecret(identityPrivateKey) + '\'' +
                ", identityPath=" + identityPath +
                ", userAgent='" + userAgent + '\'' +
                ", serverBindHost='" + serverBindHost + '\'' +
                ", serverEnabled=" + serverEnabled +
                ", serverBindPort=" + serverBindPort +
                ", serverIdleRetries=" + serverIdleRetries +
                ", serverIdleTimeout=" + serverIdleTimeout +
                ", flushBufferSize=" + flushBufferSize +
                ", serverSSLEnabled=" + serverSSLEnabled +
                ", serverSSLProtocols=" + serverSSLProtocols +
                ", serverHandshakeTimeout=" + serverHandshakeTimeout +
                ", serverEndpoints=" + serverEndpoints +
                ", serverChannelInitializer='" + serverChannelInitializer + '\'' +
                ", maxContentLength=" + maxContentLength +
                ", superPeerEndpoints=" + superPeerEndpoints +
                ", superPeerPublicKey='" + superPeerPublicKey + '\'' +
                ", superPeerRetryDelays=" + superPeerRetryDelays +
                '}';
    }

    /**
     * This method replaces each character in the return of toString() with a asterisk. Can be used
     * to mask secrets.
     *
     * @param secret
     * @return
     */
    private static String maskSecret(Object secret) {
        if (secret != null) {
            String secretStr = secret.toString();
            if (secretStr != null) {
                return "*".repeat(secretStr.length());
            }
            else {
                return null;
            }
        }
        else {
            return null;
        }
    }
}
