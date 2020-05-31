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
package org.drasyl;

import ch.qos.logback.classic.Level;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.drasyl.util.NetworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * This class represents the configuration for a {@link DrasylNode}. For example, it defines the
 * identity and the Super Peer.
 */
public class DrasylNodeConfig {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylNodeConfig.class); // NOSONAR
    //======================================== Config Paths ========================================
    static final String LOGLEVEL = "drasyl.loglevel";
    static final String IDENTITY_PUBLIC_KEY = "drasyl.identity.public-key";
    static final String IDENTITY_PRIVATE_KEY = "drasyl.identity.private-key";
    static final String IDENTITY_PATH = "drasyl.identity.path";
    static final String USER_AGENT = "drasyl.user-agent";
    static final String MAX_CONTENT_LENGTH = "drasyl.max-content-length";
    static final String FLUSH_BUFFER_SIZE = "drasyl.flush-buffer-size";
    static final String SERVER_ENABLED = "drasyl.server.enabled";
    static final String SERVER_BIND_HOST = "drasyl.server.bind-host";
    static final String SERVER_BIND_PORT = "drasyl.server.bind-port";
    static final String SERVER_ENDPOINTS = "drasyl.server.endpoints";
    static final String SERVER_IDLE_RETRIES = "drasyl.server.idle.retries";
    static final String SERVER_IDLE_TIMEOUT = "drasyl.server.idle.timeout";
    static final String SERVER_SSL_ENABLED = "drasyl.server.ssl.enabled";
    static final String SERVER_SSL_PROTOCOLS = "drasyl.server.ssl.protocols";
    static final String SERVER_HANDSHAKE_TIMEOUT = "drasyl.server.handshake-timeout";
    static final String SERVER_CHANNEL_INITIALIZER = "drasyl.server.channel-initializer";
    static final String SUPER_PEER_ENABLED = "drasyl.super-peer.enabled";
    static final String SUPER_PEER_ENDPOINTS = "drasyl.super-peer.endpoints";
    static final String SUPER_PEER_PUBLIC_KEY = "drasyl.super-peer.public-key";
    static final String SUPER_PEER_RETRY_DELAYS = "drasyl.super-peer.retry-delays";
    static final String SUPER_PEER_HANDSHAKE_TIMEOUT = "drasyl.super-peer.handshake-timeout";
    static final String SUPER_PEER_CHANNEL_INITIALIZER = "drasyl.super-peer.channel-initializer";
    static final String SUPER_PEER_IDLE_RETRIES = "drasyl.super-peer.idle.retries";
    static final String SUPER_PEER_IDLE_TIMEOUT = "drasyl.super-peer.idle.timeout";
    //======================================= Config Values ========================================
    private final Level loglevel; // NOSONAR
    private final String identityPublicKey;
    private final String identityPrivateKey;
    private final Path identityPath;
    private final String userAgent;
    private final String serverBindHost;
    private final boolean serverEnabled;
    private final int serverBindPort;
    private final short serverIdleRetries;
    private final Duration serverIdleTimeout;
    private final int flushBufferSize;
    private final boolean serverSSLEnabled;
    private final List<String> serverSSLProtocols;
    private final Duration serverHandshakeTimeout;
    private final Set<String> serverEndpoints;
    private final String serverChannelInitializer;
    private final int maxContentLength;
    private final boolean superPeerEnabled;
    private final Set<String> superPeerEndpoints;
    private final String superPeerPublicKey;
    private final List<Duration> superPeerRetryDelays;
    private final Duration superPeerHandshakeTimeout;
    private final String superPeerChannelInitializer;
    private final short superPeerIdleRetries;
    private final Duration superPeerIdleTimeout;

    /**
     * Creates a new config for a drasyl node.
     *
     * @param config config to be loaded
     * @throws ConfigException if the given config is invalid
     */
    public DrasylNodeConfig(Config config) {
        this(config, NetworkUtil::getAddresses);
    }

    DrasylNodeConfig(Config config, Supplier<Set<String>> networkAddressesProvider) {
        config.checkValid(ConfigFactory.defaultReference(), "drasyl");

        // init
        this.loglevel = Level.valueOf(config.getString(LOGLEVEL));
        this.userAgent = config.getString(USER_AGENT);

        // init identity config
        this.identityPublicKey = config.getString(IDENTITY_PUBLIC_KEY);
        this.identityPrivateKey = config.getString(IDENTITY_PRIVATE_KEY);
        this.identityPath = Paths.get(config.getString(IDENTITY_PATH));

        // Init server config
        this.serverEnabled = config.getBoolean(SERVER_ENABLED);
        this.serverBindHost = config.getString(SERVER_BIND_HOST);
        this.serverBindPort = config.getInt(SERVER_BIND_PORT);
        this.serverIdleRetries = getShort(config, SERVER_IDLE_RETRIES);
        this.serverIdleTimeout = config.getDuration(SERVER_IDLE_TIMEOUT);
        this.flushBufferSize = config.getInt(FLUSH_BUFFER_SIZE);
        this.serverHandshakeTimeout = config.getDuration(SERVER_HANDSHAKE_TIMEOUT);
        this.serverChannelInitializer = config.getString(SERVER_CHANNEL_INITIALIZER);
        this.maxContentLength = (int) Math.min(config.getMemorySize(MAX_CONTENT_LENGTH).toBytes(), Integer.MAX_VALUE);

        this.serverSSLEnabled = config.getBoolean(SERVER_SSL_ENABLED);
        this.serverSSLProtocols = config.getStringList(SERVER_SSL_PROTOCOLS);

        if (!config.getStringList(SERVER_ENDPOINTS).isEmpty()) {
            this.serverEndpoints = new HashSet<>(config.getStringList(SERVER_ENDPOINTS));
        }
        else {
            String scheme = serverSSLEnabled ? "wss" : "ws";
            this.serverEndpoints = networkAddressesProvider.get().stream().map(a -> scheme + "://" + a + ":" + serverBindPort).collect(Collectors.toSet());
        }

        // Init super peer config
        this.superPeerEnabled = config.getBoolean(SUPER_PEER_ENABLED);
        this.superPeerEndpoints = new HashSet<>(config.getStringList(SUPER_PEER_ENDPOINTS));
        this.superPeerPublicKey = config.getString(SUPER_PEER_PUBLIC_KEY);
        this.superPeerRetryDelays = config.getDurationList(SUPER_PEER_RETRY_DELAYS);
        this.superPeerHandshakeTimeout = config.getDuration(SUPER_PEER_HANDSHAKE_TIMEOUT);
        this.superPeerChannelInitializer = config.getString(SUPER_PEER_CHANNEL_INITIALIZER);
        this.superPeerIdleRetries = getShort(config, SUPER_PEER_IDLE_RETRIES);
        this.superPeerIdleTimeout = config.getDuration(SUPER_PEER_IDLE_TIMEOUT);
    }

    /**
     * Gets the short at the given path. Similar to {@link Config}, an exception is thrown for an
     * out-of-range value.
     *
     * @param config
     * @param path
     * @return
     */
    private static short getShort(Config config, String path) {
        int integerValue = config.getInt(path);
        if (integerValue > Short.MAX_VALUE || integerValue < Short.MIN_VALUE) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "short", "out-of-range-value " + integerValue);
        }

        return (short) integerValue;
    }

    @SuppressWarnings({ "java:S107" })
    DrasylNodeConfig(Level loglevel,
                     String identityPublicKey,
                     String identityPrivateKey,
                     Path identityPath,
                     String userAgent,
                     String serverBindHost,
                     boolean serverEnabled,
                     int serverBindPort,
                     short serverIdleRetries,
                     Duration serverIdleTimeout,
                     int flushBufferSize,
                     boolean serverSSLEnabled,
                     List<String> serverSSLProtocols,
                     Duration serverHandshakeTimeout,
                     Set<String> serverEndpoints,
                     String serverChannelInitializer,
                     int maxContentLength,
                     boolean superPeerEnabled, Set<String> superPeerEndpoints,
                     String superPeerPublicKey,
                     List<Duration> superPeerRetryDelays,
                     Duration superPeerHandshakeTimeout,
                     String superPeerChannelInitializer,
                     short superPeerIdleRetries,
                     Duration superPeerIdleTimeout) {
        this.loglevel = loglevel;
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
        this.superPeerEnabled = superPeerEnabled;
        this.superPeerEndpoints = superPeerEndpoints;
        this.superPeerPublicKey = superPeerPublicKey;
        this.superPeerRetryDelays = superPeerRetryDelays;
        this.superPeerHandshakeTimeout = superPeerHandshakeTimeout;
        this.superPeerChannelInitializer = superPeerChannelInitializer;
        this.superPeerIdleRetries = superPeerIdleRetries;
        this.superPeerIdleTimeout = superPeerIdleTimeout;
    }

    public Level getLoglevel() {
        return loglevel;
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

    public short getServerIdleRetries() {
        return serverIdleRetries;
    }

    public Duration getSuperPeerHandshakeTimeout() {
        return superPeerHandshakeTimeout;
    }

    public Duration getServerIdleTimeout() {
        return serverIdleTimeout;
    }

    public int getFlushBufferSize() {
        return flushBufferSize;
    }

    public List<String> getServerSSLProtocols() {
        return serverSSLProtocols;
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

    public boolean isSuperPeerEnabled() {
        return superPeerEnabled;
    }

    public Set<String> getSuperPeerEndpoints() {
        return superPeerEndpoints;
    }

    public String getSuperPeerPublicKey() {
        return superPeerPublicKey;
    }

    public List<Duration> getSuperPeerRetryDelays() {
        return superPeerRetryDelays;
    }

    public String getSuperPeerChannelInitializer() {
        return superPeerChannelInitializer;
    }

    public short getSuperPeerIdleRetries() {
        return superPeerIdleRetries;
    }

    public Duration getSuperPeerIdleTimeout() {
        return superPeerIdleTimeout;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identityPublicKey, identityPrivateKey, identityPath, userAgent, serverBindHost, serverEnabled, serverBindPort, serverIdleRetries, serverIdleTimeout, flushBufferSize, serverSSLEnabled, serverSSLProtocols, serverHandshakeTimeout, serverEndpoints, serverChannelInitializer, maxContentLength, superPeerEnabled, superPeerEndpoints, superPeerPublicKey, superPeerRetryDelays, superPeerHandshakeTimeout, superPeerChannelInitializer, superPeerIdleRetries, superPeerIdleTimeout);
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
        return loglevel == that.loglevel &&
                serverEnabled == that.serverEnabled &&
                serverBindPort == that.serverBindPort &&
                serverIdleRetries == that.serverIdleRetries &&
                flushBufferSize == that.flushBufferSize &&
                serverSSLEnabled == that.serverSSLEnabled &&
                maxContentLength == that.maxContentLength &&
                superPeerEnabled == that.superPeerEnabled &&
                superPeerIdleRetries == that.superPeerIdleRetries &&
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
                Objects.equals(superPeerRetryDelays, that.superPeerRetryDelays) &&
                Objects.equals(superPeerHandshakeTimeout, that.superPeerHandshakeTimeout) &&
                Objects.equals(superPeerChannelInitializer, that.superPeerChannelInitializer) &&
                Objects.equals(superPeerIdleTimeout, that.superPeerIdleTimeout);
    }

    @Override
    public String toString() {
        return "DrasylNodeConfig{" +
                "loglevel='" + loglevel + '\'' +
                ", identityPublicKey='" + identityPublicKey + '\'' +
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
                ", superPeerEnabled=" + superPeerEnabled +
                ", superPeerEndpoints=" + superPeerEndpoints +
                ", superPeerPublicKey='" + superPeerPublicKey + '\'' +
                ", superPeerRetryDelays=" + superPeerRetryDelays +
                ", superPeerHandshakeTimeout=" + superPeerHandshakeTimeout +
                ", superPeerChannelInitializer='" + superPeerChannelInitializer + '\'' +
                ", superPeerIdleRetries=" + superPeerIdleRetries +
                ", superPeerIdleTimeout=" + superPeerIdleTimeout +
                '}';
    }
}
