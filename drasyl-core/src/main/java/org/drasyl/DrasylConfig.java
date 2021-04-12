/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.plugin.DrasylPlugin;
import org.drasyl.serialization.Serializer;
import org.drasyl.util.MaskedString;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;
import static org.drasyl.util.InetSocketAddressUtil.socketAddressFromString;

/**
 * This class represents the configuration for a {@link DrasylNode}. For example, it defines the
 * identity and the Super Peer.
 * <p>
 * This is an immutable object.
 */
public class DrasylConfig {
    static final DrasylConfig DEFAULT = new DrasylConfig(ConfigFactory.defaultReference());
    //======================================== Config Paths ========================================
    public static final String NETWORK_ID = "drasyl.network.id";
    public static final String IDENTITY_PROOF_OF_WORK = "drasyl.identity.proof-of-work";
    public static final String IDENTITY_PUBLIC_KEY = "drasyl.identity.public-key";
    public static final String IDENTITY_PRIVATE_KEY = "drasyl.identity.private-key";
    public static final String IDENTITY_PATH = "drasyl.identity.path";
    public static final String MESSAGE_BUFFER_SIZE = "drasyl.message.buffer-size";
    public static final String INTRA_VM_DISCOVERY_ENABLED = "drasyl.intra-vm-discovery.enabled";
    public static final String REMOTE_ENABLED = "drasyl.remote.enabled";
    public static final String REMOTE_BIND_HOST = "drasyl.remote.bind-host";
    public static final String REMOTE_BIND_PORT = "drasyl.remote.bind-port";
    public static final String REMOTE_ENDPOINTS = "drasyl.remote.endpoints";
    public static final String REMOTE_EXPOSE_ENABLED = "drasyl.remote.expose.enabled";
    public static final String REMOTE_PING_INTERVAL = "drasyl.remote.ping.interval";
    public static final String REMOTE_PING_TIMEOUT = "drasyl.remote.ping.timeout";
    public static final String REMOTE_PING_COMMUNICATION_TIMEOUT = "drasyl.remote.ping.communication-timeout";
    public static final String REMOTE_PING_MAX_PEERS = "drasyl.remote.ping.max-peers";
    public static final String REMOTE_UNITE_MIN_INTERVAL = "drasyl.remote.unite.min-interval";
    public static final String REMOTE_SUPER_PEER_ENABLED = "drasyl.remote.super-peer.enabled";
    public static final String REMOTE_SUPER_PEER_ENDPOINTS = "drasyl.remote.super-peer.endpoints";
    public static final String REMOTE_STATIC_ROUTES = "drasyl.remote.static-routes";
    public static final String REMOTE_MESSAGE_MTU = "drasyl.remote.message.mtu";
    public static final String REMOTE_MESSAGE_MAX_CONTENT_LENGTH = "drasyl.remote.message.max-content-length";
    public static final String REMOTE_MESSAGE_HOP_LIMIT = "drasyl.remote.message.hop-limit";
    public static final String REMOTE_MESSAGE_ARM_ENABLED = "drasyl.remote.message.arm.enabled";
    public static final String REMOTE_MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT = "drasyl.remote.message.composed-message-transfer-timeout";
    public static final String REMOTE_LOCAL_HOST_DISCOVERY_ENABLED = "drasyl.remote.local-host-discovery.enabled";
    public static final String REMOTE_LOCAL_HOST_DISCOVERY_PATH = "drasyl.remote.local-host-discovery.path";
    public static final String REMOTE_LOCAL_HOST_DISCOVERY_LEASE_TIME = "drasyl.remote.local-host-discovery.lease-time";
    public static final String REMOTE_LOCAL_HOST_DISCOVERY_WATCH_ENABLED = "drasyl.remote.local-host-discovery.watch.enabled";
    public static final String REMOTE_LOCAL_NETWORK_DISCOVERY_ENABLED = "drasyl.remote.local-network-discovery.enabled";
    public static final String REMOTE_TCP_FALLBACK_ENABLED = "drasyl.remote.tcp-fallback.enabled";
    public static final String REMOTE_TCP_FALLBACK_SERVER_BIND_HOST = "drasyl.remote.tcp-fallback.server.bind-host";
    public static final String REMOTE_TCP_FALLBACK_SERVER_BIND_PORT = "drasyl.remote.tcp-fallback.server.bind-port";
    public static final String REMOTE_TCP_FALLBACK_CLIENT_TIMEOUT = "drasyl.remote.tcp-fallback.client.timeout";
    public static final String REMOTE_TCP_FALLBACK_CLIENT_ADDRESS = "drasyl.remote.tcp-fallback.client.address";
    public static final String MONITORING_ENABLED = "drasyl.monitoring.enabled";
    public static final String MONITORING_HOST_TAG = "drasyl.monitoring.host-tag";
    public static final String MONITORING_INFLUX_URI = "drasyl.monitoring.influx.uri";
    public static final String MONITORING_INFLUX_USER = "drasyl.monitoring.influx.user";
    public static final String MONITORING_INFLUX_PASSWORD = "drasyl.monitoring.influx.password";
    public static final String MONITORING_INFLUX_DATABASE = "drasyl.monitoring.influx.database";
    public static final String MONITORING_INFLUX_REPORTING_FREQUENCY = "drasyl.monitoring.influx.reporting-frequency";
    public static final String PLUGINS = "drasyl.plugins";
    public static final String SERIALIZATION_SERIALIZERS = "drasyl.serialization.serializers";
    public static final String SERIALIZATION_BINDINGS_INBOUND = "drasyl.serialization.bindings.inbound";
    public static final String SERIALIZATION_BINDINGS_OUTBOUND = "drasyl.serialization.bindings.outbound";
    //======================================= Config Values ========================================
    private final int networkId;
    private final ProofOfWork identityProofOfWork;
    private final CompressedPublicKey identityPublicKey;
    private final CompressedPrivateKey identityPrivateKey;
    private final Path identityPath;
    private final int messageBufferSize;
    private final boolean intraVmDiscoveryEnabled;
    private final InetAddress remoteBindHost;
    private final boolean remoteEnabled;
    private final int remoteBindPort;
    private final Duration remotePingInterval;
    private final Duration remotePingTimeout;
    private final Duration remotePingCommunicationTimeout;
    private final Duration remoteUniteMinInterval;
    private final int remotePingMaxPeers;
    private final Set<Endpoint> remoteEndpoints;
    private final boolean remoteExposeEnabled;
    private final int remoteMessageMtu;
    private final int remoteMessageMaxContentLength;
    private final byte remoteMessageHopLimit;
    private final boolean remoteMessageArmEnabled;
    private final Duration remoteMessageComposedMessageTransferTimeout;
    private final boolean remoteSuperPeerEnabled;
    private final Set<Endpoint> remoteSuperPeerEndpoints;
    private final Map<CompressedPublicKey, InetSocketAddressWrapper> remoteStaticRoutes;
    private final boolean remoteLocalHostDiscoveryEnabled;
    private final Path remoteLocalHostDiscoveryPath;
    private final Duration remoteLocalHostDiscoveryLeaseTime;
    private final boolean remoteLocalHostDiscoveryWatchEnabled;
    private final boolean remoteLocalNetworkDiscoveryEnabled;
    private final boolean remoteTcpFallbackEnabled;
    private final InetAddress remoteTcpFallbackServerBindHost;
    private final int remoteTcpFallbackServerBindPort;
    private final Duration remoteTcpFallbackClientTimeout;
    private final InetSocketAddress remoteTcpFallbackClientAddress;
    private final boolean monitoringEnabled;
    private final String monitoringHostTag;
    private final URI monitoringInfluxUri;
    private final String monitoringInfluxUser;
    private final MaskedString monitoringInfluxPassword;
    private final String monitoringInfluxDatabase;
    private final Duration monitoringInfluxReportingFrequency;
    private final Set<DrasylPlugin> pluginSet;
    private final Map<String, Serializer> serializationSerializers;
    private final Map<Class<?>, String> serializationsBindingsInbound;
    private final Map<Class<?>, String> serializationsBindingsOutbound;

    public DrasylConfig() {
        this(ConfigFactory.load());
    }

    /**
     * Creates a new config for a drasyl node.
     *
     * @param config config to be loaded
     * @throws DrasylConfigException if config is invalid
     */
    @SuppressWarnings({ "java:S138", "java:S1192", "java:S1541", "java:S3776" })
    public DrasylConfig(final Config config) {
        try {
            config.checkValid(ConfigFactory.defaultReference(), "drasyl");

            this.networkId = config.getInt(NETWORK_ID);

            // init identity config
            if (!config.getIsNull(IDENTITY_PROOF_OF_WORK)) {
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

            this.messageBufferSize = config.getInt(MESSAGE_BUFFER_SIZE);
            if (this.messageBufferSize < 0) {
                throw new DrasylConfigException(MESSAGE_BUFFER_SIZE, "Must be a non-negative value.");
            }

            this.intraVmDiscoveryEnabled = config.getBoolean(INTRA_VM_DISCOVERY_ENABLED);

            // Init remote config
            this.remoteEnabled = config.getBoolean(REMOTE_ENABLED);
            this.remoteBindHost = getInetAddress(config, REMOTE_BIND_HOST);
            this.remoteBindPort = config.getInt(REMOTE_BIND_PORT);
            this.remoteEndpoints = Set.copyOf(getEndpointList(config, REMOTE_ENDPOINTS));
            this.remoteExposeEnabled = config.getBoolean(REMOTE_EXPOSE_ENABLED);
            this.remotePingInterval = config.getDuration(REMOTE_PING_INTERVAL);
            if (this.remotePingInterval.isNegative() || this.remotePingInterval.isZero()) {
                throw new DrasylConfigException(REMOTE_PING_INTERVAL, "Must be a positive value.");
            }
            this.remotePingTimeout = config.getDuration(REMOTE_PING_TIMEOUT);
            if (this.remotePingTimeout.isNegative() || this.remotePingTimeout.isZero()) {
                throw new DrasylConfigException(REMOTE_PING_TIMEOUT, "Must be a positive value.");
            }
            this.remotePingCommunicationTimeout = config.getDuration(REMOTE_PING_COMMUNICATION_TIMEOUT);
            if (this.remotePingCommunicationTimeout.isNegative() || this.remotePingCommunicationTimeout.isZero()) {
                throw new DrasylConfigException(REMOTE_PING_COMMUNICATION_TIMEOUT, "Must be a positive value.");
            }
            this.remotePingMaxPeers = config.getInt(REMOTE_PING_MAX_PEERS);
            if (this.remotePingMaxPeers < 0) {
                throw new DrasylConfigException(REMOTE_PING_COMMUNICATION_TIMEOUT, "Must be a non-negative value.");
            }
            this.remoteUniteMinInterval = config.getDuration(REMOTE_UNITE_MIN_INTERVAL);
            if (this.remoteUniteMinInterval.isNegative()) {
                throw new DrasylConfigException(REMOTE_UNITE_MIN_INTERVAL, "Must be a non-negative value.");
            }
            this.remoteSuperPeerEnabled = config.getBoolean(REMOTE_SUPER_PEER_ENABLED);
            this.remoteSuperPeerEndpoints = Set.copyOf(getEndpointList(config, REMOTE_SUPER_PEER_ENDPOINTS));
            if (remoteSuperPeerEnabled) {
                for (final Endpoint endpoint : remoteSuperPeerEndpoints) {
                    if (endpoint.getNetworkId() != null && endpoint.getNetworkId() != networkId) {
                        throw new DrasylConfigException(REMOTE_SUPER_PEER_ENDPOINTS, "super peer's network id `" + endpoint.getNetworkId() + "` does not match your network id `" + networkId + "`: " + endpoint);
                    }
                }
            }
            this.remoteStaticRoutes = getStaticRoutes(config, REMOTE_STATIC_ROUTES);
            this.remoteLocalHostDiscoveryEnabled = config.getBoolean(REMOTE_LOCAL_HOST_DISCOVERY_ENABLED);
            if (isNullOrEmpty(config.getString(REMOTE_LOCAL_HOST_DISCOVERY_PATH))) {
                this.remoteLocalHostDiscoveryPath = Paths.get(System.getProperty("java.io.tmpdir"), "drasyl-discovery");
            }
            else {
                this.remoteLocalHostDiscoveryPath = getPath(config, REMOTE_LOCAL_HOST_DISCOVERY_PATH);
            }
            this.remoteLocalHostDiscoveryLeaseTime = config.getDuration(REMOTE_LOCAL_HOST_DISCOVERY_LEASE_TIME);
            if (this.remoteLocalHostDiscoveryLeaseTime.isNegative() || this.remoteLocalHostDiscoveryLeaseTime.isZero()) {
                throw new DrasylConfigException(REMOTE_LOCAL_HOST_DISCOVERY_LEASE_TIME, "Must be a positive value.");
            }
            this.remoteLocalHostDiscoveryWatchEnabled = config.getBoolean(REMOTE_LOCAL_HOST_DISCOVERY_WATCH_ENABLED);
            this.remoteLocalNetworkDiscoveryEnabled = config.getBoolean(REMOTE_LOCAL_NETWORK_DISCOVERY_ENABLED);
            this.remoteMessageMtu = (int) Math.min(config.getMemorySize(REMOTE_MESSAGE_MTU).toBytes(), Integer.MAX_VALUE);
            if (this.remoteMessageMtu < 1) {
                throw new DrasylConfigException(REMOTE_MESSAGE_MTU, "Must be a positive value.");
            }
            this.remoteMessageMaxContentLength = (int) Math.min(config.getMemorySize(REMOTE_MESSAGE_MAX_CONTENT_LENGTH).toBytes(), Integer.MAX_VALUE);
            if (this.remoteMessageMaxContentLength < 0) {
                throw new DrasylConfigException(REMOTE_MESSAGE_MAX_CONTENT_LENGTH, "Must be a non-negative value.");
            }
            this.remoteMessageComposedMessageTransferTimeout = config.getDuration(REMOTE_MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT);
            if (this.remoteMessageComposedMessageTransferTimeout.isNegative() || this.remoteMessageComposedMessageTransferTimeout.isZero()) {
                throw new DrasylConfigException(REMOTE_MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT, "Must be a positive value.");
            }
            this.remoteMessageHopLimit = getByte(config, REMOTE_MESSAGE_HOP_LIMIT);
            this.remoteMessageArmEnabled = config.getBoolean(REMOTE_MESSAGE_ARM_ENABLED);
            this.remoteTcpFallbackEnabled = config.getBoolean(REMOTE_TCP_FALLBACK_ENABLED);
            this.remoteTcpFallbackServerBindHost = getInetAddress(config, REMOTE_TCP_FALLBACK_SERVER_BIND_HOST);
            this.remoteTcpFallbackServerBindPort = config.getInt(REMOTE_TCP_FALLBACK_SERVER_BIND_PORT);
            this.remoteTcpFallbackClientTimeout = config.getDuration(REMOTE_TCP_FALLBACK_CLIENT_TIMEOUT);
            this.remoteTcpFallbackClientAddress = getInetSocketAddress(config, REMOTE_TCP_FALLBACK_CLIENT_ADDRESS);

            // Init monitoring config
            monitoringEnabled = config.getBoolean(MONITORING_ENABLED);
            monitoringHostTag = config.getString(MONITORING_HOST_TAG);
            monitoringInfluxUri = getURI(config, MONITORING_INFLUX_URI);
            monitoringInfluxUser = config.getString(MONITORING_INFLUX_USER);
            monitoringInfluxPassword = MaskedString.of(config.getString(MONITORING_INFLUX_PASSWORD));
            monitoringInfluxDatabase = config.getString(MONITORING_INFLUX_DATABASE);
            monitoringInfluxReportingFrequency = config.getDuration(MONITORING_INFLUX_REPORTING_FREQUENCY);

            // Load plugins
            this.pluginSet = Set.copyOf(getPlugins(config, PLUGINS));

            // Load serialization config
            this.serializationSerializers = Map.copyOf(getSerializationSerializers(config, SERIALIZATION_SERIALIZERS));
            this.serializationsBindingsInbound = Map.copyOf(getSerializationBindings(config, SERIALIZATION_BINDINGS_INBOUND, serializationSerializers.keySet()));
            this.serializationsBindingsOutbound = Map.copyOf(getSerializationBindings(config, SERIALIZATION_BINDINGS_OUTBOUND, serializationSerializers.keySet()));
        }
        catch (final ConfigException e) {
            throw new DrasylConfigException(e);
        }
    }

    @SuppressWarnings({ "java:S107", "java:S2384" })
    DrasylConfig(final int networkId,
                 final ProofOfWork identityProofOfWork,
                 final CompressedPublicKey identityPublicKey,
                 final CompressedPrivateKey identityPrivateKey,
                 final Path identityPath,
                 final int messageBufferSize,
                 final boolean intraVmDiscoveryEnabled,
                 final InetAddress remoteBindHost,
                 final boolean remoteEnabled,
                 final int remoteBindPort,
                 final Duration remotePingInterval,
                 final Duration remotePingTimeout,
                 final Duration remotePingCommunicationTimeout,
                 final Duration remoteUniteMinInterval,
                 final int remotePingMaxPeers,
                 final Set<Endpoint> remoteEndpoints,
                 final boolean remoteExposeEnabled,
                 final boolean remoteSuperPeerEnabled,
                 final Set<Endpoint> remoteSuperPeerEndpoints,
                 final Map<CompressedPublicKey, InetSocketAddressWrapper> remoteStaticRoutes,
                 final int remoteMessageMaxContentLength,
                 final byte remoteMessageHopLimit,
                 final boolean remoteMessageArmEnabled,
                 final Duration remoteMessageComposedMessageTransferTimeout,
                 final int remoteMessageMtu,
                 final boolean remoteLocalHostDiscoveryEnabled,
                 final Path remoteLocalHostDiscoveryPath,
                 final Duration remoteLocalHostDiscoveryLeaseTime,
                 final boolean remoteLocalHostDiscoveryWatchEnabled,
                 final boolean remoteLocalNetworkDiscoveryEnabled,
                 final boolean remoteTcpFallbackEnabled,
                 final InetAddress remoteTcpFallbackServerBindHost,
                 final int remoteTcpFallbackServerBindPort,
                 final Duration remoteTcpFallbackClientTimeout,
                 final InetSocketAddress remoteTcpFallbackClientAddress,
                 final boolean monitoringEnabled,
                 final String monitoringHostTag,
                 final URI monitoringInfluxUri,
                 final String monitoringInfluxUser,
                 final MaskedString monitoringInfluxPassword,
                 final String monitoringInfluxDatabase,
                 final Duration monitoringInfluxReportingFrequency,
                 final Set<DrasylPlugin> pluginSet,
                 final Map<String, Serializer> serializationSerializers,
                 final Map<Class<?>, String> serializationsBindingsInbound,
                 final Map<Class<?>, String> serializationsBindingsOutbound) {
        this.networkId = networkId;
        this.identityProofOfWork = identityProofOfWork;
        this.identityPublicKey = identityPublicKey;
        this.identityPrivateKey = identityPrivateKey;
        this.identityPath = identityPath;
        this.messageBufferSize = messageBufferSize;
        this.intraVmDiscoveryEnabled = intraVmDiscoveryEnabled;
        this.remoteBindHost = requireNonNull(remoteBindHost);
        this.remoteEnabled = remoteEnabled;
        this.remoteBindPort = remoteBindPort;
        this.remotePingInterval = remotePingInterval;
        this.remotePingTimeout = remotePingTimeout;
        this.remotePingCommunicationTimeout = requireNonNull(remotePingCommunicationTimeout);
        this.remoteUniteMinInterval = requireNonNull(remoteUniteMinInterval);
        this.remotePingMaxPeers = remotePingMaxPeers;
        this.remoteEndpoints = requireNonNull(remoteEndpoints);
        this.remoteExposeEnabled = remoteExposeEnabled;
        this.remoteSuperPeerEnabled = remoteSuperPeerEnabled;
        this.remoteSuperPeerEndpoints = requireNonNull(remoteSuperPeerEndpoints);
        this.remoteStaticRoutes = requireNonNull(remoteStaticRoutes);
        this.remoteMessageMtu = remoteMessageMtu;
        this.remoteMessageMaxContentLength = remoteMessageMaxContentLength;
        this.remoteMessageHopLimit = remoteMessageHopLimit;
        this.remoteMessageArmEnabled = remoteMessageArmEnabled;
        this.remoteMessageComposedMessageTransferTimeout = requireNonNull(remoteMessageComposedMessageTransferTimeout);
        this.remoteLocalHostDiscoveryEnabled = remoteLocalHostDiscoveryEnabled;
        this.remoteLocalHostDiscoveryPath = requireNonNull(remoteLocalHostDiscoveryPath);
        this.remoteLocalHostDiscoveryLeaseTime = requireNonNull(remoteLocalHostDiscoveryLeaseTime);
        this.remoteLocalHostDiscoveryWatchEnabled = remoteLocalHostDiscoveryWatchEnabled;
        this.remoteLocalNetworkDiscoveryEnabled = remoteLocalNetworkDiscoveryEnabled;
        this.remoteTcpFallbackEnabled = remoteTcpFallbackEnabled;
        this.remoteTcpFallbackServerBindHost = requireNonNull(remoteTcpFallbackServerBindHost);
        this.remoteTcpFallbackServerBindPort = remoteTcpFallbackServerBindPort;
        this.remoteTcpFallbackClientTimeout = remoteTcpFallbackClientTimeout;
        this.remoteTcpFallbackClientAddress = requireNonNull(remoteTcpFallbackClientAddress);
        this.monitoringEnabled = monitoringEnabled;
        this.monitoringHostTag = requireNonNull(monitoringHostTag);
        this.monitoringInfluxUri = requireNonNull(monitoringInfluxUri);
        this.monitoringInfluxUser = requireNonNull(monitoringInfluxUser);
        this.monitoringInfluxPassword = requireNonNull(monitoringInfluxPassword);
        this.monitoringInfluxDatabase = requireNonNull(monitoringInfluxDatabase);
        this.monitoringInfluxReportingFrequency = requireNonNull(monitoringInfluxReportingFrequency);
        this.pluginSet = requireNonNull(pluginSet);
        this.serializationSerializers = requireNonNull(serializationSerializers);
        this.serializationsBindingsInbound = requireNonNull(serializationsBindingsInbound);
        this.serializationsBindingsOutbound = requireNonNull(serializationsBindingsOutbound);
    }

    /**
     * Gets the {@link ProofOfWork} at the given path. Similar to {@link Config}, an exception is
     * thrown for an invalid value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link ProofOfWork} value at the requested path
     * @throws DrasylConfigException if value is absent or null
     */
    @SuppressWarnings({ "java:S1192" })
    public static ProofOfWork getProofOfWork(final Config config, final String path) {
        try {
            final int intValue = config.getInt(path);
            return ProofOfWork.of(intValue);
        }
        catch (final ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * Gets the {@link CompressedPublicKey} at the given path. Similar to {@link Config}, an
     * exception is thrown for an invalid value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link CompressedPublicKey} value at the requested path
     * @throws DrasylConfigException if value is not convertible to a {@link CompressedPublicKey}
     */
    @SuppressWarnings({ "java:S1192" })
    public static CompressedPublicKey getPublicKey(final Config config, final String path) {
        try {
            final String stringValue = config.getString(path);
            return CompressedPublicKey.of(stringValue);
        }
        catch (final IllegalArgumentException | ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * Gets the {@link CompressedPrivateKey} at the given path. Similar to {@link Config}, an
     * exception is thrown for an invalid value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link CompressedPrivateKey} value at the requested path
     * @throws DrasylConfigException if value is not convertible to a {@link CompressedPrivateKey}
     */
    @SuppressWarnings({ "java:S1192" })
    public static CompressedPrivateKey getPrivateKey(final Config config, final String path) {
        try {
            final String stringValue = config.getString(path);
            return CompressedPrivateKey.of(stringValue);
        }
        catch (final ConfigException | IllegalArgumentException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * Gets the {@link Path} at the given path.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link Path} value at the requested path
     * @throws DrasylConfigException if value at path is invalid
     */
    public static Path getPath(final Config config, final String path) {
        try {
            return Paths.get(config.getString(path));
        }
        catch (final InvalidPathException | ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * @throws DrasylConfigException if value at path is invalid
     */
    public static InetAddress getInetAddress(final Config config, final String path) {
        try {
            final String stringValue = config.getString(path);
            return InetAddress.getByName(stringValue);
        }
        catch (final UnknownHostException | ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * @throws DrasylConfigException if value at path is invalid
     */
    public static List<Endpoint> getEndpointList(final Config config, final String path) {
        try {
            final List<String> stringListValue = config.getStringList(path);
            final List<Endpoint> endpointList = new ArrayList<>();
            for (final String stringValue : stringListValue) {
                endpointList.add(Endpoint.of(stringValue));
            }
            return endpointList;
        }
        catch (final IllegalArgumentException | ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * @throws DrasylConfigException if value at path is invalid
     */
    public static Endpoint getEndpoint(final Config config, final String path) {
        try {
            final String stringValue = config.getString(path);
            return Endpoint.of(stringValue);
        }
        catch (final IllegalArgumentException | ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * Gets the short at the given path. Similar to {@link Config}, an exception is thrown for an
     * out-of-range value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the short value at the requested path
     * @throws DrasylConfigException if value is not convertible to a short
     */
    @SuppressWarnings("unused")
    public static short getShort(final Config config, final String path) {
        try {
            final int integerValue = config.getInt(path);
            if (integerValue > Short.MAX_VALUE || integerValue < Short.MIN_VALUE) {
                throw new DrasylConfigException(path, "value is out of range: " + integerValue);
            }

            return (short) integerValue;
        }
        catch (final ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * Gets the byte at the given path. Similar to {@link Config}, an exception is thrown for an
     * out-of-range value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the byte value at the requested path
     * @throws DrasylConfigException if value is not convertible to a short
     */
    public static byte getByte(final Config config, final String path) {
        try {
            final int integerValue = config.getInt(path);
            if (integerValue > Byte.MAX_VALUE || integerValue < Byte.MIN_VALUE) {
                throw new DrasylConfigException(path, "value is out of range: " + integerValue);
            }

            return (byte) integerValue;
        }
        catch (final ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * Gets the {@link URI} at the given path.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link URI} value at the requested path
     * @throws DrasylConfigException if value at path is invalid
     */
    public static URI getURI(final Config config, final String path) {
        try {
            final String stringValue = config.getString(path);
            return new URI(stringValue);
        }
        catch (final URISyntaxException | ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * @throws DrasylConfigException if value at path is invalid
     */
    public static Set<DrasylPlugin> getPlugins(final Config config, final String path) {
        try {
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
        catch (final ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
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
        catch (final ClassNotFoundException | NoSuchMethodException | InstantiationException | InvocationTargetException | IllegalAccessException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    @SuppressWarnings("SameParameterValue")
    public static Map<String, Serializer> getSerializationSerializers(final Config config,
                                                                      final String path) {
        try {
            final Map<String, Serializer> serializers = new HashMap<>();

            for (final Map.Entry<String, ConfigValue> entry : config.getObject(path).entrySet()) {
                final String binding = entry.getKey();
                final String clazzName = entry.getValue().atKey("clazzName").getString("clazzName");
                final Serializer serializer = initiateSerializer(path, clazzName);
                serializers.put(binding, serializer);
            }
            return serializers;
        }
        catch (final ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    @SuppressWarnings({ "java:S1192" })
    private static Serializer initiateSerializer(final String path,
                                                 final String clazzName) {
        try {
            @SuppressWarnings("unchecked") final Class<? extends Serializer> clazz = (Class<? extends Serializer>) Class.forName(clazzName);
            final Constructor<? extends Serializer> constructor = clazz.getConstructor();
            constructor.setAccessible(true); // NOSONAR

            return constructor.newInstance();
        }
        catch (final ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    public static Map<Class<?>, String> getSerializationBindings(final Config config,
                                                                 final String path,
                                                                 final Collection<String> serializers) {
        try {
            final Map<Class<?>, String> bindings = new HashMap<>();
            for (final Map.Entry<String, ConfigValue> entry : config.getObject(path).entrySet()) {
                final String binding = entry.getKey();
                final String serializer = entry.getValue().atKey("serializer").getString("serializer");
                if (serializers.contains(serializer)) {
                    final Class<?> bindingClazz = Class.forName(binding);
                    bindings.put(bindingClazz, serializer);
                }
                else {
                    throw new DrasylConfigException(path, "serializer not found: " + serializer);
                }
            }
            return bindings;
        }
        catch (final ClassNotFoundException | ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * @throws DrasylConfigException if value at path is invalid
     */
    public static InetSocketAddress getInetSocketAddress(final Config config, final String path) {
        try {
            final String stringValue = config.getString(path);
            final URI uriValue = new URI("my://" + stringValue);
            final String host = uriValue.getHost();
            final int port = uriValue.getPort();

            return InetSocketAddress.createUnresolved(host, port);
        }
        catch (final URISyntaxException | IllegalArgumentException | ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * @throws DrasylConfigException if value at path is invalid
     */
    public static Map<CompressedPublicKey, InetSocketAddressWrapper> getStaticRoutes(final Config config,
                                                                                     final String path) {
        try {
            final Map<CompressedPublicKey, InetSocketAddressWrapper> routes = new HashMap<>();
            for (final Map.Entry<String, ConfigValue> entry : config.getObject(path).entrySet()) {
                final CompressedPublicKey publicKey = CompressedPublicKey.of(entry.getKey());
                final InetSocketAddressWrapper address = socketAddressFromString(entry.getValue().atKey("address").getString("address"));

                routes.put(publicKey, address);
            }
            return routes;
        }
        catch (final IllegalArgumentException | ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * Parses a file into a Config instance as with
     *
     * @param file the file to parse
     * @return the parsed configuration
     * @throws DrasylConfigException on IO or parse errors
     */
    public static DrasylConfig parseFile(final File file) {
        try {
            return new DrasylConfig(ConfigFactory.parseFile(file).withFallback(ConfigFactory.load()));
        }
        catch (final ConfigException e) {
            throw new DrasylConfigException(e);
        }
    }

    /**
     * Parses a file into a Config instance as with
     *
     * @param s string to parse
     * @return the parsed configuration
     * @throws DrasylConfigException on IO or parse errors
     */
    public static DrasylConfig parseString(final String s) {
        try {
            return new DrasylConfig(ConfigFactory.parseString(s).withFallback(ConfigFactory.load()));
        }
        catch (final ConfigException e) {
            throw new DrasylConfigException(e);
        }
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
                config.messageBufferSize,
                config.intraVmDiscoveryEnabled,
                config.remoteBindHost,
                config.remoteEnabled,
                config.remoteBindPort,
                config.remotePingInterval,
                config.remotePingTimeout,
                config.remotePingCommunicationTimeout,
                config.remoteUniteMinInterval,
                config.remotePingMaxPeers,
                config.remoteEndpoints,
                config.remoteExposeEnabled,
                config.remoteSuperPeerEnabled,
                config.remoteSuperPeerEndpoints,
                config.remoteStaticRoutes,
                config.remoteMessageMtu,
                config.remoteMessageMaxContentLength,
                config.remoteMessageComposedMessageTransferTimeout,
                config.remoteMessageHopLimit,
                config.remoteMessageArmEnabled,
                config.remoteLocalHostDiscoveryEnabled,
                config.remoteLocalHostDiscoveryPath,
                config.remoteLocalHostDiscoveryLeaseTime,
                config.remoteLocalHostDiscoveryWatchEnabled,
                config.remoteLocalNetworkDiscoveryEnabled,
                config.remoteTcpFallbackEnabled,
                config.remoteTcpFallbackServerBindHost,
                config.remoteTcpFallbackServerBindPort,
                config.remoteTcpFallbackClientTimeout,
                config.remoteTcpFallbackClientAddress,
                config.monitoringEnabled,
                config.monitoringHostTag,
                config.monitoringInfluxUri,
                config.monitoringInfluxUser,
                config.monitoringInfluxPassword,
                config.monitoringInfluxDatabase,
                config.monitoringInfluxReportingFrequency,
                config.pluginSet,
                config.serializationSerializers,
                config.serializationsBindingsInbound,
                config.serializationsBindingsOutbound
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                networkId,
                identityProofOfWork,
                identityPublicKey,
                identityPrivateKey,
                identityPath,
                messageBufferSize,
                intraVmDiscoveryEnabled,
                remoteBindHost,
                remoteEnabled,
                remoteBindPort,
                remotePingInterval,
                remotePingTimeout,
                remotePingCommunicationTimeout,
                remoteUniteMinInterval,
                remotePingMaxPeers,
                remoteEndpoints,
                remoteExposeEnabled,
                remoteMessageMtu,
                remoteMessageMaxContentLength,
                remoteMessageComposedMessageTransferTimeout,
                remoteMessageHopLimit,
                remoteSuperPeerEnabled,
                remoteSuperPeerEndpoints,
                remoteStaticRoutes,
                remoteLocalHostDiscoveryEnabled,
                remoteLocalHostDiscoveryPath,
                remoteLocalHostDiscoveryLeaseTime,
                remoteLocalHostDiscoveryWatchEnabled,
                monitoringEnabled,
                monitoringHostTag,
                monitoringInfluxUri,
                monitoringInfluxUser,
                monitoringInfluxPassword,
                monitoringInfluxDatabase,
                monitoringInfluxReportingFrequency,
                pluginSet,
                serializationSerializers,
                serializationsBindingsInbound,
                serializationsBindingsOutbound
        );
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
                messageBufferSize == that.messageBufferSize &&
                intraVmDiscoveryEnabled == that.intraVmDiscoveryEnabled &&
                remoteEnabled == that.remoteEnabled &&
                remoteBindPort == that.remoteBindPort &&
                remotePingMaxPeers == that.remotePingMaxPeers &&
                remoteExposeEnabled == that.remoteExposeEnabled &&
                remoteMessageMtu == that.remoteMessageMtu &&
                remoteMessageMaxContentLength == that.remoteMessageMaxContentLength &&
                remoteMessageHopLimit == that.remoteMessageHopLimit &&
                remoteSuperPeerEnabled == that.remoteSuperPeerEnabled &&
                remoteLocalHostDiscoveryEnabled == that.remoteLocalHostDiscoveryEnabled &&
                remoteLocalHostDiscoveryWatchEnabled == that.remoteLocalHostDiscoveryWatchEnabled &&
                remoteLocalNetworkDiscoveryEnabled == that.remoteLocalNetworkDiscoveryEnabled &&
                remoteTcpFallbackEnabled == that.remoteTcpFallbackEnabled &&
                remoteTcpFallbackServerBindPort == that.remoteTcpFallbackServerBindPort &&
                monitoringEnabled == that.monitoringEnabled &&
                Objects.equals(identityProofOfWork, that.identityProofOfWork) &&
                Objects.equals(identityPublicKey, that.identityPublicKey) &&
                Objects.equals(identityPrivateKey, that.identityPrivateKey) &&
                Objects.equals(identityPath, that.identityPath) &&
                Objects.equals(remoteBindHost, that.remoteBindHost) &&
                Objects.equals(remotePingInterval, that.remotePingInterval) &&
                Objects.equals(remotePingTimeout, that.remotePingTimeout) &&
                Objects.equals(remotePingCommunicationTimeout, that.remotePingCommunicationTimeout) &&
                Objects.equals(remoteUniteMinInterval, that.remoteUniteMinInterval) &&
                Objects.equals(remoteEndpoints, that.remoteEndpoints) &&
                Objects.equals(remoteMessageComposedMessageTransferTimeout, that.remoteMessageComposedMessageTransferTimeout) &&
                Objects.equals(remoteSuperPeerEndpoints, that.remoteSuperPeerEndpoints) &&
                Objects.equals(remoteStaticRoutes, that.remoteStaticRoutes) &&
                Objects.equals(remoteLocalHostDiscoveryPath, that.remoteLocalHostDiscoveryPath) &&
                Objects.equals(remoteLocalHostDiscoveryLeaseTime, that.remoteLocalHostDiscoveryLeaseTime) &&
                Objects.equals(remoteTcpFallbackServerBindHost, that.remoteTcpFallbackServerBindHost) &&
                Objects.equals(remoteTcpFallbackClientTimeout, that.remoteTcpFallbackClientTimeout) &&
                Objects.equals(remoteTcpFallbackClientAddress, that.remoteTcpFallbackClientAddress) &&
                Objects.equals(monitoringHostTag, that.monitoringHostTag) &&
                Objects.equals(monitoringInfluxUri, that.monitoringInfluxUri) &&
                Objects.equals(monitoringInfluxUser, that.monitoringInfluxUser) &&
                Objects.equals(monitoringInfluxPassword, that.monitoringInfluxPassword) &&
                Objects.equals(monitoringInfluxDatabase, that.monitoringInfluxDatabase) &&
                Objects.equals(monitoringInfluxReportingFrequency, that.monitoringInfluxReportingFrequency) &&
                Objects.equals(pluginSet, that.pluginSet) &&
                Objects.equals(serializationSerializers, that.serializationSerializers) &&
                Objects.equals(serializationsBindingsInbound, that.serializationsBindingsInbound) &&
                Objects.equals(serializationsBindingsOutbound, that.serializationsBindingsOutbound);
    }

    @Override
    public String toString() {
        return "DrasylConfig{" +
                "networkId=" + networkId +
                ", identityProofOfWork=" + identityProofOfWork +
                ", identityPublicKey=" + identityPublicKey +
                ", identityPrivateKey=" + identityPrivateKey +
                ", identityPath=" + identityPath +
                ", messageBufferSize=" + messageBufferSize +
                ", intraVmDiscoveryEnabled=" + intraVmDiscoveryEnabled +
                ", remoteBindHost=" + remoteBindHost +
                ", remoteEnabled=" + remoteEnabled +
                ", remoteBindPort=" + remoteBindPort +
                ", remotePingInterval=" + remotePingInterval +
                ", remotePingTimeout=" + remotePingTimeout +
                ", remotePingCommunicationTimeout=" + remotePingCommunicationTimeout +
                ", remoteUniteMinInterval=" + remoteUniteMinInterval +
                ", remotePingMaxPeers=" + remotePingMaxPeers +
                ", remoteEndpoints=" + remoteEndpoints +
                ", remoteExposeEnabled=" + remoteExposeEnabled +
                ", remoteMessageMtu=" + remoteMessageMtu +
                ", remoteMessageMaxContentLength=" + remoteMessageMaxContentLength +
                ", remoteMessageComposedMessageTransferTimeout=" + remoteMessageComposedMessageTransferTimeout +
                ", remoteMessageHopLimit=" + remoteMessageHopLimit +
                ", remoteSuperPeerEnabled=" + remoteSuperPeerEnabled +
                ", remoteSuperPeerEndpoints=" + remoteSuperPeerEndpoints +
                ", remoteStaticRoutes=" + remoteStaticRoutes +
                ", remoteLocalHostDiscoveryEnabled=" + remoteLocalHostDiscoveryEnabled +
                ", remoteLocalHostDiscoveryPath=" + remoteLocalHostDiscoveryPath +
                ", remoteLocalHostDiscoveryLeaseTime=" + remoteLocalHostDiscoveryLeaseTime +
                ", remoteLocalHostDiscoveryWatchEnabled=" + remoteLocalHostDiscoveryWatchEnabled +
                ", remoteLocalNetworkDiscoveryEnabled=" + remoteLocalNetworkDiscoveryEnabled +
                ", remoteTcpFallbackEnabled=" + remoteTcpFallbackEnabled +
                ", remoteTcpFallbackServerBindHost=" + remoteTcpFallbackServerBindHost +
                ", remoteTcpFallbackServerBindPort=" + remoteTcpFallbackServerBindPort +
                ", remoteTcpFallbackClientTimeout=" + remoteTcpFallbackClientTimeout +
                ", remoteTcpFallbackClientEndpoints=" + remoteTcpFallbackClientAddress +
                ", monitoringEnabled=" + monitoringEnabled +
                ", monitoringHostTag=" + monitoringHostTag +
                ", monitoringInfluxUri=" + monitoringInfluxUri +
                ", monitoringInfluxUser='" + monitoringInfluxUser + '\'' +
                ", monitoringInfluxPassword='" + monitoringInfluxPassword + '\'' +
                ", monitoringInfluxDatabase='" + monitoringInfluxDatabase + '\'' +
                ", monitoringInfluxReportingFrequency=" + monitoringInfluxReportingFrequency +
                ", pluginSet=" + pluginSet +
                ", serializationSerializers=" + serializationSerializers +
                ", serializationsBindingsInbound=" + serializationsBindingsInbound +
                ", serializationsBindingsOutbound=" + serializationsBindingsOutbound +
                '}';
    }

    public Duration getRemotePingTimeout() {
        return remotePingTimeout;
    }

    public Duration getRemotePingCommunicationTimeout() {
        return remotePingCommunicationTimeout;
    }

    public Duration getRemoteUniteMinInterval() {
        return remoteUniteMinInterval;
    }

    public int getRemotePingMaxPeers() {
        return remotePingMaxPeers;
    }

    public int getNetworkId() {
        return networkId;
    }

    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    public String getMonitoringHostTag() {
        return monitoringHostTag;
    }

    public URI getMonitoringInfluxUri() {
        return monitoringInfluxUri;
    }

    public String getMonitoringInfluxUser() {
        return monitoringInfluxUser;
    }

    public MaskedString getMonitoringInfluxPassword() {
        return monitoringInfluxPassword;
    }

    public String getMonitoringInfluxDatabase() {
        return monitoringInfluxDatabase;
    }

    public Duration getMonitoringInfluxReportingFrequency() {
        return monitoringInfluxReportingFrequency;
    }

    public boolean isRemoteEnabled() {
        return remoteEnabled;
    }

    public InetAddress getRemoteBindHost() {
        return remoteBindHost;
    }

    public int getRemoteBindPort() {
        return remoteBindPort;
    }

    @SuppressWarnings("java:S2384")
    public Set<Endpoint> getRemoteEndpoints() {
        return remoteEndpoints;
    }

    public boolean isRemoteExposeEnabled() {
        return remoteExposeEnabled;
    }

    public Duration getRemotePingInterval() {
        return remotePingInterval;
    }

    public boolean isRemoteSuperPeerEnabled() {
        return remoteSuperPeerEnabled;
    }

    @SuppressWarnings("java:S2384")
    public Set<Endpoint> getRemoteSuperPeerEndpoints() {
        return remoteSuperPeerEndpoints;
    }

    public boolean isRemoteTcpFallbackEnabled() {
        return remoteTcpFallbackEnabled;
    }

    public InetAddress getRemoteTcpFallbackServerBindHost() {
        return remoteTcpFallbackServerBindHost;
    }

    public int getRemoteTcpFallbackServerBindPort() {
        return remoteTcpFallbackServerBindPort;
    }

    public Duration getRemoteTcpFallbackClientTimeout() {
        return remoteTcpFallbackClientTimeout;
    }

    @SuppressWarnings("java:S2384")
    public InetSocketAddress getRemoteTcpFallbackClientAddress() {
        return remoteTcpFallbackClientAddress;
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

    public int getRemoteMessageMtu() {
        return remoteMessageMtu;
    }

    public int getRemoteMessageMaxContentLength() {
        return remoteMessageMaxContentLength;
    }

    public Duration getRemoteMessageComposedMessageTransferTimeout() {
        return remoteMessageComposedMessageTransferTimeout;
    }

    public byte getRemoteMessageHopLimit() {
        return remoteMessageHopLimit;
    }

    public boolean isRemoteMessageArmEnabled() {
        return remoteMessageArmEnabled;
    }

    public Map<CompressedPublicKey, InetSocketAddressWrapper> getRemoteStaticRoutes() {
        return remoteStaticRoutes;
    }

    public boolean isIntraVmDiscoveryEnabled() {
        return intraVmDiscoveryEnabled;
    }

    public boolean isRemoteLocalHostDiscoveryEnabled() {
        return remoteLocalHostDiscoveryEnabled;
    }

    public Path getRemoteLocalHostDiscoveryPath() {
        return remoteLocalHostDiscoveryPath;
    }

    public Duration getRemoteLocalHostDiscoveryLeaseTime() {
        return remoteLocalHostDiscoveryLeaseTime;
    }

    public boolean isRemoteLocalHostDiscoveryWatchEnabled() {
        return remoteLocalHostDiscoveryWatchEnabled;
    }

    public boolean isRemoteLocalNetworkDiscoveryEnabled() {
        return remoteLocalNetworkDiscoveryEnabled;
    }

    @SuppressWarnings("java:S2384")
    public Set<DrasylPlugin> getPlugins() {
        return pluginSet;
    }

    public Map<String, Serializer> getSerializationSerializers() {
        return serializationSerializers;
    }

    public Map<Class<?>, String> getSerializationsBindingsInbound() {
        return serializationsBindingsInbound;
    }

    public Map<Class<?>, String> getSerializationsBindingsOutbound() {
        return serializationsBindingsOutbound;
    }

    public int getMessageBufferSize() {
        return messageBufferSize;
    }

    @SuppressWarnings("java:S2972")
    public static final class Builder {
        //======================================= Config Values ========================================
        private int networkId;
        private ProofOfWork identityProofOfWork;
        private CompressedPublicKey identityPublicKey;
        private CompressedPrivateKey identityPrivateKey;
        private Path identityPath;
        private int messageBufferSize;
        private boolean intraVmDiscoveryEnabled;
        private InetAddress remoteBindHost;
        private boolean remoteEnabled;
        private int remoteBindPort;
        private Duration remotePingInterval;
        private Duration remotePingTimeout;
        private Duration remotePingCommunicationTimeout;
        private Duration remoteUniteMinInterval;
        private int remotePingMaxPeers;
        private Set<Endpoint> remoteEndpoints;
        private boolean remoteExposeEnabled;
        private int remoteMessageMtu;
        private int remoteMessageMaxContentLength;
        private byte remoteMessageHopLimit;
        private boolean remoteMessageArmEnabled;
        private Duration remoteMessageComposedMessageTransferTimeout;
        private boolean remoteSuperPeerEnabled;
        private Set<Endpoint> remoteSuperPeerEndpoints;
        private Map<CompressedPublicKey, InetSocketAddressWrapper> remoteStaticRoutes;
        private boolean remoteLocalHostDiscoveryEnabled;
        private Path remoteLocalHostDiscoveryPath;
        private Duration remoteLocalHostDiscoveryLeaseTime;
        private boolean remoteLocalHostDiscoveryWatchEnabled;
        private boolean remoteLocalNetworkDiscoveryEnabled;
        private boolean remoteTcpFallbackEnabled;
        private Duration remoteTcpFallbackClientTimeout;
        private InetAddress remoteTcpFallbackServerBindHost;
        private int remoteTcpFallbackServerBindPort;
        private InetSocketAddress remoteTcpFallbackClientAddress;
        private boolean monitoringEnabled;
        private String monitoringHostTag;
        private URI monitoringInfluxUri;
        private String monitoringInfluxUser;
        private MaskedString monitoringInfluxPassword;
        private String monitoringInfluxDatabase;
        private Duration monitoringInfluxReportingFrequency;
        private Set<DrasylPlugin> pluginSet;
        private Map<String, Serializer> serializationSerializers;
        private Map<Class<?>, String> serializationsBindingsInbound;
        private Map<Class<?>, String> serializationsBindingsOutbound;

        @SuppressWarnings({ "java:S107", "java:S2384" })
        public Builder(final int networkId,
                       final ProofOfWork identityProofOfWork,
                       final CompressedPublicKey identityPublicKey,
                       final CompressedPrivateKey identityPrivateKey,
                       final Path identityPath,
                       final int messageBufferSize,
                       final boolean intraVmDiscoveryEnabled,
                       final InetAddress remoteBindHost,
                       final boolean remoteEnabled,
                       final int remoteBindPort,
                       final Duration remotePingInterval,
                       final Duration remotePingTimeout,
                       final Duration remotePingCommunicationTimeout,
                       final Duration remoteUniteMinInterval,
                       final int remotePingMaxPeers,
                       final Set<Endpoint> remoteEndpoints,
                       final boolean remoteExposeEnabled,
                       final boolean remoteSuperPeerEnabled,
                       final Set<Endpoint> remoteSuperPeerEndpoints,
                       final Map<CompressedPublicKey, InetSocketAddressWrapper> remoteStaticRoutes,
                       final int remoteMessageMtu,
                       final int remoteMessageMaxContentLength,
                       final Duration remoteMessageComposedMessageTransferTimeout,
                       final byte remoteMessageHopLimit,
                       final boolean remoteMessageArmEnabled,
                       final boolean remoteLocalHostDiscoveryEnabled,
                       final Path remoteLocalHostDiscoveryPath,
                       final Duration remoteLocalHostDiscoveryLeaseTime,
                       final boolean remoteLocalHostDiscoveryWatchEnabled,
                       final boolean remoteLocalNetworkDiscoveryEnabled,
                       final boolean remoteTcpFallbackEnabled,
                       final InetAddress remoteTcpFallbackServerBindHost,
                       final int remoteTcpFallbackServerBindPort,
                       final Duration remoteTcpFallbackClientTimeout,
                       final InetSocketAddress remoteTcpFallbackClientAddress,
                       final boolean monitoringEnabled,
                       final String monitoringHostTag,
                       final URI monitoringInfluxUri,
                       final String monitoringInfluxUser,
                       final MaskedString monitoringInfluxPassword,
                       final String monitoringInfluxDatabase,
                       final Duration monitoringInfluxReportingFrequency,
                       final Set<DrasylPlugin> pluginSet,
                       final Map<String, Serializer> serializationSerializers,
                       final Map<Class<?>, String> serializationsBindingsInbound,
                       final Map<Class<?>, String> serializationsBindingsOutbound) {
            this.networkId = networkId;
            this.identityProofOfWork = identityProofOfWork;
            this.identityPublicKey = identityPublicKey;
            this.identityPrivateKey = identityPrivateKey;
            this.identityPath = identityPath;
            this.messageBufferSize = messageBufferSize;
            this.remoteBindHost = requireNonNull(remoteBindHost);
            this.remoteEnabled = remoteEnabled;
            this.monitoringHostTag = requireNonNull(monitoringHostTag);
            this.remoteBindPort = remoteBindPort;
            this.remotePingInterval = requireNonNull(remotePingInterval);
            this.remotePingTimeout = requireNonNull(remotePingTimeout);
            this.remotePingCommunicationTimeout = requireNonNull(remotePingCommunicationTimeout);
            this.remoteUniteMinInterval = requireNonNull(remoteUniteMinInterval);
            this.remotePingMaxPeers = remotePingMaxPeers;
            this.remoteEndpoints = requireNonNull(remoteEndpoints);
            this.remoteExposeEnabled = remoteExposeEnabled;
            this.remoteMessageMtu = remoteMessageMtu;
            this.remoteMessageMaxContentLength = remoteMessageMaxContentLength;
            this.remoteMessageHopLimit = remoteMessageHopLimit;
            this.remoteMessageArmEnabled = remoteMessageArmEnabled;
            this.remoteMessageComposedMessageTransferTimeout = requireNonNull(remoteMessageComposedMessageTransferTimeout);
            this.remoteSuperPeerEnabled = remoteSuperPeerEnabled;
            this.remoteSuperPeerEndpoints = requireNonNull(remoteSuperPeerEndpoints);
            this.remoteStaticRoutes = requireNonNull(remoteStaticRoutes);
            this.remoteTcpFallbackEnabled = remoteTcpFallbackEnabled;
            this.remoteTcpFallbackServerBindHost = requireNonNull(remoteTcpFallbackServerBindHost);
            this.remoteTcpFallbackServerBindPort = remoteTcpFallbackServerBindPort;
            this.remoteTcpFallbackClientTimeout = remoteTcpFallbackClientTimeout;
            this.remoteTcpFallbackClientAddress = requireNonNull(remoteTcpFallbackClientAddress);
            this.intraVmDiscoveryEnabled = intraVmDiscoveryEnabled;
            this.remoteLocalHostDiscoveryEnabled = remoteLocalHostDiscoveryEnabled;
            this.remoteLocalHostDiscoveryPath = requireNonNull(remoteLocalHostDiscoveryPath);
            this.remoteLocalHostDiscoveryLeaseTime = requireNonNull(remoteLocalHostDiscoveryLeaseTime);
            this.remoteLocalHostDiscoveryWatchEnabled = remoteLocalHostDiscoveryWatchEnabled;
            this.remoteLocalNetworkDiscoveryEnabled = remoteLocalNetworkDiscoveryEnabled;
            this.monitoringEnabled = monitoringEnabled;
            this.monitoringInfluxUri = requireNonNull(monitoringInfluxUri);
            this.monitoringInfluxUser = requireNonNull(monitoringInfluxUser);
            this.monitoringInfluxPassword = requireNonNull(monitoringInfluxPassword);
            this.monitoringInfluxDatabase = requireNonNull(monitoringInfluxDatabase);
            this.monitoringInfluxReportingFrequency = requireNonNull(monitoringInfluxReportingFrequency);
            this.pluginSet = requireNonNull(pluginSet);
            this.serializationSerializers = requireNonNull(serializationSerializers);
            this.serializationsBindingsInbound = requireNonNull(serializationsBindingsInbound);
            this.serializationsBindingsOutbound = requireNonNull(serializationsBindingsOutbound);
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

        public Builder messageBufferSize(final int messageBufferSize) {
            this.messageBufferSize = messageBufferSize;
            return this;
        }

        public Builder remoteBindHost(final InetAddress remoteBindHost) {
            this.remoteBindHost = requireNonNull(remoteBindHost);
            return this;
        }

        public Builder remoteEnabled(final boolean remoteEnabled) {
            this.remoteEnabled = remoteEnabled;
            return this;
        }

        public Builder remoteBindPort(final int remoteBindPort) {
            this.remoteBindPort = remoteBindPort;
            return this;
        }

        public Builder remotePingInterval(final Duration remotePingInterval) {
            this.remotePingInterval = remotePingInterval;
            return this;
        }

        public Builder remotePingTimeout(final Duration remotePingTimeout) {
            this.remotePingTimeout = remotePingTimeout;
            return this;
        }

        public Builder remotePingCommunicationTimeout(final Duration remotePingCommunicationTimeout) {
            this.remotePingCommunicationTimeout = requireNonNull(remotePingCommunicationTimeout);
            return this;
        }

        public Builder remoteUniteMinInterval(final Duration remoteUniteMinInterval) {
            this.remoteUniteMinInterval = requireNonNull(remoteUniteMinInterval);
            return this;
        }

        public Builder remotePingMaxPeers(final int remotePingMaxPeers) {
            this.remotePingMaxPeers = remotePingMaxPeers;
            return this;
        }

        public Builder remoteEndpoints(final Set<Endpoint> remoteEndpoints) {
            this.remoteEndpoints = Set.copyOf(remoteEndpoints);
            return this;
        }

        public Builder remoteExposeEnabled(final boolean remoteExposeEnabled) {
            this.remoteExposeEnabled = remoteExposeEnabled;
            return this;
        }

        public Builder remoteStaticRoutes(final Map<CompressedPublicKey, InetSocketAddressWrapper> remoteStaticRoutes) {
            this.remoteStaticRoutes = Map.copyOf(remoteStaticRoutes);
            return this;
        }

        public Builder remoteMessageMtu(final int remoteMessageMtu) {
            this.remoteMessageMtu = remoteMessageMtu;
            return this;
        }

        public Builder remoteMessageMaxContentLength(final int remoteMessageMaxContentLength) {
            this.remoteMessageMaxContentLength = remoteMessageMaxContentLength;
            return this;
        }

        public Builder remoteMessageHopLimit(final byte remoteMessageHopLimit) {
            this.remoteMessageHopLimit = remoteMessageHopLimit;
            return this;
        }

        public Builder remoteMessageArmEnabled(final boolean remoteMessageArmEnabled) {
            this.remoteMessageArmEnabled = remoteMessageArmEnabled;
            return this;
        }

        public Builder remoteMessageComposedMessageTransferTimeout(final Duration messageComposedMessageTransferTimeout) {
            this.remoteMessageComposedMessageTransferTimeout = requireNonNull(messageComposedMessageTransferTimeout);
            return this;
        }

        public Builder remoteSuperPeerEnabled(final boolean remoteSuperPeerEnabled) {
            this.remoteSuperPeerEnabled = remoteSuperPeerEnabled;
            return this;
        }

        public Builder remoteSuperPeerEndpoints(final Set<Endpoint> remoteSuperPeerEndpoints) {
            this.remoteSuperPeerEndpoints = Set.copyOf(remoteSuperPeerEndpoints);
            return this;
        }

        public Builder intraVmDiscoveryEnabled(final boolean intraVmDiscoveryEnabled) {
            this.intraVmDiscoveryEnabled = intraVmDiscoveryEnabled;
            return this;
        }

        public Builder remoteLocalHostDiscoveryEnabled(final boolean remoteLocalHostDiscoveryEnabled) {
            this.remoteLocalHostDiscoveryEnabled = remoteLocalHostDiscoveryEnabled;
            return this;
        }

        public Builder remoteLocalHostDiscoveryPath(final Path remoteLocalHostDiscoveryPath) {
            this.remoteLocalHostDiscoveryPath = requireNonNull(remoteLocalHostDiscoveryPath);
            return this;
        }

        public Builder remoteLocalHostDiscoveryLeaseTime(final Duration remoteLocalHostDiscoveryLeaseTime) {
            this.remoteLocalHostDiscoveryLeaseTime = requireNonNull(remoteLocalHostDiscoveryLeaseTime);
            return this;
        }

        public Builder remoteLocalHostDiscoveryWatchEnabled(final boolean remoteLocalHostDiscoveryWatchEnabled) {
            this.remoteLocalHostDiscoveryWatchEnabled = remoteLocalHostDiscoveryWatchEnabled;
            return this;
        }

        public Builder remoteLocalNetworkDiscoveryEnabled(final boolean remoteLocalNetworkDiscoveryEnabled) {
            this.remoteLocalNetworkDiscoveryEnabled = remoteLocalNetworkDiscoveryEnabled;
            return this;
        }

        public Builder remoteTcpFallbackEnabled(final boolean remoteTcpFallbackEnabled) {
            this.remoteTcpFallbackEnabled = remoteTcpFallbackEnabled;
            return this;
        }

        public Builder remoteTcpFallbackServerBindHost(final InetAddress remoteTcpFallbackServerBindHost) {
            this.remoteTcpFallbackServerBindHost = requireNonNull(remoteTcpFallbackServerBindHost);
            return this;
        }

        public Builder remoteTcpFallbackServerBindPort(final int remoteTcpFallbackServerBindPort) {
            this.remoteTcpFallbackServerBindPort = remoteTcpFallbackServerBindPort;
            return this;
        }

        public Builder remoteTcpFallbackClientTimeout(final Duration remoteTcpFallbackClientTimeout) {
            this.remoteTcpFallbackClientTimeout = requireNonNull(remoteTcpFallbackClientTimeout);
            return this;
        }

        public Builder remoteTcpFallbackClientAddress(final InetSocketAddress remoteTcpFallbackClientAddress) {
            this.remoteTcpFallbackClientAddress = requireNonNull(remoteTcpFallbackClientAddress);
            return this;
        }

        public Builder monitoringEnabled(final boolean monitoringEnabled) {
            this.monitoringEnabled = monitoringEnabled;
            return this;
        }

        public Builder monitoringHostTag(final String monitoringHostTag) {
            this.monitoringHostTag = requireNonNull(monitoringHostTag);
            return this;
        }

        public Builder monitoringInfluxUri(final URI monitoringInfluxUri) {
            this.monitoringInfluxUri = requireNonNull(monitoringInfluxUri);
            return this;
        }

        public Builder monitoringInfluxUser(final String monitoringInfluxUser) {
            this.monitoringInfluxUser = requireNonNull(monitoringInfluxUser);
            return this;
        }

        public Builder monitoringInfluxPassword(final MaskedString monitoringInfluxPassword) {
            this.monitoringInfluxPassword = requireNonNull(monitoringInfluxPassword);
            return this;
        }

        public Builder monitoringInfluxDatabase(final String monitoringInfluxDatabase) {
            this.monitoringInfluxDatabase = requireNonNull(monitoringInfluxDatabase);
            return this;
        }

        public Builder monitoringInfluxReportingFrequency(final Duration monitoringInfluxReportingFrequency) {
            this.monitoringInfluxReportingFrequency = requireNonNull(monitoringInfluxReportingFrequency);
            return this;
        }

        public Builder plugins(final Set<DrasylPlugin> pluginSet) {
            this.pluginSet = Set.copyOf(pluginSet);
            return this;
        }

        public Builder serializationSerializers(final Map<String, Serializer> serializationSerializers) {
            this.serializationSerializers = Map.copyOf(serializationSerializers);
            return this;
        }

        public Builder serializationsBindingsInbound(final Map<Class<?>, String> serializationsBindingsInbound) {
            this.serializationsBindingsInbound = Map.copyOf(serializationsBindingsInbound);
            return this;
        }

        public Builder addSerializationsBindingsInbound(final Class<?> clazz, final String name) {
            final Map<Class<?>, String> bindings = new HashMap<>(this.serializationsBindingsInbound);
            bindings.put(clazz, name);
            return serializationsBindingsInbound(bindings);
        }

        public Builder serializationsBindingsOutbound(final Map<Class<?>, String> serializationsBindingsOutbound) {
            this.serializationsBindingsOutbound = Map.copyOf(serializationsBindingsOutbound);
            return this;
        }

        public Builder addSerializationsBindingsOutbound(final Class<?> clazz, final String name) {
            final Map<Class<?>, String> bindings = new HashMap<>(this.serializationsBindingsOutbound);
            bindings.put(clazz, name);
            return serializationsBindingsOutbound(bindings);
        }

        public DrasylConfig build() {
            return new DrasylConfig(
                    networkId,
                    identityProofOfWork,
                    identityPublicKey,
                    identityPrivateKey,
                    identityPath,
                    messageBufferSize,
                    intraVmDiscoveryEnabled,
                    remoteBindHost,
                    remoteEnabled,
                    remoteBindPort,
                    remotePingInterval,
                    remotePingTimeout,
                    remotePingCommunicationTimeout,
                    remoteUniteMinInterval,
                    remotePingMaxPeers,
                    remoteEndpoints,
                    remoteExposeEnabled,
                    remoteSuperPeerEnabled,
                    remoteSuperPeerEndpoints,
                    remoteStaticRoutes,
                    remoteMessageMaxContentLength,
                    remoteMessageHopLimit,
                    remoteMessageArmEnabled,
                    remoteMessageComposedMessageTransferTimeout,
                    remoteMessageMtu,
                    remoteLocalHostDiscoveryEnabled,
                    remoteLocalHostDiscoveryPath,
                    remoteLocalHostDiscoveryLeaseTime,
                    remoteLocalHostDiscoveryWatchEnabled,
                    remoteLocalNetworkDiscoveryEnabled,
                    remoteTcpFallbackEnabled,
                    remoteTcpFallbackServerBindHost,
                    remoteTcpFallbackServerBindPort,
                    remoteTcpFallbackClientTimeout,
                    remoteTcpFallbackClientAddress,
                    monitoringEnabled,
                    monitoringHostTag,
                    monitoringInfluxUri,
                    monitoringInfluxUser,
                    monitoringInfluxPassword,
                    monitoringInfluxDatabase,
                    monitoringInfluxReportingFrequency,
                    pluginSet,
                    serializationSerializers,
                    serializationsBindingsInbound,
                    serializationsBindingsOutbound
            );
        }
    }
}
