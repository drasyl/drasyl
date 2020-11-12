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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.plugins.DrasylPlugin;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * This class represents the configuration for a {@link DrasylNode}. For example, it defines the
 * identity and the Super Peer.
 * <p>
 * This is an immutable object.
 */
public class DrasylConfig {
    static final DrasylConfig DEFAULT = new DrasylConfig(ConfigFactory.defaultReference());
    //======================================== Config Paths ========================================
    static final String NETWORK_ID = "drasyl.network.id";
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
    static final String SERVER_SSL_ENABLED = "drasyl.server.ssl.enabled";
    static final String SERVER_SSL_PROTOCOLS = "drasyl.server.ssl.protocols";
    static final String SERVER_HANDSHAKE_TIMEOUT = "drasyl.server.handshake-timeout";
    static final String SERVER_CHANNEL_INITIALIZER = "drasyl.server.channel-initializer";
    static final String SERVER_EXPOSE_ENABLED = "drasyl.server.expose.enabled";
    static final String SUPER_PEER_ENABLED = "drasyl.super-peer.enabled";
    static final String SUPER_PEER_ENDPOINTS = "drasyl.super-peer.endpoints";
    static final String SUPER_PEER_RETRY_DELAYS = "drasyl.super-peer.retry-delays";
    static final String SUPER_PEER_HANDSHAKE_TIMEOUT = "drasyl.super-peer.handshake-timeout";
    static final String SUPER_PEER_CHANNEL_INITIALIZER = "drasyl.super-peer.channel-initializer";
    static final String SUPER_PEER_IDLE_RETRIES = "drasyl.super-peer.idle.retries";
    static final String SUPER_PEER_IDLE_TIMEOUT = "drasyl.super-peer.idle.timeout";
    static final String INTRA_VM_DISCOVERY_ENABLED = "drasyl.intra-vm-discovery.enabled";
    static final String LOCAL_HOST_DISCOVERY_ENABLED = "drasyl.local-host-discovery.enabled";
    static final String LOCAL_HOST_DISCOVERY_PATH = "drasyl.local-host-discovery.path";
    static final String LOCAL_HOST_DISCOVERY_LEASE_TIME = "drasyl.local-host-discovery.lease-time";
    static final String DIRECT_CONNECTIONS_ENABLED = "drasyl.direct-connections.enabled";
    static final String DIRECT_CONNECTIONS_MAX_CONCURRENT_CONNECTIONS = "drasyl.direct-connections.max-concurrent-connections";
    static final String DIRECT_CONNECTIONS_RETRY_DELAYS = "drasyl.direct-connections.retry-delays";
    static final String DIRECT_CONNECTIONS_HANDSHAKE_TIMEOUT = "drasyl.direct-connections.handshake-timeout";
    static final String DIRECT_CONNECTIONS_CHANNEL_INITIALIZER = "drasyl.direct-connections.channel-initializer";
    static final String DIRECT_CONNECTIONS_IDLE_RETRIES = "drasyl.direct-connections.idle.retries";
    static final String DIRECT_CONNECTIONS_IDLE_TIMEOUT = "drasyl.direct-connections.idle.timeout";
    static final String MONITORING_ENABLED = "drasyl.monitoring.enabled";
    static final String MONITORING_INFLUX_URI = "drasyl.monitoring.influx.uri";
    static final String MONITORING_INFLUX_USER = "drasyl.monitoring.influx.user";
    static final String MONITORING_INFLUX_PASSWORD = "drasyl.monitoring.influx.password";
    static final String MONITORING_INFLUX_DATABASE = "drasyl.monitoring.influx.database";
    static final String MONITORING_INFLUX_REPORTING_FREQUENCY = "drasyl.monitoring.influx.reporting-frequency";
    static final String PLUGINS = "drasyl.plugins";
    static final String MARSHALLING_INBOUND_ALLOWED_TYPES = "drasyl.marshalling.inbound.allowed-types";
    static final String MARSHALLING_INBOUND_ALLOW_ALL_PRIMITIVES = "drasyl.marshalling.inbound.allow-all-primitives";
    static final String MARSHALLING_INBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES = "drasyl.marshalling.inbound.allow-array-of-defined-types";
    static final String MARSHALLING_INBOUND_ALLOWED_PACKAGES = "drasyl.marshalling.inbound.allowed-packages";
    static final String MARSHALLING_OUTBOUND_ALLOWED_TYPES = "drasyl.marshalling.outbound.allowed-types";
    static final String MARSHALLING_OUTBOUND_ALLOW_ALL_PRIMITIVES = "drasyl.marshalling.outbound.allow-all-primitives";
    static final String MARSHALLING_OUTBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES = "drasyl.marshalling.outbound.allow-array-of-defined-types";
    static final String MARSHALLING_OUTBOUND_ALLOWED_PACKAGES = "drasyl.marshalling.outbound.allowed-packages";
    //======================================= Config Values ========================================
    private final int networkId;
    private final ProofOfWork identityProofOfWork;
    private final CompressedPublicKey identityPublicKey;
    private final CompressedPrivateKey identityPrivateKey;
    private final Path identityPath;
    private final InetAddress serverBindHost;
    private final boolean serverEnabled;
    private final int serverBindPort;
    private final int flushBufferSize;
    private final boolean serverSSLEnabled;
    private final Set<String> serverSSLProtocols;
    private final Duration serverHandshakeTimeout;
    private final Set<Endpoint> serverEndpoints;
    private final Class<? extends ChannelInitializer<SocketChannel>> serverChannelInitializer;
    private final boolean serverExposeEnabled;
    private final int messageMaxContentLength;
    private final short messageHopLimit;
    private final Duration messageComposedMessageTransferTimeout;
    private final boolean superPeerEnabled;
    private final Set<Endpoint> superPeerEndpoints;
    private final List<Duration> superPeerRetryDelays;
    private final Duration superPeerHandshakeTimeout;
    private final Class<? extends ChannelInitializer<SocketChannel>> superPeerChannelInitializer;
    private final short superPeerIdleRetries;
    private final Duration superPeerIdleTimeout;
    private final boolean intraVmDiscoveryEnabled;
    private final boolean localHostDiscoveryEnabled;
    private final Path localHostDiscoveryPath;
    private final Duration localHostDiscoveryLeaseTime;
    private final boolean directConnectionsEnabled;
    private final int directConnectionsMaxConcurrentConnections;
    private final List<Duration> directConnectionsRetryDelays;
    private final Duration directConnectionsHandshakeTimeout;
    private final Class<? extends ChannelInitializer<SocketChannel>> directConnectionsChannelInitializer;
    private final short directConnectionsIdleRetries;
    private final Duration directConnectionsIdleTimeout;
    private final boolean monitoringEnabled;
    private final URI monitoringInfluxUri;
    private final String monitoringInfluxUser;
    private final String monitoringInfluxPassword;
    private final String monitoringInfluxDatabase;
    private final Duration monitoringInfluxReportingFrequency;
    private final Set<DrasylPlugin> pluginSet;
    private final List<String> marshallingInboundAllowedTypes;
    private final boolean marshallingInboundAllowAllPrimitives;
    private final boolean marshallingInboundAllowArrayOfDefinedTypes;
    private final List<String> marshallingInboundAllowedPackages;
    private final List<String> marshallingOutboundAllowedTypes;
    private final boolean marshallingOutboundAllowAllPrimitives;
    private final boolean marshallingOutboundAllowArrayOfDefinedTypes;
    private final List<String> marshallingOutboundAllowedPackages;

    public DrasylConfig() {
        this(ConfigFactory.load());
    }

    /**
     * Creates a new config for a drasyl node.
     *
     * @param config config to be loaded
     */
    public DrasylConfig(final Config config) {
        config.checkValid(ConfigFactory.defaultReference(), "drasyl");

        this.networkId = config.getInt(NETWORK_ID);

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
        this.serverBindHost = getInetAddress(config, SERVER_BIND_HOST);
        this.serverBindPort = config.getInt(SERVER_BIND_PORT);
        this.flushBufferSize = config.getInt(FLUSH_BUFFER_SIZE);
        this.serverHandshakeTimeout = config.getDuration(SERVER_HANDSHAKE_TIMEOUT);
        this.serverChannelInitializer = getChannelInitializer(config, SERVER_CHANNEL_INITIALIZER);
        this.messageMaxContentLength = (int) Math.min(config.getMemorySize(MESSAGE_MAX_CONTENT_LENGTH).toBytes(), Integer.MAX_VALUE);
        this.messageComposedMessageTransferTimeout = config.getDuration(MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT);
        this.messageHopLimit = getShort(config, MESSAGE_HOP_LIMIT);
        this.serverSSLEnabled = config.getBoolean(SERVER_SSL_ENABLED);
        this.serverSSLProtocols = Set.copyOf(config.getStringList(SERVER_SSL_PROTOCOLS));
        this.serverEndpoints = Set.copyOf(getEndpointList(config, SERVER_ENDPOINTS));
        this.serverExposeEnabled = config.getBoolean(SERVER_EXPOSE_ENABLED);

        // Init super peer config
        this.superPeerEnabled = config.getBoolean(SUPER_PEER_ENABLED);
        this.superPeerEndpoints = Set.copyOf(getEndpointList(config, SUPER_PEER_ENDPOINTS));
        this.superPeerRetryDelays = List.copyOf(config.getDurationList(SUPER_PEER_RETRY_DELAYS));
        this.superPeerHandshakeTimeout = config.getDuration(SUPER_PEER_HANDSHAKE_TIMEOUT);
        this.superPeerChannelInitializer = getChannelInitializer(config, SUPER_PEER_CHANNEL_INITIALIZER);
        this.superPeerIdleRetries = getShort(config, SUPER_PEER_IDLE_RETRIES);
        this.superPeerIdleTimeout = config.getDuration(SUPER_PEER_IDLE_TIMEOUT);

        this.intraVmDiscoveryEnabled = config.getBoolean(INTRA_VM_DISCOVERY_ENABLED);

        // Init local host discovery config
        this.localHostDiscoveryEnabled = config.getBoolean(LOCAL_HOST_DISCOVERY_ENABLED);
        if (!config.getString(LOCAL_HOST_DISCOVERY_PATH).equals("")) {
            this.localHostDiscoveryPath = getPath(config, LOCAL_HOST_DISCOVERY_PATH);
        }
        else {
            this.localHostDiscoveryPath = Paths.get(System.getProperty("java.io.tmpdir"), "drasyl-discovery");
        }
        this.localHostDiscoveryLeaseTime = config.getDuration(LOCAL_HOST_DISCOVERY_LEASE_TIME);

        // Init direct connections config
        this.directConnectionsEnabled = config.getBoolean(DIRECT_CONNECTIONS_ENABLED);
        this.directConnectionsMaxConcurrentConnections = config.getInt(DIRECT_CONNECTIONS_MAX_CONCURRENT_CONNECTIONS);
        this.directConnectionsRetryDelays = List.copyOf(config.getDurationList(DIRECT_CONNECTIONS_RETRY_DELAYS));
        this.directConnectionsHandshakeTimeout = config.getDuration(DIRECT_CONNECTIONS_HANDSHAKE_TIMEOUT);
        this.directConnectionsChannelInitializer = getChannelInitializer(config, DIRECT_CONNECTIONS_CHANNEL_INITIALIZER);
        this.directConnectionsIdleRetries = getShort(config, DIRECT_CONNECTIONS_IDLE_RETRIES);
        this.directConnectionsIdleTimeout = config.getDuration(DIRECT_CONNECTIONS_IDLE_TIMEOUT);

        // Init monitoring config
        monitoringEnabled = config.getBoolean(MONITORING_ENABLED);
        monitoringInfluxUri = getURI(config, MONITORING_INFLUX_URI);
        monitoringInfluxUser = config.getString(MONITORING_INFLUX_USER);
        monitoringInfluxPassword = config.getString(MONITORING_INFLUX_PASSWORD);
        monitoringInfluxDatabase = config.getString(MONITORING_INFLUX_DATABASE);
        monitoringInfluxReportingFrequency = config.getDuration(MONITORING_INFLUX_REPORTING_FREQUENCY);

        // Load plugins
        this.pluginSet = Set.copyOf(getPlugins(config, PLUGINS));

        // Load marshalling config
        this.marshallingInboundAllowedTypes = List.copyOf(config.getStringList(MARSHALLING_INBOUND_ALLOWED_TYPES));
        this.marshallingInboundAllowAllPrimitives = config.getBoolean(MARSHALLING_INBOUND_ALLOW_ALL_PRIMITIVES);
        this.marshallingInboundAllowArrayOfDefinedTypes = config.getBoolean(MARSHALLING_INBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES);
        this.marshallingInboundAllowedPackages = List.copyOf(config.getStringList(MARSHALLING_INBOUND_ALLOWED_PACKAGES));
        this.marshallingOutboundAllowedTypes = List.copyOf(config.getStringList(MARSHALLING_OUTBOUND_ALLOWED_TYPES));
        this.marshallingOutboundAllowAllPrimitives = config.getBoolean(MARSHALLING_OUTBOUND_ALLOW_ALL_PRIMITIVES);
        this.marshallingOutboundAllowArrayOfDefinedTypes = config.getBoolean(MARSHALLING_OUTBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES);
        this.marshallingOutboundAllowedPackages = List.copyOf(config.getStringList(MARSHALLING_OUTBOUND_ALLOWED_PACKAGES));
    }

    /**
     * Gets the {@link ProofOfWork} at the given path. Similar to {@link Config}, an exception is
     * thrown for an invalid value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link ProofOfWork} value at the requested path
     * @throws ConfigException.Missing   if value is absent or null
     * @throws ConfigException.WrongType if value is not convertible to a {@link ProofOfWork}
     */
    @SuppressWarnings({ "java:S1192" })
    public static ProofOfWork getProofOfWork(final Config config, final String path) {
        try {
            final int intValue = config.getInt(path);
            return ProofOfWork.of(intValue);
        }
        catch (final IllegalArgumentException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "proof of work", "invalid-value: " + e.getMessage());
        }
    }

    /**
     * Gets the {@link CompressedPublicKey} at the given path. Similar to {@link Config}, an
     * exception is thrown for an invalid value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link CompressedPublicKey} value at the requested path
     * @throws ConfigException.Missing   if value is absent or null
     * @throws ConfigException.WrongType if value is not convertible to a {@link CompressedPublicKey}
     */
    @SuppressWarnings({ "java:S1192" })
    public static CompressedPublicKey getPublicKey(final Config config, final String path) {
        try {
            final String stringValue = config.getString(path);
            return CompressedPublicKey.of(stringValue);
        }
        catch (final CryptoException | IllegalArgumentException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "compressed public key", "invalid-value: " + e.getMessage());
        }
    }

    /**
     * Gets the {@link CompressedPrivateKey} at the given path. Similar to {@link Config}, an
     * exception is thrown for an invalid value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link CompressedPrivateKey} value at the requested path
     * @throws ConfigException.Missing   if value is absent or null
     * @throws ConfigException.WrongType if value is not convertible to a {@link CompressedPrivateKey}
     */
    @SuppressWarnings({ "java:S1192" })
    public static CompressedPrivateKey getPrivateKey(final Config config, final String path) {
        try {
            final String stringValue = config.getString(path);
            return CompressedPrivateKey.of(stringValue);
        }
        catch (final CryptoException | IllegalArgumentException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "compressed private key", "invalid-value: " + e.getMessage());
        }
    }

    /**
     * Gets the {@link Path} at the given path.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link Path} value at the requested path
     * @throws ConfigException if value at path is invalid
     */
    public static Path getPath(final Config config, final String path) {
        try {
            return Paths.get(config.getString(path));
        }
        catch (final InvalidPathException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "path", "invalid: " + e.getMessage());
        }
    }

    /**
     * Gets the short at the given path. Similar to {@link Config}, an exception is thrown for an
     * out-of-range value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the short value at the requested path
     * @throws ConfigException.Missing   if value is absent or null
     * @throws ConfigException.WrongType if value is not convertible to a short
     */
    public static short getShort(final Config config, final String path) {
        final int integerValue = config.getInt(path);
        if (integerValue > Short.MAX_VALUE || integerValue < Short.MIN_VALUE) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "short", "out-of-range-value " + integerValue);
        }

        return (short) integerValue;
    }

    /**
     * @throws ConfigException if value at path is invalid
     */
    public static List<Endpoint> getEndpointList(final Config config, final String path) {
        final List<String> stringListValue = config.getStringList(path);
        final List<Endpoint> endpointList = new ArrayList<>();
        try {
            for (final String stringValue : stringListValue) {
                endpointList.add(Endpoint.of(stringValue));
            }
        }
        catch (final IllegalArgumentException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "url", "invalid-value: " + e.getMessage());
        }
        return endpointList;
    }

    /**
     * @throws ConfigException if value at path is invalid
     */
    @SuppressWarnings("unchecked")
    public static Class<ChannelInitializer<SocketChannel>> getChannelInitializer(final Config config,
                                                                                 final String path) {
        final String className = config.getString(path);
        try {
            return (Class<ChannelInitializer<SocketChannel>>) Class.forName(className);
        }
        catch (final ClassNotFoundException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "socket channel", "class-not-found: " + e.getMessage());
        }
    }

    /**
     * @throws ConfigException if value at path is invalid
     */
    public static Set<DrasylPlugin> getPlugins(final Config config, final String path) {
        final Set<DrasylPlugin> plugins = new HashSet<>();

        for (final Map.Entry<String, ConfigValue> entry : config.getObject(path).entrySet()) {
            final String clazzName = entry.getKey();
            /*
             * Here a key is intentionally used and immediately deleted. atPath() could throw an
             * exception if the class name contains a $ character for an inner class.
             */
            final Config pluginConfig = entry.getValue().atKey("plugin").getConfig("plugin"); // NOSONAR

            if (pluginConfig.getBoolean("enabled")) {
                final DrasylPlugin plugin = initiatePlugin(path, clazzName, pluginConfig);
                plugins.add(plugin);
            }
        }
        return plugins;
    }

    @SuppressWarnings({ "java:S1192" })
    private static DrasylPlugin initiatePlugin(final String path,
                                               final String clazzName,
                                               final Config pluginConfig) {
        try {
            @SuppressWarnings("unchecked") final Class<? extends DrasylPlugin> clazz = (Class<? extends DrasylPlugin>) Class.forName(clazzName);
            final Constructor<? extends DrasylPlugin> constructor = clazz.getConstructor(Config.class);
            constructor.setAccessible(true); // NOSONAR

            return constructor.newInstance(pluginConfig);
        }
        catch (final ClassNotFoundException e) {
            throw new ConfigException.WrongType(pluginConfig.origin(), path, "plugin", "class-not-found: " + e.getMessage());
        }
        catch (final NoSuchMethodException e) {
            throw new ConfigException.WrongType(pluginConfig.origin(), path, "plugin", "no-such-method: " + e.getMessage());
        }
        catch (final IllegalAccessException e) {
            throw new ConfigException.WrongType(pluginConfig.origin(), path, "plugin", "illegal-access: " + e.getMessage());
        }
        catch (final InstantiationException e) {
            throw new ConfigException.WrongType(pluginConfig.origin(), path, "plugin", "instantiation: " + e.getMessage());
        }
        catch (final InvocationTargetException e) {
            throw new ConfigException.WrongType(pluginConfig.origin(), path, "plugin", "invocation-target: " + e.getTargetException().getMessage());
        }
    }

    /**
     * @throws ConfigException if value at path is invalid
     */
    public static InetAddress getInetAddress(final Config config, final String path) {
        final String stringValue = config.getString(path);
        try {
            return InetAddress.getByName(stringValue);
        }
        catch (final UnknownHostException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "inet address", "unknown-host: " + e.getMessage());
        }
    }

    /**
     * Gets the {@link URI} at the given path.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link URI} value at the requested path
     * @throws ConfigException if value at path is invalid
     */
    public static URI getURI(final Config config, final String path) {
        final String stringValue = config.getString(path);
        try {
            return new URI(stringValue);
        }
        catch (final NullPointerException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "uri", "null");
        }
        catch (final URISyntaxException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "inet address", "violates RFC 2396: " + e.getMessage());
        }
    }

    @SuppressWarnings({ "java:S107" })
    DrasylConfig(final int networkId,
                 final ProofOfWork identityProofOfWork,
                 final CompressedPublicKey identityPublicKey,
                 final CompressedPrivateKey identityPrivateKey,
                 final Path identityPath,
                 final InetAddress serverBindHost,
                 final boolean serverEnabled,
                 final int serverBindPort,
                 final int flushBufferSize,
                 final boolean serverSSLEnabled,
                 final Set<String> serverSSLProtocols,
                 final Duration serverHandshakeTimeout,
                 final Set<Endpoint> serverEndpoints,
                 final Class<? extends ChannelInitializer<SocketChannel>> serverChannelInitializer,
                 final boolean serverExposeEnabled,
                 final int messageMaxContentLength,
                 final short messageHopLimit,
                 final Duration messageComposedMessageTransferTimeout,
                 final boolean superPeerEnabled,
                 final Set<Endpoint> superPeerEndpoints,
                 final List<Duration> superPeerRetryDelays,
                 final Duration superPeerHandshakeTimeout,
                 final Class<? extends ChannelInitializer<SocketChannel>> superPeerChannelInitializer,
                 final short superPeerIdleRetries,
                 final Duration superPeerIdleTimeout,
                 final boolean intraVmDiscoveryEnabled,
                 final boolean localHostDiscoveryEnabled,
                 final Path localHostDiscoveryPath,
                 final Duration localHostDiscoveryLeaseTime, final boolean directConnectionsEnabled,
                 final int directConnectionsMaxConcurrentConnections,
                 final List<Duration> directConnectionsRetryDelays,
                 final Duration directConnectionsHandshakeTimeout,
                 final Class<? extends ChannelInitializer<SocketChannel>> directConnectionsChannelInitializer,
                 final short directConnectionsIdleRetries,
                 final Duration directConnectionsIdleTimeout,
                 final boolean monitoringEnabled,
                 final URI monitoringInfluxUri,
                 final String monitoringInfluxUser,
                 final String monitoringInfluxPassword,
                 final String monitoringInfluxDatabase,
                 final Duration monitoringInfluxReportingFrequency,
                 final Set<DrasylPlugin> pluginSet,
                 final List<String> marshallingInboundAllowedTypes,
                 final boolean marshallingInboundAllowAllPrimitives,
                 final boolean marshallingInboundAllowArrayOfDefinedTypes,
                 final List<String> marshallingInboundAllowedPackages,
                 final List<String> marshallingOutboundAllowedTypes,
                 final boolean marshallingOutboundAllowAllPrimitives,
                 final boolean marshallingOutboundAllowArrayOfDefinedTypes,
                 final List<String> marshallingOutboundAllowedPackages) {
        this.networkId = networkId;
        this.identityProofOfWork = identityProofOfWork;
        this.identityPublicKey = identityPublicKey;
        this.identityPrivateKey = identityPrivateKey;
        this.identityPath = identityPath;
        this.serverBindHost = serverBindHost;
        this.serverEnabled = serverEnabled;
        this.serverBindPort = serverBindPort;
        this.flushBufferSize = flushBufferSize;
        this.serverSSLEnabled = serverSSLEnabled;
        this.serverSSLProtocols = serverSSLProtocols;
        this.serverHandshakeTimeout = serverHandshakeTimeout;
        this.serverEndpoints = serverEndpoints;
        this.serverChannelInitializer = serverChannelInitializer;
        this.serverExposeEnabled = serverExposeEnabled;
        this.messageMaxContentLength = messageMaxContentLength;
        this.messageHopLimit = messageHopLimit;
        this.messageComposedMessageTransferTimeout = messageComposedMessageTransferTimeout;
        this.superPeerEnabled = superPeerEnabled;
        this.superPeerEndpoints = superPeerEndpoints;
        this.superPeerRetryDelays = superPeerRetryDelays;
        this.superPeerHandshakeTimeout = superPeerHandshakeTimeout;
        this.superPeerChannelInitializer = superPeerChannelInitializer;
        this.superPeerIdleRetries = superPeerIdleRetries;
        this.superPeerIdleTimeout = superPeerIdleTimeout;
        this.intraVmDiscoveryEnabled = intraVmDiscoveryEnabled;
        this.localHostDiscoveryEnabled = localHostDiscoveryEnabled;
        this.localHostDiscoveryPath = localHostDiscoveryPath;
        this.localHostDiscoveryLeaseTime = localHostDiscoveryLeaseTime;
        this.directConnectionsEnabled = directConnectionsEnabled;
        this.directConnectionsMaxConcurrentConnections = directConnectionsMaxConcurrentConnections;
        this.directConnectionsRetryDelays = directConnectionsRetryDelays;
        this.directConnectionsHandshakeTimeout = directConnectionsHandshakeTimeout;
        this.directConnectionsChannelInitializer = directConnectionsChannelInitializer;
        this.directConnectionsIdleRetries = directConnectionsIdleRetries;
        this.directConnectionsIdleTimeout = directConnectionsIdleTimeout;
        this.monitoringEnabled = monitoringEnabled;
        this.monitoringInfluxUri = monitoringInfluxUri;
        this.monitoringInfluxUser = monitoringInfluxUser;
        this.monitoringInfluxPassword = monitoringInfluxPassword;
        this.monitoringInfluxDatabase = monitoringInfluxDatabase;
        this.monitoringInfluxReportingFrequency = monitoringInfluxReportingFrequency;
        this.pluginSet = pluginSet;
        this.marshallingInboundAllowedTypes = marshallingInboundAllowedTypes;
        this.marshallingInboundAllowAllPrimitives = marshallingInboundAllowAllPrimitives;
        this.marshallingInboundAllowArrayOfDefinedTypes = marshallingInboundAllowArrayOfDefinedTypes;
        this.marshallingInboundAllowedPackages = marshallingInboundAllowedPackages;
        this.marshallingOutboundAllowedTypes = marshallingOutboundAllowedTypes;
        this.marshallingOutboundAllowAllPrimitives = marshallingOutboundAllowAllPrimitives;
        this.marshallingOutboundAllowArrayOfDefinedTypes = marshallingOutboundAllowArrayOfDefinedTypes;
        this.marshallingOutboundAllowedPackages = marshallingOutboundAllowedPackages;
    }

    public int getNetworkId() {
        return networkId;
    }

    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    public URI getMonitoringInfluxUri() {
        return monitoringInfluxUri;
    }

    public String getMonitoringInfluxUser() {
        return monitoringInfluxUser;
    }

    public String getMonitoringInfluxPassword() {
        return monitoringInfluxPassword;
    }

    public String getMonitoringInfluxDatabase() {
        return monitoringInfluxDatabase;
    }

    public Duration getMonitoringInfluxReportingFrequency() {
        return monitoringInfluxReportingFrequency;
    }

    public InetAddress getServerBindHost() {
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

    public Duration getSuperPeerHandshakeTimeout() {
        return superPeerHandshakeTimeout;
    }

    public int getFlushBufferSize() {
        return flushBufferSize;
    }

    public Set<String> getServerSSLProtocols() {
        return serverSSLProtocols;
    }

    public Duration getServerHandshakeTimeout() {
        return serverHandshakeTimeout;
    }

    public Set<Endpoint> getServerEndpoints() {
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

    public Set<Endpoint> getSuperPeerEndpoints() {
        return superPeerEndpoints;
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

    public boolean isLocalHostDiscoveryEnabled() {
        return localHostDiscoveryEnabled;
    }

    public Path getLocalHostDiscoveryPath() {
        return localHostDiscoveryPath;
    }

    public Duration getLocalHostDiscoveryLeaseTime() {
        return localHostDiscoveryLeaseTime;
    }

    public boolean areDirectConnectionsEnabled() {
        return directConnectionsEnabled;
    }

    public int getDirectConnectionsMaxConcurrentConnections() {
        return directConnectionsMaxConcurrentConnections;
    }

    public Duration getDirectConnectionsIdleTimeout() {
        return directConnectionsIdleTimeout;
    }

    public short getDirectConnectionsIdleRetries() {
        return directConnectionsIdleRetries;
    }

    public Duration getDirectConnectionsHandshakeTimeout() {
        return directConnectionsHandshakeTimeout;
    }

    public List<Duration> getDirectConnectionsRetryDelays() {
        return directConnectionsRetryDelays;
    }

    public Set<DrasylPlugin> getPlugins() {
        return pluginSet;
    }

    public Class<? extends ChannelInitializer<SocketChannel>> getDirectConnectionsChannelInitializer
            () {
        return getSuperPeerChannelInitializer();
    }

    public List<String> getMarshallingInboundAllowedTypes() {
        return marshallingInboundAllowedTypes;
    }

    public boolean isMarshallingInboundAllowAllPrimitives() {
        return marshallingInboundAllowAllPrimitives;
    }

    public boolean isMarshallingInboundAllowArrayOfDefinedTypes() {
        return marshallingInboundAllowArrayOfDefinedTypes;
    }

    public List<String> getMarshallingInboundAllowedPackages() {
        return marshallingInboundAllowedPackages;
    }

    public List<String> getMarshallingOutboundAllowedTypes() {
        return marshallingOutboundAllowedTypes;
    }

    public boolean isMarshallingOutboundAllowAllPrimitives() {
        return marshallingOutboundAllowAllPrimitives;
    }

    public boolean isMarshallingOutboundAllowArrayOfDefinedTypes() {
        return marshallingOutboundAllowArrayOfDefinedTypes;
    }

    public List<String> getMarshallingOutboundAllowedPackages() {
        return marshallingOutboundAllowedPackages;
    }

    public boolean isServerExposeEnabled() {
        return serverExposeEnabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(networkId, identityProofOfWork, identityPublicKey, identityPrivateKey,
                identityPath, serverBindHost, serverEnabled, serverBindPort, flushBufferSize,
                serverSSLEnabled, serverSSLProtocols, serverHandshakeTimeout,
                serverEndpoints, serverChannelInitializer,
                serverExposeEnabled, messageMaxContentLength, messageHopLimit,
                messageComposedMessageTransferTimeout, superPeerEnabled, superPeerEndpoints,
                superPeerRetryDelays, superPeerHandshakeTimeout, superPeerChannelInitializer,
                superPeerIdleRetries, superPeerIdleTimeout, intraVmDiscoveryEnabled,
                localHostDiscoveryEnabled, localHostDiscoveryPath, localHostDiscoveryLeaseTime,
                directConnectionsEnabled, directConnectionsMaxConcurrentConnections,
                directConnectionsRetryDelays, directConnectionsHandshakeTimeout,
                directConnectionsChannelInitializer, directConnectionsIdleRetries,
                directConnectionsIdleTimeout, monitoringEnabled, monitoringInfluxUri,
                monitoringInfluxUser, monitoringInfluxPassword, monitoringInfluxDatabase,
                monitoringInfluxReportingFrequency, pluginSet, marshallingInboundAllowedTypes,
                marshallingInboundAllowAllPrimitives, marshallingInboundAllowArrayOfDefinedTypes,
                marshallingInboundAllowedPackages, marshallingOutboundAllowedTypes,
                marshallingOutboundAllowAllPrimitives, marshallingOutboundAllowArrayOfDefinedTypes,
                marshallingOutboundAllowedPackages);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DrasylConfig that = (DrasylConfig) o;
        return networkId == that.networkId &&
                serverEnabled == that.serverEnabled &&
                serverBindPort == that.serverBindPort &&
                flushBufferSize == that.flushBufferSize &&
                serverSSLEnabled == that.serverSSLEnabled &&
                messageMaxContentLength == that.messageMaxContentLength &&
                messageHopLimit == that.messageHopLimit &&
                superPeerEnabled == that.superPeerEnabled &&
                superPeerIdleRetries == that.superPeerIdleRetries &&
                intraVmDiscoveryEnabled == that.intraVmDiscoveryEnabled &&
                localHostDiscoveryEnabled == that.localHostDiscoveryEnabled &&
                directConnectionsEnabled == that.directConnectionsEnabled &&
                directConnectionsMaxConcurrentConnections == that.directConnectionsMaxConcurrentConnections &&
                directConnectionsIdleRetries == that.directConnectionsIdleRetries &&
                monitoringEnabled == that.monitoringEnabled &&
                Objects.equals(identityProofOfWork, that.identityProofOfWork) &&
                Objects.equals(identityPublicKey, that.identityPublicKey) &&
                Objects.equals(identityPrivateKey, that.identityPrivateKey) &&
                Objects.equals(identityPath, that.identityPath) &&
                Objects.equals(serverBindHost, that.serverBindHost) &&
                Objects.equals(serverSSLProtocols, that.serverSSLProtocols) &&
                Objects.equals(serverHandshakeTimeout, that.serverHandshakeTimeout) &&
                Objects.equals(serverEndpoints, that.serverEndpoints) &&
                Objects.equals(serverChannelInitializer, that.serverChannelInitializer) &&
                Objects.equals(serverExposeEnabled, that.serverExposeEnabled) &&
                Objects.equals(messageComposedMessageTransferTimeout, that.messageComposedMessageTransferTimeout) &&
                Objects.equals(superPeerEndpoints, that.superPeerEndpoints) &&
                Objects.equals(superPeerRetryDelays, that.superPeerRetryDelays) &&
                Objects.equals(superPeerHandshakeTimeout, that.superPeerHandshakeTimeout) &&
                Objects.equals(superPeerChannelInitializer, that.superPeerChannelInitializer) &&
                Objects.equals(superPeerIdleTimeout, that.superPeerIdleTimeout) &&
                Objects.equals(localHostDiscoveryPath, that.localHostDiscoveryPath) &&
                Objects.equals(localHostDiscoveryLeaseTime, that.localHostDiscoveryLeaseTime) &&
                Objects.equals(directConnectionsRetryDelays, that.directConnectionsRetryDelays) &&
                Objects.equals(directConnectionsHandshakeTimeout, that.directConnectionsHandshakeTimeout) &&
                Objects.equals(directConnectionsChannelInitializer, that.directConnectionsChannelInitializer) &&
                Objects.equals(directConnectionsIdleTimeout, that.directConnectionsIdleTimeout) &&
                Objects.equals(monitoringInfluxUri, that.monitoringInfluxUri) &&
                Objects.equals(monitoringInfluxUser, that.monitoringInfluxUser) &&
                Objects.equals(monitoringInfluxPassword, that.monitoringInfluxPassword) &&
                Objects.equals(monitoringInfluxDatabase, that.monitoringInfluxDatabase) &&
                Objects.equals(monitoringInfluxReportingFrequency, that.monitoringInfluxReportingFrequency) &&
                Objects.equals(pluginSet, that.pluginSet) &&
                Objects.equals(marshallingInboundAllowedTypes, that.marshallingInboundAllowedTypes) &&
                Objects.equals(marshallingInboundAllowAllPrimitives, that.marshallingInboundAllowAllPrimitives) &&
                Objects.equals(marshallingInboundAllowArrayOfDefinedTypes, that.marshallingInboundAllowArrayOfDefinedTypes) &&
                Objects.equals(marshallingInboundAllowedPackages, that.marshallingInboundAllowedPackages) &&
                Objects.equals(marshallingOutboundAllowedTypes, that.marshallingOutboundAllowedTypes) &&
                Objects.equals(marshallingOutboundAllowAllPrimitives, that.marshallingOutboundAllowAllPrimitives) &&
                Objects.equals(marshallingOutboundAllowArrayOfDefinedTypes, that.marshallingOutboundAllowArrayOfDefinedTypes) &&
                Objects.equals(marshallingOutboundAllowedPackages, that.marshallingOutboundAllowedPackages);
    }

    @Override
    public String toString() {
        return "DrasylConfig{" +
                "networkId=" + networkId +
                ", identityProofOfWork=" + identityProofOfWork +
                ", identityPublicKey=" + identityPublicKey +
                ", identityPrivateKey=" + maskSecret(identityPrivateKey) +
                ", identityPath=" + identityPath +
                ", serverBindHost='" + serverBindHost + '\'' +
                ", serverEnabled=" + serverEnabled +
                ", serverBindPort=" + serverBindPort +
                ", flushBufferSize=" + flushBufferSize +
                ", serverSSLEnabled=" + serverSSLEnabled +
                ", serverSSLProtocols=" + serverSSLProtocols +
                ", serverHandshakeTimeout=" + serverHandshakeTimeout +
                ", serverEndpoints=" + serverEndpoints +
                ", serverChannelInitializer=" + serverChannelInitializer +
                ", serverExposeEnabled=" + serverExposeEnabled +
                ", messageMaxContentLength=" + messageMaxContentLength +
                ", messageHopLimit=" + messageHopLimit +
                ", messageComposedMessageTransferTimeout=" + messageComposedMessageTransferTimeout +
                ", superPeerEnabled=" + superPeerEnabled +
                ", superPeerEndpoints=" + superPeerEndpoints +
                ", superPeerRetryDelays=" + superPeerRetryDelays +
                ", superPeerHandshakeTimeout=" + superPeerHandshakeTimeout +
                ", superPeerChannelInitializer=" + superPeerChannelInitializer +
                ", superPeerIdleRetries=" + superPeerIdleRetries +
                ", superPeerIdleTimeout=" + superPeerIdleTimeout +
                ", intraVmDiscoveryEnabled=" + intraVmDiscoveryEnabled +
                ", localHostDiscoveryEnabled=" + localHostDiscoveryEnabled +
                ", localHostDiscoveryPath=" + localHostDiscoveryPath +
                ", localHostDiscoveryLeaseTime=" + localHostDiscoveryLeaseTime +
                ", directConnectionsEnabled=" + directConnectionsEnabled +
                ", directConnectionsMaxConcurrentConnections=" + directConnectionsMaxConcurrentConnections +
                ", directConnectionsRetryDelays=" + directConnectionsRetryDelays +
                ", directConnectionsHandshakeTimeout=" + directConnectionsHandshakeTimeout +
                ", directConnectionsChannelInitializer=" + directConnectionsChannelInitializer +
                ", directConnectionsIdleRetries=" + directConnectionsIdleRetries +
                ", directConnectionsIdleTimeout=" + directConnectionsIdleTimeout +
                ", monitoringEnabled=" + monitoringEnabled +
                ", monitoringInfluxUri='" + monitoringInfluxUri + '\'' +
                ", monitoringInfluxUser='" + monitoringInfluxUser + '\'' +
                ", monitoringInfluxPassword='" + maskSecret(identityPrivateKey) + '\'' +
                ", monitoringInfluxDatabase='" + monitoringInfluxDatabase + '\'' +
                ", monitoringInfluxReportingFrequency=" + monitoringInfluxReportingFrequency +
                ", plugins=" + pluginSet +
                ", marshallingInboundAllowedTypes=" + marshallingInboundAllowedTypes +
                ", marshallingInboundAllowAllPrimitives=" + marshallingInboundAllowAllPrimitives +
                ", marshallingInboundAllowArrayOfDefinedTypes=" + marshallingInboundAllowArrayOfDefinedTypes +
                ", marshallingInboundAllowedPackages=" + marshallingInboundAllowedPackages +
                ", marshallingOutboundAllowedTypes=" + marshallingOutboundAllowedTypes +
                ", marshallingOutboundAllowAllPrimitives=" + marshallingOutboundAllowAllPrimitives +
                ", marshallingOutboundAllowArrayOfDefinedTypes=" + marshallingOutboundAllowArrayOfDefinedTypes +
                ", marshallingOutboundAllowedPackages=" + marshallingOutboundAllowedPackages +
                '}';
    }

    /**
     * Parses a file into a Config instance as with
     *
     * @param file the file to parse
     * @return the parsed configuration
     * @throws ConfigException on IO or parse errors
     */
    public static DrasylConfig parseFile(final File file) {
        return new DrasylConfig(ConfigFactory.parseFile(file).withFallback(ConfigFactory.load()));
    }

    /**
     * Parses a file into a Config instance as with
     *
     * @param s string to parse
     * @return the parsed configuration
     * @throws ConfigException on IO or parse errors
     */
    public static DrasylConfig parseString(final String s) {
        return new DrasylConfig(ConfigFactory.parseString(s).withFallback(ConfigFactory.load()));
    }

    /**
     * Creates a new builder to build a custom {@link DrasylConfig}. The built configuration is
     * derived from the default configuration. The builder must be finalized by calling {@link
     * Builder#build()} to create the resulting {@link DrasylConfig}.
     *
     * @return the new builder
     */
    public static Builder newBuilder() {
        return newBuilder(DEFAULT);
    }

    public static Builder newBuilder(final DrasylConfig config) {
        return new Builder(
                config.networkId,
                config.identityProofOfWork,
                config.identityPublicKey,
                config.identityPrivateKey,
                config.identityPath,
                config.serverBindHost,
                config.serverEnabled,
                config.serverBindPort,
                config.flushBufferSize,
                config.serverSSLEnabled,
                config.serverSSLProtocols,
                config.serverHandshakeTimeout,
                config.serverEndpoints,
                config.serverChannelInitializer,
                config.serverExposeEnabled,
                config.messageMaxContentLength,
                config.messageHopLimit,
                config.messageComposedMessageTransferTimeout,
                config.superPeerEnabled,
                config.superPeerEndpoints,
                config.superPeerRetryDelays,
                config.superPeerHandshakeTimeout,
                config.superPeerChannelInitializer,
                config.superPeerIdleRetries,
                config.superPeerIdleTimeout,
                config.intraVmDiscoveryEnabled,
                config.localHostDiscoveryEnabled,
                config.localHostDiscoveryPath,
                config.localHostDiscoveryLeaseTime,
                config.directConnectionsEnabled,
                config.directConnectionsMaxConcurrentConnections,
                config.directConnectionsRetryDelays,
                config.directConnectionsHandshakeTimeout,
                config.directConnectionsChannelInitializer,
                config.directConnectionsIdleRetries,
                config.directConnectionsIdleTimeout,
                config.monitoringEnabled,
                config.monitoringInfluxUri,
                config.monitoringInfluxUser,
                config.monitoringInfluxPassword,
                config.monitoringInfluxDatabase,
                config.monitoringInfluxReportingFrequency,
                config.pluginSet,
                config.marshallingInboundAllowedTypes,
                config.marshallingInboundAllowAllPrimitives,
                config.marshallingInboundAllowArrayOfDefinedTypes,
                config.marshallingInboundAllowedPackages,
                config.marshallingOutboundAllowedTypes,
                config.marshallingOutboundAllowAllPrimitives,
                config.marshallingOutboundAllowArrayOfDefinedTypes,
                config.marshallingOutboundAllowedPackages
        );
    }

    /**
     * Builder class to create a {@link DrasylConfig} with custom values.
     */
    public static final class Builder {
        //======================================= Config Values ========================================
        private int networkId;
        private ProofOfWork identityProofOfWork;
        private CompressedPublicKey identityPublicKey;
        private CompressedPrivateKey identityPrivateKey;
        private Path identityPath;
        private InetAddress serverBindHost;
        private boolean serverEnabled;
        private int serverBindPort;
        private int flushBufferSize;
        private boolean serverSSLEnabled;
        private Set<String> serverSSLProtocols;
        private Duration serverHandshakeTimeout;
        private Set<Endpoint> serverEndpoints;
        private Class<? extends ChannelInitializer<SocketChannel>> serverChannelInitializer;
        private boolean serverExposeEnabled;
        private int messageMaxContentLength;
        private short messageHopLimit;
        private Duration messageComposedMessageTransferTimeout;
        private boolean superPeerEnabled;
        private Set<Endpoint> superPeerEndpoints;
        private List<Duration> superPeerRetryDelays;
        private Duration superPeerHandshakeTimeout;
        private Class<? extends ChannelInitializer<SocketChannel>> superPeerChannelInitializer;
        private short superPeerIdleRetries;
        private Duration superPeerIdleTimeout;
        private boolean intraVmDiscoveryEnabled;
        private boolean localHostDiscoveryEnabled;
        private final Path localHostDiscoveryPath;
        private Duration localHostDiscoveryLeaseTime;
        private boolean directConnectionsEnabled;
        private int directConnectionsMaxConcurrentConnections;
        private List<Duration> directConnectionsRetryDelays;
        private Duration directConnectionsHandshakeTimeout;
        private Class<? extends ChannelInitializer<SocketChannel>> directConnectionsChannelInitializer;
        private short directConnectionsIdleRetries;
        private Duration directConnectionsIdleTimeout;
        private boolean monitoringEnabled;
        private URI monitoringInfluxUri;
        private String monitoringInfluxUser;
        private String monitoringInfluxPassword;
        private String monitoringInfluxDatabase;
        private Duration monitoringInfluxReportingFrequency;
        private Set<DrasylPlugin> plugins;
        private List<String> marshallingInboundAllowedTypes;
        private boolean marshallingInboundAllowAllPrimitives;
        private boolean marshallingInboundAllowArrayOfDefinedTypes;
        private List<String> marshallingInboundAllowedPackages;
        private List<String> marshallingOutboundAllowedTypes;
        private boolean marshallingOutboundAllowAllPrimitives;
        private boolean marshallingOutboundAllowArrayOfDefinedTypes;
        private List<String> marshallingOutboundAllowedPackages;

        @SuppressWarnings({ "java:S107" })
        private Builder(final int networkId,
                        final ProofOfWork identityProofOfWork,
                        final CompressedPublicKey identityPublicKey,
                        final CompressedPrivateKey identityPrivateKey,
                        final Path identityPath,
                        final InetAddress serverBindHost,
                        final boolean serverEnabled,
                        final int serverBindPort,
                        final int flushBufferSize,
                        final boolean serverSSLEnabled,
                        final Set<String> serverSSLProtocols,
                        final Duration serverHandshakeTimeout,
                        final Set<Endpoint> serverEndpoints,
                        final Class<? extends ChannelInitializer<SocketChannel>> serverChannelInitializer,
                        final boolean serverExposeEnabled,
                        final int messageMaxContentLength,
                        final short messageHopLimit,
                        final Duration messageComposedMessageTransferTimeout,
                        final boolean superPeerEnabled,
                        final Set<Endpoint> superPeerEndpoints,
                        final List<Duration> superPeerRetryDelays,
                        final Duration superPeerHandshakeTimeout,
                        final Class<? extends ChannelInitializer<SocketChannel>> superPeerChannelInitializer,
                        final short superPeerIdleRetries,
                        final Duration superPeerIdleTimeout,
                        final boolean intraVmDiscoveryEnabled,
                        final boolean localHostDiscoveryEnabled,
                        final Path localHostDiscoveryPath,
                        final Duration localHostDiscoveryLeaseTime,
                        final boolean directConnectionsEnabled,
                        final int directConnectionsMaxConcurrentConnections,
                        final List<Duration> directConnectionsRetryDelays,
                        final Duration directConnectionsHandshakeTimeout,
                        final Class<? extends ChannelInitializer<SocketChannel>> directConnectionsChannelInitializer,
                        final short directConnectionsIdleRetries,
                        final Duration directConnectionsIdleTimeout,
                        final boolean monitoringEnabled,
                        final URI monitoringInfluxUri,
                        final String monitoringInfluxUser,
                        final String monitoringInfluxPassword,
                        final String monitoringInfluxDatabase,
                        final Duration monitoringInfluxReportingFrequency,
                        final Set<DrasylPlugin> plugins,
                        final List<String> marshallingInboundAllowedTypes,
                        final boolean marshallingInboundAllowAllPrimitives,
                        final boolean marshallingInboundAllowArrayOfDefinedTypes,
                        final List<String> marshallingInboundAllowedPackages,
                        final List<String> marshallingOutboundAllowedTypes,
                        final boolean marshallingOutboundAllowAllPrimitives,
                        final boolean marshallingOutboundAllowArrayOfDefinedTypes,
                        final List<String> marshallingOutboundAllowedPackages) {
            this.networkId = networkId;
            this.identityProofOfWork = identityProofOfWork;
            this.identityPublicKey = identityPublicKey;
            this.identityPrivateKey = identityPrivateKey;
            this.identityPath = identityPath;
            this.serverBindHost = serverBindHost;
            this.serverEnabled = serverEnabled;
            this.serverBindPort = serverBindPort;
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
            this.superPeerRetryDelays = superPeerRetryDelays;
            this.superPeerHandshakeTimeout = superPeerHandshakeTimeout;
            this.superPeerChannelInitializer = superPeerChannelInitializer;
            this.superPeerIdleRetries = superPeerIdleRetries;
            this.superPeerIdleTimeout = superPeerIdleTimeout;
            this.intraVmDiscoveryEnabled = intraVmDiscoveryEnabled;
            this.localHostDiscoveryEnabled = localHostDiscoveryEnabled;
            this.localHostDiscoveryPath = localHostDiscoveryPath;
            this.localHostDiscoveryLeaseTime = localHostDiscoveryLeaseTime;
            this.directConnectionsEnabled = directConnectionsEnabled;
            this.directConnectionsMaxConcurrentConnections = directConnectionsMaxConcurrentConnections;
            this.directConnectionsRetryDelays = directConnectionsRetryDelays;
            this.directConnectionsHandshakeTimeout = directConnectionsHandshakeTimeout;
            this.directConnectionsChannelInitializer = directConnectionsChannelInitializer;
            this.directConnectionsIdleRetries = directConnectionsIdleRetries;
            this.directConnectionsIdleTimeout = directConnectionsIdleTimeout;
            this.monitoringEnabled = monitoringEnabled;
            this.monitoringInfluxUri = monitoringInfluxUri;
            this.monitoringInfluxUser = monitoringInfluxUser;
            this.monitoringInfluxPassword = monitoringInfluxPassword;
            this.monitoringInfluxDatabase = monitoringInfluxDatabase;
            this.monitoringInfluxReportingFrequency = monitoringInfluxReportingFrequency;
            this.plugins = plugins;
            this.marshallingInboundAllowedTypes = marshallingInboundAllowedTypes;
            this.marshallingInboundAllowAllPrimitives = marshallingInboundAllowAllPrimitives;
            this.marshallingInboundAllowArrayOfDefinedTypes = marshallingInboundAllowArrayOfDefinedTypes;
            this.marshallingInboundAllowedPackages = marshallingInboundAllowedPackages;
            this.marshallingOutboundAllowedTypes = marshallingOutboundAllowedTypes;
            this.marshallingOutboundAllowAllPrimitives = marshallingOutboundAllowAllPrimitives;
            this.marshallingOutboundAllowArrayOfDefinedTypes = marshallingOutboundAllowArrayOfDefinedTypes;
            this.marshallingOutboundAllowedPackages = marshallingOutboundAllowedPackages;
            this.serverExposeEnabled = serverExposeEnabled;
        }

        public Builder networkId(final int networkId) {
            this.networkId = networkId;
            return this;
        }

        public Builder identityProofOfWork(final ProofOfWork identityProofOfWork) {
            this.identityProofOfWork = identityProofOfWork;
            return this;
        }

        public Builder identityPublicKey(final CompressedPublicKey identityPublicKey) {
            this.identityPublicKey = identityPublicKey;
            return this;
        }

        public Builder identityPrivateKey(final CompressedPrivateKey identityPrivateKey) {
            this.identityPrivateKey = identityPrivateKey;
            return this;
        }

        public Builder identityPath(final Path identityPath) {
            this.identityPath = identityPath;
            return this;
        }

        public Builder serverBindHost(final InetAddress serverBindHost) {
            this.serverBindHost = serverBindHost;
            return this;
        }

        public Builder serverEnabled(final boolean serverEnabled) {
            this.serverEnabled = serverEnabled;
            return this;
        }

        public Builder serverBindPort(final int serverBindPort) {
            this.serverBindPort = serverBindPort;
            return this;
        }

        public Builder flushBufferSize(final int flushBufferSize) {
            this.flushBufferSize = flushBufferSize;
            return this;
        }

        public Builder serverSSLEnabled(final boolean serverSSLEnabled) {
            this.serverSSLEnabled = serverSSLEnabled;
            return this;
        }

        public Builder serverSSLProtocols(final Set<String> serverSSLProtocols) {
            this.serverSSLProtocols = serverSSLProtocols;
            return this;
        }

        public Builder serverHandshakeTimeout(final Duration serverHandshakeTimeout) {
            this.serverHandshakeTimeout = serverHandshakeTimeout;
            return this;
        }

        public Builder serverEndpoints(final Set<Endpoint> serverEndpoints) {
            this.serverEndpoints = serverEndpoints;
            return this;
        }

        public Builder serverChannelInitializer(final Class<? extends ChannelInitializer<SocketChannel>> serverChannelInitializer) {
            this.serverChannelInitializer = serverChannelInitializer;
            return this;
        }

        public Builder messageMaxContentLength(final int messageMaxContentLength) {
            this.messageMaxContentLength = messageMaxContentLength;
            return this;
        }

        public Builder messageHopLimit(final short messageHopLimit) {
            this.messageHopLimit = messageHopLimit;
            return this;
        }

        public Builder messageComposedMessageTransferTimeout(final Duration composedMessageTransferTimeout) {
            this.messageComposedMessageTransferTimeout = composedMessageTransferTimeout;
            return this;
        }

        public Builder superPeerEnabled(final boolean superPeerEnabled) {
            this.superPeerEnabled = superPeerEnabled;
            return this;
        }

        public Builder superPeerEndpoints(final Set<Endpoint> superPeerEndpoints) {
            this.superPeerEndpoints = superPeerEndpoints;
            return this;
        }

        public Builder superPeerRetryDelays(final List<Duration> superPeerRetryDelays) {
            this.superPeerRetryDelays = superPeerRetryDelays;
            return this;
        }

        public Builder superPeerHandshakeTimeout(final Duration superPeerHandshakeTimeout) {
            this.superPeerHandshakeTimeout = superPeerHandshakeTimeout;
            return this;
        }

        public Builder superPeerChannelInitializer(final Class<? extends ChannelInitializer<SocketChannel>> superPeerChannelInitializer) {
            this.superPeerChannelInitializer = superPeerChannelInitializer;
            return this;
        }

        public Builder superPeerIdleRetries(final short superPeerIdleRetries) {
            this.superPeerIdleRetries = superPeerIdleRetries;
            return this;
        }

        public Builder superPeerIdleTimeout(final Duration superPeerIdleTimeout) {
            this.superPeerIdleTimeout = superPeerIdleTimeout;
            return this;
        }

        public Builder intraVmDiscoveryEnabled(final boolean intraVmDiscoveryEnabled) {
            this.intraVmDiscoveryEnabled = intraVmDiscoveryEnabled;
            return this;
        }

        public Builder localHostDiscoveryEnabled(final boolean localHostDiscoveryEnabled) {
            this.localHostDiscoveryEnabled = localHostDiscoveryEnabled;
            return this;
        }

        public Builder localHostDiscoveryLeaseTime(final Duration localHostDiscoveryLeaseTime) {
            this.localHostDiscoveryLeaseTime = localHostDiscoveryLeaseTime;
            return this;
        }

        public Builder directConnectionsEnabled(final boolean directConnectionsEnabled) {
            this.directConnectionsEnabled = directConnectionsEnabled;
            return this;
        }

        public Builder directConnectionsMaxConcurrentConnections(final int directConnectionsMaxConcurrentConnections) {
            this.directConnectionsMaxConcurrentConnections = directConnectionsMaxConcurrentConnections;
            return this;
        }

        public Builder directConnectionsRetryDelays(final List<Duration> directConnectionsRetryDelays) {
            this.directConnectionsRetryDelays = directConnectionsRetryDelays;
            return this;
        }

        public Builder directConnectionsHandshakeTimeout(final Duration directConnectionsHandshakeTimeout) {
            this.directConnectionsHandshakeTimeout = directConnectionsHandshakeTimeout;
            return this;
        }

        public Builder directConnectionsChannelInitializer(final Class<? extends ChannelInitializer<SocketChannel>> directConnectionsChannelInitializer) {
            this.directConnectionsChannelInitializer = directConnectionsChannelInitializer;
            return this;
        }

        public Builder directConnectionsIdleRetries(final short directConnectionsIdleRetries) {
            this.directConnectionsIdleRetries = directConnectionsIdleRetries;
            return this;
        }

        public Builder directConnectionsIdleTimeout(final Duration directConnectionsIdleTimeout) {
            this.directConnectionsIdleTimeout = directConnectionsIdleTimeout;
            return this;
        }

        public Builder monitoringEnabled(final boolean monitoringEnabled) {
            this.monitoringEnabled = monitoringEnabled;
            return this;
        }

        public Builder monitoringInfluxUri(final URI monitoringInfluxUri) {
            this.monitoringInfluxUri = monitoringInfluxUri;
            return this;
        }

        public Builder monitoringInfluxUser(final String monitoringInfluxUser) {
            this.monitoringInfluxUser = monitoringInfluxUser;
            return this;
        }

        public Builder monitoringInfluxPassword(final String monitoringInfluxPassword) {
            this.monitoringInfluxPassword = monitoringInfluxPassword;
            return this;
        }

        public Builder monitoringInfluxDatabase(final String monitoringInfluxDatabase) {
            this.monitoringInfluxDatabase = monitoringInfluxDatabase;
            return this;
        }

        public Builder monitoringInfluxReportingFrequency(final Duration monitoringInfluxReportingFrequency) {
            this.monitoringInfluxReportingFrequency = monitoringInfluxReportingFrequency;
            return this;
        }

        public Builder plugins(final Set<DrasylPlugin> plugins) {
            this.plugins = plugins;
            return this;
        }

        public Builder marshallingInboundAllowedTypes(final List<String> marshallingInboundAllowedTypes) {
            this.marshallingInboundAllowedTypes = marshallingInboundAllowedTypes;
            return this;
        }

        public Builder marshallingInboundAllowAllPrimitives(final boolean marshallingInboundAllowAllPrimitives) {
            this.marshallingInboundAllowAllPrimitives = marshallingInboundAllowAllPrimitives;
            return this;
        }

        public Builder marshallingInboundAllowArrayOfDefinedTypes(final boolean marshallingInboundAllowArrayOfDefinedTypes) {
            this.marshallingInboundAllowArrayOfDefinedTypes = marshallingInboundAllowArrayOfDefinedTypes;
            return this;
        }

        public Builder marshallingInboundAllowedPackages(final List<String> marshallingInboundAllowedPackages) {
            this.marshallingInboundAllowedPackages = marshallingInboundAllowedPackages;
            return this;
        }

        public Builder marshallingOutboundAllowedTypes(final List<String> marshallingOutboundAllowedTypes) {
            this.marshallingOutboundAllowedTypes = marshallingOutboundAllowedTypes;
            return this;
        }

        public Builder marshallingOutboundAllowAllPrimitives(final boolean marshallingOutboundAllowAllPrimitives) {
            this.marshallingOutboundAllowAllPrimitives = marshallingOutboundAllowAllPrimitives;
            return this;
        }

        public Builder marshallingOutboundAllowArrayOfDefinedTypes(final boolean marshallingOutboundAllowArrayOfDefinedTypes) {
            this.marshallingOutboundAllowArrayOfDefinedTypes = marshallingOutboundAllowArrayOfDefinedTypes;
            return this;
        }

        public Builder marshallingOutboundAllowedPackages(final List<String> marshallingOutboundAllowedPackages) {
            this.marshallingOutboundAllowedPackages = marshallingOutboundAllowedPackages;
            return this;
        }

        public Builder serverExposeEnabled(final boolean serverExposeEnabled) {
            this.serverExposeEnabled = serverExposeEnabled;
            return this;
        }

        /**
         * Creates a {@link DrasylConfig} from the builder.
         *
         * @return the {@link DrasylConfig} created from the builder
         */
        public DrasylConfig build() {
            return new DrasylConfig(networkId, identityProofOfWork, identityPublicKey,
                    identityPrivateKey, identityPath, serverBindHost, serverEnabled, serverBindPort,
                    flushBufferSize, serverSSLEnabled,
                    serverSSLProtocols, serverHandshakeTimeout, serverEndpoints,
                    serverChannelInitializer, serverExposeEnabled, messageMaxContentLength, messageHopLimit,
                    messageComposedMessageTransferTimeout, superPeerEnabled, superPeerEndpoints,
                    superPeerRetryDelays, superPeerHandshakeTimeout,
                    superPeerChannelInitializer, superPeerIdleRetries, superPeerIdleTimeout,
                    intraVmDiscoveryEnabled, localHostDiscoveryEnabled, localHostDiscoveryPath,
                    localHostDiscoveryLeaseTime, directConnectionsEnabled,
                    directConnectionsMaxConcurrentConnections, directConnectionsRetryDelays,
                    directConnectionsHandshakeTimeout, directConnectionsChannelInitializer,
                    directConnectionsIdleRetries, directConnectionsIdleTimeout, monitoringEnabled,
                    monitoringInfluxUri, monitoringInfluxUser, monitoringInfluxPassword,
                    monitoringInfluxDatabase, monitoringInfluxReportingFrequency,
                    plugins, marshallingInboundAllowedTypes, marshallingInboundAllowAllPrimitives,
                    marshallingInboundAllowArrayOfDefinedTypes, marshallingInboundAllowedPackages,
                    marshallingOutboundAllowedTypes, marshallingOutboundAllowAllPrimitives,
                    marshallingOutboundAllowArrayOfDefinedTypes, marshallingOutboundAllowedPackages
            );
        }
    }
}