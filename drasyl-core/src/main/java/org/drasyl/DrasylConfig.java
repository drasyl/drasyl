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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * This class represents the configuration for a {@link DrasylNode}. For example, it defines the
 * identity and the Super Peer.
 */
public class DrasylConfig {
    static final DrasylConfig DEFAULT = new DrasylConfig(ConfigFactory.defaultReference());
    //======================================== Config Paths ========================================
    static final String LOGLEVEL = "drasyl.loglevel";
    static final String IDENTITY_PROOF_OF_WORK = "drasyl.identity.proof-of-work";
    static final String IDENTITY_PUBLIC_KEY = "drasyl.identity.public-key";
    static final String IDENTITY_PRIVATE_KEY = "drasyl.identity.private-key";
    static final String IDENTITY_PATH = "drasyl.identity.path";
    static final String MESSAGE_MAX_CONTENT_LENGTH = "drasyl.message.max-content-length";
    static final String MESSAGE_HOP_LIMIT = "drasyl.message.hop-limit";
    static final String MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT = "drasyl.message.composed-message-transfer-timeout";
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
    static final String INTRA_VM_DISCOVERY_ENABLED = "drasyl.intra-vm-discovery.enabled";
    static final String DIRECT_CONNECTIONS_ENABLED = "drasyl.direct-connections.enabled";
    //======================================= Config Values ========================================
    private final Level loglevel; // NOSONAR
    private final ProofOfWork identityProofOfWork;
    private final CompressedPublicKey identityPublicKey;
    private final CompressedPrivateKey identityPrivateKey;
    private final Path identityPath;
    private final String serverBindHost;
    private final boolean serverEnabled;
    private final int serverBindPort;
    private final short serverIdleRetries;
    private final Duration serverIdleTimeout;
    private final int flushBufferSize;
    private final boolean serverSSLEnabled;
    private final List<String> serverSSLProtocols;
    private final Duration serverHandshakeTimeout;
    private final Set<URI> serverEndpoints;
    private final Class<? extends ChannelInitializer<SocketChannel>> serverChannelInitializer;
    private final int messageMaxContentLength;
    private final short messageHopLimit;
    private final Duration messageComposedMessageTransferTimeout;
    private final boolean superPeerEnabled;
    private final Set<URI> superPeerEndpoints;
    private final CompressedPublicKey superPeerPublicKey;
    private final List<Duration> superPeerRetryDelays;
    private final Duration superPeerHandshakeTimeout;
    private final Class<? extends ChannelInitializer<SocketChannel>> superPeerChannelInitializer;
    private final short superPeerIdleRetries;
    private final Duration superPeerIdleTimeout;
    private final boolean intraVmDiscoveryEnabled;
    private final boolean directConnectionsEnabled;

    public DrasylConfig() {
        this(ConfigFactory.load());
    }

    /**
     * Creates a new config for a drasyl node.
     *
     * @param config config to be loaded
     * @throws ConfigException if the given config is invalid
     */
    public DrasylConfig(Config config) {
        config.checkValid(ConfigFactory.defaultReference(), "drasyl");

        this.loglevel = getLoglevel(config, LOGLEVEL);

        // init identity config
        if (config.getInt(IDENTITY_PROOF_OF_WORK) >= 0) {
            this.identityProofOfWork = getProofOfWork(config, IDENTITY_PROOF_OF_WORK);
        }
        else {
            this.identityProofOfWork = null;
        }
        if (!config.getString(IDENTITY_PUBLIC_KEY).isEmpty()) {
            this.identityPublicKey = getPublicKey(config, IDENTITY_PUBLIC_KEY);
        }
        else {
            this.identityPublicKey = null;
        }
        if (!config.getString(IDENTITY_PRIVATE_KEY).isEmpty()) {
            this.identityPrivateKey = getPrivateKey(config, IDENTITY_PRIVATE_KEY);
        }
        else {
            this.identityPrivateKey = null;
        }
        this.identityPath = getPath(config, IDENTITY_PATH);

        // Init server config
        this.serverEnabled = config.getBoolean(SERVER_ENABLED);
        this.serverBindHost = config.getString(SERVER_BIND_HOST);
        this.serverBindPort = config.getInt(SERVER_BIND_PORT);
        this.serverIdleRetries = getShort(config, SERVER_IDLE_RETRIES);
        this.serverIdleTimeout = config.getDuration(SERVER_IDLE_TIMEOUT);
        this.flushBufferSize = config.getInt(FLUSH_BUFFER_SIZE);
        this.serverHandshakeTimeout = config.getDuration(SERVER_HANDSHAKE_TIMEOUT);
        this.serverChannelInitializer = getChannelInitializer(config, SERVER_CHANNEL_INITIALIZER);
        this.messageMaxContentLength = (int) Math.min(config.getMemorySize(MESSAGE_MAX_CONTENT_LENGTH).toBytes(), Integer.MAX_VALUE);
        this.messageComposedMessageTransferTimeout = config.getDuration(MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT);
        this.messageHopLimit = getShort(config, MESSAGE_HOP_LIMIT);
        this.serverSSLEnabled = config.getBoolean(SERVER_SSL_ENABLED);
        this.serverSSLProtocols = config.getStringList(SERVER_SSL_PROTOCOLS);
        this.serverEndpoints = new HashSet<>(getUriList(config, SERVER_ENDPOINTS));

        // Init super peer config
        this.superPeerEnabled = config.getBoolean(SUPER_PEER_ENABLED);
        this.superPeerEndpoints = new HashSet<>(getUriList(config, SUPER_PEER_ENDPOINTS));
        if (!config.getString(SUPER_PEER_PUBLIC_KEY).equals("")) {
            this.superPeerPublicKey = getPublicKey(config, SUPER_PEER_PUBLIC_KEY);
        }
        else {
            this.superPeerPublicKey = null;
        }
        this.superPeerRetryDelays = config.getDurationList(SUPER_PEER_RETRY_DELAYS);
        this.superPeerHandshakeTimeout = config.getDuration(SUPER_PEER_HANDSHAKE_TIMEOUT);
        this.superPeerChannelInitializer = getChannelInitializer(config, SUPER_PEER_CHANNEL_INITIALIZER);
        this.superPeerIdleRetries = getShort(config, SUPER_PEER_IDLE_RETRIES);
        this.superPeerIdleTimeout = config.getDuration(SUPER_PEER_IDLE_TIMEOUT);

        this.intraVmDiscoveryEnabled = config.getBoolean(INTRA_VM_DISCOVERY_ENABLED);
        this.directConnectionsEnabled = config.getBoolean(DIRECT_CONNECTIONS_ENABLED);
    }

    private Level getLoglevel(Config config, String path) {
        return Level.valueOf(config.getString(path));
    }

    /**
     * Gets the proof of work at the given path. Similar to {@link Config}, an exception is thrown
     * for an invalid value.
     *
     * @param config
     * @param path
     * @return
     */
    @SuppressWarnings({ "java:S1192" })
    private ProofOfWork getProofOfWork(Config config, String path) {
        try {
            int intValue = config.getInt(path);
            return new ProofOfWork(intValue);
        }
        catch (IllegalArgumentException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "proof of work", "invalid-value: " + e.getMessage());
        }
    }

    /**
     * Gets the compressed public key at the given path. Similar to {@link Config}, an exception is
     * thrown for an invalid value.
     *
     * @param config
     * @param path
     * @return
     */
    @SuppressWarnings({ "java:S1192" })
    private CompressedPublicKey getPublicKey(Config config, String path) {
        try {
            String stringValue = config.getString(path);
            return CompressedPublicKey.of(stringValue);
        }
        catch (CryptoException | IllegalArgumentException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "compressed public key", "invalid-value: " + e.getMessage());
        }
    }

    /**
     * Gets the compressed private key at the given path. Similar to {@link Config}, an exception is
     * thrown for an invalid value.
     *
     * @param config
     * @param path
     * @return
     */
    @SuppressWarnings({ "java:S1192" })
    private CompressedPrivateKey getPrivateKey(Config config, String path) {
        try {
            String stringValue = config.getString(path);
            return CompressedPrivateKey.of(stringValue);
        }
        catch (CryptoException | IllegalArgumentException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "compressed private key", "invalid-value: " + e.getMessage());
        }
    }

    private Path getPath(Config config, String path) {
        return Paths.get(config.getString(path));
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

    private List<URI> getUriList(Config config, String path) {
        List<String> stringListValue = config.getStringList(path);
        List<URI> uriList = new ArrayList<>();
        try {
            for (String stringValue : stringListValue) {
                uriList.add(new URI(stringValue));
            }
        }
        catch (URISyntaxException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "url", "invalid-value: " + e.getMessage());
        }
        return uriList;
    }

    private Class<ChannelInitializer<SocketChannel>> getChannelInitializer(Config config,
                                                                           String path) {
        String className = config.getString(path);
        try {
            return (Class<ChannelInitializer<SocketChannel>>) Class.forName(className);
        }
        catch (ClassNotFoundException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "socket channel", "class-not-found: " + e.getMessage());
        }
    }

    @SuppressWarnings({ "java:S107" })
    DrasylConfig(Level loglevel,
                 ProofOfWork identityProofOfWork,
                 CompressedPublicKey identityPublicKey,
                 CompressedPrivateKey identityPrivateKey,
                 Path identityPath,
                 String serverBindHost,
                 boolean serverEnabled,
                 int serverBindPort,
                 short serverIdleRetries,
                 Duration serverIdleTimeout,
                 int flushBufferSize,
                 boolean serverSSLEnabled,
                 List<String> serverSSLProtocols,
                 Duration serverHandshakeTimeout,
                 Set<URI> serverEndpoints,
                 Class<? extends ChannelInitializer<SocketChannel>> serverChannelInitializer,
                 int messageMaxContentLength,
                 short messageHopLimit,
                 Duration messageComposedMessageTransferTimeout,
                 boolean superPeerEnabled,
                 Set<URI> superPeerEndpoints,
                 CompressedPublicKey superPeerPublicKey,
                 List<Duration> superPeerRetryDelays,
                 Duration superPeerHandshakeTimeout,
                 Class<? extends ChannelInitializer<SocketChannel>> superPeerChannelInitializer,
                 short superPeerIdleRetries,
                 Duration superPeerIdleTimeout,
                 boolean intraVmDiscoveryEnabled,
                 boolean directConnectionsEnabled) {
        this.loglevel = loglevel;
        this.identityProofOfWork = identityProofOfWork;
        this.identityPublicKey = identityPublicKey;
        this.identityPrivateKey = identityPrivateKey;
        this.identityPath = identityPath;
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
        this.messageMaxContentLength = messageMaxContentLength;
        this.messageHopLimit = messageHopLimit;
        this.messageComposedMessageTransferTimeout = messageComposedMessageTransferTimeout;
        this.superPeerEnabled = superPeerEnabled;
        this.superPeerEndpoints = superPeerEndpoints;
        this.superPeerPublicKey = superPeerPublicKey;
        this.superPeerRetryDelays = superPeerRetryDelays;
        this.superPeerHandshakeTimeout = superPeerHandshakeTimeout;
        this.superPeerChannelInitializer = superPeerChannelInitializer;
        this.superPeerIdleRetries = superPeerIdleRetries;
        this.superPeerIdleTimeout = superPeerIdleTimeout;
        this.intraVmDiscoveryEnabled = intraVmDiscoveryEnabled;
        this.directConnectionsEnabled = directConnectionsEnabled;
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

    public ProofOfWork getIdentityProofOfWork() {
        return identityProofOfWork;
    }

    public CompressedPublicKey getIdentityPublicKey() {
        return identityPublicKey;
    }

    public CompressedPrivateKey getIdentityPrivateKey() {
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

    public Set<URI> getServerEndpoints() {
        return serverEndpoints;
    }

    public Class<? extends ChannelInitializer<SocketChannel>> getServerChannelInitializer() {
        return serverChannelInitializer;
    }

    public int getMessageMaxContentLength() {
        return messageMaxContentLength;
    }

    public short getMessageHopLimit() {
        return messageHopLimit;
    }

    public Duration getMessageComposedMessageTransferTimeout() {
        return messageComposedMessageTransferTimeout;
    }

    public boolean isSuperPeerEnabled() {
        return superPeerEnabled;
    }

    public Set<URI> getSuperPeerEndpoints() {
        return superPeerEndpoints;
    }

    public CompressedPublicKey getSuperPeerPublicKey() {
        return superPeerPublicKey;
    }

    public List<Duration> getSuperPeerRetryDelays() {
        return superPeerRetryDelays;
    }

    public Class<? extends ChannelInitializer<SocketChannel>> getSuperPeerChannelInitializer() {
        return superPeerChannelInitializer;
    }

    public short getSuperPeerIdleRetries() {
        return superPeerIdleRetries;
    }

    public Duration getSuperPeerIdleTimeout() {
        return superPeerIdleTimeout;
    }

    public boolean isIntraVmDiscoveryEnabled() {
        return intraVmDiscoveryEnabled;
    }

    public boolean areDirectConnectionsEnabled() {
        return directConnectionsEnabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(loglevel, identityProofOfWork, identityPublicKey, identityPrivateKey, identityPath, serverBindHost, serverEnabled, serverBindPort, serverIdleRetries, serverIdleTimeout, flushBufferSize, serverSSLEnabled, serverSSLProtocols, serverHandshakeTimeout, serverEndpoints, serverChannelInitializer, messageMaxContentLength, messageHopLimit, messageComposedMessageTransferTimeout, superPeerEnabled, superPeerEndpoints, superPeerPublicKey, superPeerRetryDelays, superPeerHandshakeTimeout, superPeerChannelInitializer, superPeerIdleRetries, superPeerIdleTimeout, intraVmDiscoveryEnabled, directConnectionsEnabled);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DrasylConfig that = (DrasylConfig) o;
        return serverEnabled == that.serverEnabled &&
                serverBindPort == that.serverBindPort &&
                serverIdleRetries == that.serverIdleRetries &&
                flushBufferSize == that.flushBufferSize &&
                serverSSLEnabled == that.serverSSLEnabled &&
                messageMaxContentLength == that.messageMaxContentLength &&
                messageHopLimit == that.messageHopLimit &&
                superPeerEnabled == that.superPeerEnabled &&
                superPeerIdleRetries == that.superPeerIdleRetries &&
                intraVmDiscoveryEnabled == that.intraVmDiscoveryEnabled &&
                directConnectionsEnabled == that.directConnectionsEnabled &&
                Objects.equals(loglevel, that.loglevel) &&
                Objects.equals(identityProofOfWork, that.identityProofOfWork) &&
                Objects.equals(identityPublicKey, that.identityPublicKey) &&
                Objects.equals(identityPrivateKey, that.identityPrivateKey) &&
                Objects.equals(identityPath, that.identityPath) &&
                Objects.equals(serverBindHost, that.serverBindHost) &&
                Objects.equals(serverIdleTimeout, that.serverIdleTimeout) &&
                Objects.equals(serverSSLProtocols, that.serverSSLProtocols) &&
                Objects.equals(serverHandshakeTimeout, that.serverHandshakeTimeout) &&
                Objects.equals(serverEndpoints, that.serverEndpoints) &&
                Objects.equals(serverChannelInitializer, that.serverChannelInitializer) &&
                Objects.equals(messageComposedMessageTransferTimeout, that.messageComposedMessageTransferTimeout) &&
                Objects.equals(superPeerEndpoints, that.superPeerEndpoints) &&
                Objects.equals(superPeerPublicKey, that.superPeerPublicKey) &&
                Objects.equals(superPeerRetryDelays, that.superPeerRetryDelays) &&
                Objects.equals(superPeerHandshakeTimeout, that.superPeerHandshakeTimeout) &&
                Objects.equals(superPeerChannelInitializer, that.superPeerChannelInitializer) &&
                Objects.equals(superPeerIdleTimeout, that.superPeerIdleTimeout);
    }

    @Override
    public String toString() {
        return "DrasylConfig{" +
                "loglevel=" + loglevel +
                ", identityProofOfWork=" + identityProofOfWork +
                ", identityPublicKey=" + identityPublicKey +
                ", identityPrivateKey=" + maskSecret(identityPrivateKey) +
                ", identityPath=" + identityPath +
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
                ", serverChannelInitializer=" + serverChannelInitializer +
                ", messageMaxContentLength=" + messageMaxContentLength +
                ", messageHopLimit=" + messageHopLimit +
                ", messageComposedMessageTransferTimeout=" + messageComposedMessageTransferTimeout +
                ", superPeerEnabled=" + superPeerEnabled +
                ", superPeerEndpoints=" + superPeerEndpoints +
                ", superPeerPublicKey=" + superPeerPublicKey +
                ", superPeerRetryDelays=" + superPeerRetryDelays +
                ", superPeerHandshakeTimeout=" + superPeerHandshakeTimeout +
                ", superPeerChannelInitializer=" + superPeerChannelInitializer +
                ", superPeerIdleRetries=" + superPeerIdleRetries +
                ", superPeerIdleTimeout=" + superPeerIdleTimeout +
                ", intraVmDiscoveryEnabled=" + intraVmDiscoveryEnabled +
                ", directConnectionsEnabled=" + directConnectionsEnabled +
                '}';
    }

    public static DrasylConfig parseFile(File file) {
        return new DrasylConfig(ConfigFactory.parseFile(file).withFallback(ConfigFactory.load()));
    }

    public static Builder newBuilder() {
        return newBuilder(DEFAULT);
    }

    public static Builder newBuilder(DrasylConfig config) {
        return new Builder(
                config.loglevel,
                config.identityProofOfWork,
                config.identityPublicKey,
                config.identityPrivateKey,
                config.identityPath,
                config.serverBindHost,
                config.serverEnabled,
                config.serverBindPort,
                config.serverIdleRetries,
                config.serverIdleTimeout,
                config.flushBufferSize,
                config.serverSSLEnabled,
                config.serverSSLProtocols,
                config.serverHandshakeTimeout,
                config.serverEndpoints,
                config.serverChannelInitializer,
                config.messageMaxContentLength,
                config.messageHopLimit,
                config.messageComposedMessageTransferTimeout,
                config.superPeerEnabled,
                config.superPeerEndpoints,
                config.superPeerPublicKey,
                config.superPeerRetryDelays,
                config.superPeerHandshakeTimeout,
                config.superPeerChannelInitializer,
                config.superPeerIdleRetries,
                config.superPeerIdleTimeout,
                config.intraVmDiscoveryEnabled,
                config.directConnectionsEnabled
        );
    }

    public static final class Builder {
        //======================================= Config Values ========================================
        private Level loglevel; // NOSONAR
        private ProofOfWork identityProofOfWork;
        private CompressedPublicKey identityPublicKey;
        private CompressedPrivateKey identityPrivateKey;
        private Path identityPath;
        private String serverBindHost;
        private boolean serverEnabled;
        private int serverBindPort;
        private short serverIdleRetries;
        private Duration serverIdleTimeout;
        private int flushBufferSize;
        private boolean serverSSLEnabled;
        private List<String> serverSSLProtocols;
        private Duration serverHandshakeTimeout;
        private Set<URI> serverEndpoints;
        private Class<? extends ChannelInitializer<SocketChannel>> serverChannelInitializer;
        private int messageMaxContentLength;
        private short messageHopLimit;
        private Duration messageComposedMessageTransferTimeout;
        private boolean superPeerEnabled;
        private Set<URI> superPeerEndpoints;
        private CompressedPublicKey superPeerPublicKey;
        private List<Duration> superPeerRetryDelays;
        private Duration superPeerHandshakeTimeout;
        private Class<? extends ChannelInitializer<SocketChannel>> superPeerChannelInitializer;
        private short superPeerIdleRetries;
        private Duration superPeerIdleTimeout;
        private boolean intraVmDiscoveryEnabled;
        private boolean directConnectionsEnabled;

        @SuppressWarnings({ "java:S107" })
        private Builder(Level loglevel,
                        ProofOfWork identityProofOfWork,
                        CompressedPublicKey identityPublicKey,
                        CompressedPrivateKey identityPrivateKey,
                        Path identityPath,
                        String serverBindHost,
                        boolean serverEnabled,
                        int serverBindPort,
                        short serverIdleRetries,
                        Duration serverIdleTimeout,
                        int flushBufferSize,
                        boolean serverSSLEnabled,
                        List<String> serverSSLProtocols,
                        Duration serverHandshakeTimeout,
                        Set<URI> serverEndpoints,
                        Class<? extends ChannelInitializer<SocketChannel>> serverChannelInitializer,
                        int messageMaxContentLength,
                        short messageHopLimit,
                        Duration messageComposedMessageTransferTimeout,
                        boolean superPeerEnabled,
                        Set<URI> superPeerEndpoints,
                        CompressedPublicKey superPeerPublicKey,
                        List<Duration> superPeerRetryDelays,
                        Duration superPeerHandshakeTimeout,
                        Class<? extends ChannelInitializer<SocketChannel>> superPeerChannelInitializer,
                        short superPeerIdleRetries,
                        Duration superPeerIdleTimeout,
                        boolean intraVmDiscoveryEnabled,
                        boolean directConnectionsEnabled) {
            this.loglevel = loglevel;
            this.identityProofOfWork = identityProofOfWork;
            this.identityPublicKey = identityPublicKey;
            this.identityPrivateKey = identityPrivateKey;
            this.identityPath = identityPath;
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
            this.messageMaxContentLength = messageMaxContentLength;
            this.messageHopLimit = messageHopLimit;
            this.messageComposedMessageTransferTimeout = messageComposedMessageTransferTimeout;
            this.superPeerEnabled = superPeerEnabled;
            this.superPeerEndpoints = superPeerEndpoints;
            this.superPeerPublicKey = superPeerPublicKey;
            this.superPeerRetryDelays = superPeerRetryDelays;
            this.superPeerHandshakeTimeout = superPeerHandshakeTimeout;
            this.superPeerChannelInitializer = superPeerChannelInitializer;
            this.superPeerIdleRetries = superPeerIdleRetries;
            this.superPeerIdleTimeout = superPeerIdleTimeout;
            this.intraVmDiscoveryEnabled = intraVmDiscoveryEnabled;
            this.directConnectionsEnabled = directConnectionsEnabled;
        }

        public Builder loglevel(Level loglevel) {
            this.loglevel = loglevel;
            return this;
        }

        public Builder identityProofOfWork(ProofOfWork identityProofOfWork) {
            this.identityProofOfWork = identityProofOfWork;
            return this;
        }

        public Builder identityPublicKey(CompressedPublicKey identityPublicKey) {
            this.identityPublicKey = identityPublicKey;
            return this;
        }

        public Builder identityPrivateKey(CompressedPrivateKey identityPrivateKey) {
            this.identityPrivateKey = identityPrivateKey;
            return this;
        }

        public Builder identityPath(Path identityPath) {
            this.identityPath = identityPath;
            return this;
        }

        public Builder serverBindHost(String serverBindHost) {
            this.serverBindHost = serverBindHost;
            return this;
        }

        public Builder serverEnabled(boolean serverEnabled) {
            this.serverEnabled = serverEnabled;
            return this;
        }

        public Builder serverBindPort(int serverBindPort) {
            this.serverBindPort = serverBindPort;
            return this;
        }

        public Builder serverIdleRetries(short serverIdleRetries) {
            this.serverIdleRetries = serverIdleRetries;
            return this;
        }

        public Builder serverIdleTimeout(Duration serverIdleTimeout) {
            this.serverIdleTimeout = serverIdleTimeout;
            return this;
        }

        public Builder flushBufferSize(int flushBufferSize) {
            this.flushBufferSize = flushBufferSize;
            return this;
        }

        public Builder serverSSLEnabled(boolean serverSSLEnabled) {
            this.serverSSLEnabled = serverSSLEnabled;
            return this;
        }

        public Builder serverSSLProtocols(List<String> serverSSLProtocols) {
            this.serverSSLProtocols = serverSSLProtocols;
            return this;
        }

        public Builder serverHandshakeTimeout(Duration serverHandshakeTimeout) {
            this.serverHandshakeTimeout = serverHandshakeTimeout;
            return this;
        }

        public Builder serverEndpoints(Set<URI> serverEndpoints) {
            this.serverEndpoints = serverEndpoints;
            return this;
        }

        public Builder serverChannelInitializer(Class<? extends ChannelInitializer<SocketChannel>> serverChannelInitializer) {
            this.serverChannelInitializer = serverChannelInitializer;
            return this;
        }

        public Builder messageMaxContentLength(int messageMaxContentLength) {
            this.messageMaxContentLength = messageMaxContentLength;
            return this;
        }

        public Builder messageHopLimit(short messageHopLimit) {
            this.messageHopLimit = messageHopLimit;
            return this;
        }

        public Builder messageComposedMessageTransferTimeout(Duration composedMessageTransferTimeout) {
            this.messageComposedMessageTransferTimeout = composedMessageTransferTimeout;
            return this;
        }

        public Builder superPeerEnabled(boolean superPeerEnabled) {
            this.superPeerEnabled = superPeerEnabled;
            return this;
        }

        public Builder superPeerEndpoints(Set<URI> superPeerEndpoints) {
            this.superPeerEndpoints = superPeerEndpoints;
            return this;
        }

        public Builder superPeerPublicKey(CompressedPublicKey superPeerPublicKey) {
            this.superPeerPublicKey = superPeerPublicKey;
            return this;
        }

        public Builder superPeerRetryDelays(List<Duration> superPeerRetryDelays) {
            this.superPeerRetryDelays = superPeerRetryDelays;
            return this;
        }

        public Builder superPeerHandshakeTimeout(Duration superPeerHandshakeTimeout) {
            this.superPeerHandshakeTimeout = superPeerHandshakeTimeout;
            return this;
        }

        public Builder superPeerChannelInitializer(Class<? extends ChannelInitializer<SocketChannel>> superPeerChannelInitializer) {
            this.superPeerChannelInitializer = superPeerChannelInitializer;
            return this;
        }

        public Builder superPeerIdleRetries(short superPeerIdleRetries) {
            this.superPeerIdleRetries = superPeerIdleRetries;
            return this;
        }

        public Builder superPeerIdleTimeout(Duration superPeerIdleTimeout) {
            this.superPeerIdleTimeout = superPeerIdleTimeout;
            return this;
        }

        public Builder intraVmDiscoveryEnabled(boolean intraVmDiscoveryEnabled) {
            this.intraVmDiscoveryEnabled = intraVmDiscoveryEnabled;
            return this;
        }

        public Builder directConnectionsEnabled(boolean directConnectionsEnabled) {
            this.directConnectionsEnabled = directConnectionsEnabled;
            return this;
        }

        public DrasylConfig build() {
            return new DrasylConfig(loglevel, identityProofOfWork, identityPublicKey, identityPrivateKey, identityPath, serverBindHost, serverEnabled, serverBindPort, serverIdleRetries, serverIdleTimeout, flushBufferSize, serverSSLEnabled, serverSSLProtocols, serverHandshakeTimeout, serverEndpoints, serverChannelInitializer, messageMaxContentLength, messageHopLimit, messageComposedMessageTransferTimeout, superPeerEnabled, superPeerEndpoints, superPeerPublicKey, superPeerRetryDelays, superPeerHandshakeTimeout, superPeerChannelInitializer, superPeerIdleRetries, superPeerIdleTimeout, intraVmDiscoveryEnabled, directConnectionsEnabled);
        }
    }
}
