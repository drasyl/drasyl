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
package org.drasyl.node;

import com.google.auto.value.AutoValue;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import org.drasyl.annotation.Nullable;
import org.drasyl.identity.*;
import org.drasyl.node.handler.plugin.DrasylPlugin;
import org.drasyl.node.handler.serialization.Serializer;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

import static org.drasyl.util.InetSocketAddressUtil.socketAddressFromString;

/**
 * This class represents the configuration for a {@link DrasylNode}. For example, it defines the
 * identity and the Super Peer.
 * <p>
 * This is an immutable object.
 */
@SuppressWarnings("java:S118")
@AutoValue
public abstract class DrasylConfig {
    static final DrasylConfig DEFAULT = DrasylConfig.of(ConfigFactory.defaultReference());
    //======================================== Config Paths ========================================
    public static final String NETWORK_ID = "drasyl.network.id";
    public static final String IDENTITY_PROOF_OF_WORK = "drasyl.identity.proof-of-work";
    public static final String IDENTITY_SECRET_KEY = "drasyl.identity.secret-key";
    public static final String IDENTITY_PATH = "drasyl.identity.path";
    public static final String MESSAGE_BUFFER_SIZE = "drasyl.message.buffer-size";
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
    public static final String REMOTE_LOCAL_HOST_DISCOVERY_ENABLED = "drasyl.remote.local-host-discovery.enabled";
    public static final String REMOTE_LOCAL_HOST_DISCOVERY_PATH = "drasyl.remote.local-host-discovery.path";
    public static final String REMOTE_LOCAL_HOST_DISCOVERY_LEASE_TIME = "drasyl.remote.local-host-discovery.lease-time";
    public static final String REMOTE_LOCAL_HOST_DISCOVERY_WATCH_ENABLED = "drasyl.remote.local-host-discovery.watch.enabled";
    public static final String REMOTE_LOCAL_NETWORK_DISCOVERY_ENABLED = "drasyl.remote.local-network-discovery.enabled";
    public static final String REMOTE_HANDSHAKE_TIMEOUT = "drasyl.remote.handshake.timeout";
    public static final String REMOTE_MESSAGE_MTU = "drasyl.remote.message.mtu";
    public static final String REMOTE_MESSAGE_MAX_CONTENT_LENGTH = "drasyl.remote.message.max-content-length";
    public static final String REMOTE_MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT = "drasyl.remote.message.composed-message-transfer-timeout";
    public static final String REMOTE_MESSAGE_HOP_LIMIT = "drasyl.remote.message.hop-limit";
    public static final String REMOTE_MESSAGE_ARM_PROTOCOL_ENABLED = "drasyl.remote.message.arm.protocol.enabled";
    public static final String REMOTE_MESSAGE_ARM_PROTOCOL_SESSION_MAX_COUNT = "drasyl.remote.message.arm.protocol.session.max-count";
    public static final String REMOTE_MESSAGE_ARM_PROTOCOL_SESSION_EXPIRE_AFTER = "drasyl.remote.message.arm.protocol.session.expire-after";
    public static final String REMOTE_MESSAGE_ARM_APPLICATION_ENABLED = "drasyl.remote.message.arm.application.enabled";
    public static final String REMOTE_MESSAGE_ARM_APPLICATION_AGREEMENT_MAX_COUNT = "drasyl.remote.message.arm.application.agreement.max-count";
    public static final String REMOTE_MESSAGE_ARM_APPLICATION_AGREEMENT_EXPIRE_AFTER = "drasyl.remote.message.arm.application.agreement.expire-after";
    public static final String REMOTE_MESSAGE_ARM_APPLICATION_AGREEMENT_RETRY_INTERVAL = "drasyl.remote.message.arm.application.agreement.retry-interval";
    public static final String REMOTE_MESSAGE_ARQ_ENABLED = "drasyl.remote.message.arq.enabled";
    public static final String REMOTE_MESSAGE_ARQ_WINDOW_SIZE = "drasyl.remote.message.arq.window-size";
    public static final String REMOTE_MESSAGE_ARQ_RETRY_TIMEOUT = "drasyl.remote.message.arq.retry-timeout";
    public static final String REMOTE_MESSAGE_ARQ_DEAD_PEER_TIMEOUT = "drasyl.remote.message.arq.dead-peer-timeout";
    public static final String REMOTE_MESSAGE_ARQ_CLOCK = "drasyl.remote.message.arq.clock";
    public static final String REMOTE_TCP_FALLBACK_ENABLED = "drasyl.remote.tcp-fallback.enabled";
    public static final String REMOTE_TCP_FALLBACK_SERVER_BIND_HOST = "drasyl.remote.tcp-fallback.server.bind-host";
    public static final String REMOTE_TCP_FALLBACK_SERVER_BIND_PORT = "drasyl.remote.tcp-fallback.server.bind-port";
    public static final String REMOTE_TCP_FALLBACK_CLIENT_TIMEOUT = "drasyl.remote.tcp-fallback.client.timeout";
    public static final String REMOTE_TCP_FALLBACK_CLIENT_ADDRESS = "drasyl.remote.tcp-fallback.client.address";
    public static final String INTRA_VM_DISCOVERY_ENABLED = "drasyl.intra-vm-discovery.enabled";
    public static final String CHANNEL_INACTIVITY_TIMEOUT = "drasyl.channel.inactivity-timeout";
    public static final String PLUGINS = "drasyl.plugins";
    public static final String SERIALIZATION_SERIALIZERS = "drasyl.serialization.serializers";
    public static final String SERIALIZATION_BINDINGS_INBOUND = "drasyl.serialization.bindings.inbound";
    public static final String SERIALIZATION_BINDINGS_OUTBOUND = "drasyl.serialization.bindings.outbound";

    public static DrasylConfig of() {
        return of(ConfigFactory.load());
    }

    @SuppressWarnings("java:S138")
    public static DrasylConfig of(final Config config) {
        try {
            config.checkValid(ConfigFactory.defaultReference(), "drasyl");

            final Builder builder = new AutoValue_DrasylConfig.Builder();

            // network
            builder.networkId(config.getInt(NETWORK_ID));

            // identity
            if (!config.getIsNull(IDENTITY_PROOF_OF_WORK)) {
                builder.identityProofOfWork(getProofOfWork(config, IDENTITY_PROOF_OF_WORK));
            }
            if (!config.getString(IDENTITY_SECRET_KEY).isEmpty()) {
                builder.identitySecretKey(getIdentitySecretKey(config, IDENTITY_SECRET_KEY));
            }
            builder.identityPath(getPath(config, IDENTITY_PATH));

            // message
            builder.messageBufferSize(config.getInt(MESSAGE_BUFFER_SIZE));

            // remote
            builder.remoteEnabled(config.getBoolean(REMOTE_ENABLED));
            builder.remoteBindHost(getInetAddress(config, REMOTE_BIND_HOST));
            builder.remoteBindPort(config.getInt(REMOTE_BIND_PORT));
            builder.remoteEndpoints(Set.copyOf(getEndpointSet(config, REMOTE_ENDPOINTS)));
            builder.remoteExposeEnabled(config.getBoolean(REMOTE_EXPOSE_ENABLED));
            builder.remotePingInterval(config.getDuration(REMOTE_PING_INTERVAL));
            builder.remotePingTimeout(config.getDuration(REMOTE_PING_TIMEOUT));
            builder.remotePingCommunicationTimeout(config.getDuration(REMOTE_PING_COMMUNICATION_TIMEOUT));
            builder.remotePingMaxPeers(config.getInt(REMOTE_PING_MAX_PEERS));
            builder.remoteUniteMinInterval(config.getDuration(REMOTE_UNITE_MIN_INTERVAL));
            builder.remoteSuperPeerEnabled(config.getBoolean(REMOTE_SUPER_PEER_ENABLED));
            builder.remoteSuperPeerEndpoints(Set.copyOf(getEndpointSet(config, REMOTE_SUPER_PEER_ENDPOINTS)));
            builder.remoteStaticRoutes(Map.copyOf(getStaticRoutes(config, REMOTE_STATIC_ROUTES)));
            builder.remoteLocalHostDiscoveryEnabled(config.getBoolean(REMOTE_LOCAL_HOST_DISCOVERY_ENABLED));
            final String discoveryPath = config.getString(REMOTE_LOCAL_HOST_DISCOVERY_PATH);
            if (discoveryPath == null || discoveryPath.isEmpty()) {
                builder.remoteLocalHostDiscoveryPath(Paths.get(System.getProperty("java.io.tmpdir"), "drasyl-discovery"));
            }
            else {
                builder.remoteLocalHostDiscoveryPath(getPath(config, REMOTE_LOCAL_HOST_DISCOVERY_PATH));
            }
            builder.remoteLocalHostDiscoveryLeaseTime(config.getDuration(REMOTE_LOCAL_HOST_DISCOVERY_LEASE_TIME));
            builder.remoteLocalHostDiscoveryWatchEnabled(config.getBoolean(REMOTE_LOCAL_HOST_DISCOVERY_WATCH_ENABLED));
            builder.remoteLocalNetworkDiscoveryEnabled(config.getBoolean(REMOTE_LOCAL_NETWORK_DISCOVERY_ENABLED));
            builder.remoteMessageMtu((int) Math.min(config.getMemorySize(REMOTE_MESSAGE_MTU).toBytes(), Integer.MAX_VALUE));
            builder.remoteMessageMaxContentLength((int) Math.min(config.getMemorySize(REMOTE_MESSAGE_MAX_CONTENT_LENGTH).toBytes(), Integer.MAX_VALUE));
            builder.remoteMessageComposedMessageTransferTimeout(config.getDuration(REMOTE_MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT));
            builder.remoteMessageHopLimit(getByte(config, REMOTE_MESSAGE_HOP_LIMIT));
            builder.remoteTcpFallbackEnabled(config.getBoolean(REMOTE_TCP_FALLBACK_ENABLED));
            builder.remoteTcpFallbackServerBindHost(getInetAddress(config, REMOTE_TCP_FALLBACK_SERVER_BIND_HOST));
            builder.remoteTcpFallbackServerBindPort(config.getInt(REMOTE_TCP_FALLBACK_SERVER_BIND_PORT));
            builder.remoteTcpFallbackClientTimeout(config.getDuration(REMOTE_TCP_FALLBACK_CLIENT_TIMEOUT));
            builder.remoteTcpFallbackClientAddress(getInetSocketAddress(config, REMOTE_TCP_FALLBACK_CLIENT_ADDRESS));

            // handshake
            builder.remoteHandshakeTimeout(config.getDuration(REMOTE_HANDSHAKE_TIMEOUT));

            // arm
            builder.remoteMessageArmProtocolEnabled(config.getBoolean(REMOTE_MESSAGE_ARM_PROTOCOL_ENABLED));
            builder.remoteMessageArmProtocolSessionMaxCount(config.getInt(REMOTE_MESSAGE_ARM_PROTOCOL_SESSION_MAX_COUNT));
            builder.remoteMessageArmProtocolSessionExpireAfter(config.getDuration(REMOTE_MESSAGE_ARM_PROTOCOL_SESSION_EXPIRE_AFTER));
            builder.remoteMessageArmApplicationEnabled(config.getBoolean(REMOTE_MESSAGE_ARM_APPLICATION_ENABLED));
            builder.remoteMessageArmApplicationAgreementMaxCount(config.getInt(REMOTE_MESSAGE_ARM_APPLICATION_AGREEMENT_MAX_COUNT));
            builder.remoteMessageArmApplicationAgreementExpireAfter(config.getDuration(REMOTE_MESSAGE_ARM_APPLICATION_AGREEMENT_EXPIRE_AFTER));
            builder.remoteMessageArmApplicationAgreementRetryInterval(config.getDuration(REMOTE_MESSAGE_ARM_APPLICATION_AGREEMENT_RETRY_INTERVAL));

            // arq
            builder.remoteMessageArqEnabled(config.getBoolean(REMOTE_MESSAGE_ARQ_ENABLED));
            builder.remoteMessageArqWindowSize(config.getInt(REMOTE_MESSAGE_ARQ_WINDOW_SIZE));
            builder.remoteMessageArqRetryTimeout(config.getDuration(REMOTE_MESSAGE_ARQ_RETRY_TIMEOUT));
            builder.remoteMessageArqDeadPeerTimeout(config.getDuration(REMOTE_MESSAGE_ARQ_DEAD_PEER_TIMEOUT));
            builder.remoteMessageArqClock(config.getDuration(REMOTE_MESSAGE_ARQ_CLOCK));

            // intra vm discovery
            builder.intraVmDiscoveryEnabled(config.getBoolean(INTRA_VM_DISCOVERY_ENABLED));

            // plugins
            builder.plugins(Set.copyOf(getPlugins(config, PLUGINS)));

            // serialization
            builder.serializationSerializers(Map.copyOf(getSerializationSerializers(config, SERIALIZATION_SERIALIZERS)));
            builder.serializationsBindingsInbound(Map.copyOf(getSerializationBindings(config, SERIALIZATION_BINDINGS_INBOUND, getSerializationSerializers(config, SERIALIZATION_SERIALIZERS).keySet())));
            builder.serializationsBindingsOutbound(Map.copyOf(getSerializationBindings(config, SERIALIZATION_BINDINGS_OUTBOUND, getSerializationSerializers(config, SERIALIZATION_SERIALIZERS).keySet())));

            // channel
            builder.channelInactivityTimeout(config.getDuration(CHANNEL_INACTIVITY_TIMEOUT));

            return builder.build();
        }
        catch (final ConfigException e) {
            throw new DrasylConfigException(e);
        }
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
     * Gets the {@link IdentityPublicKey} at the given path. Similar to {@link Config}, an exception
     * is thrown for an invalid value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link IdentityPublicKey} value at the requested path
     * @throws DrasylConfigException if value is not convertible to a {@link IdentityPublicKey}
     */
    @SuppressWarnings({ "java:S1192" })
    public static IdentityPublicKey getIdentityPublicKey(final Config config, final String path) {
        try {
            final String stringValue = config.getString(path);
            return IdentityPublicKey.of(stringValue);
        }
        catch (final IllegalArgumentException | ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * Gets the {@link IdentitySecretKey} at the given path. Similar to {@link Config}, an exception
     * is thrown for an invalid value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link IdentitySecretKey} value at the requested path
     * @throws DrasylConfigException if value is not convertible to a {@link IdentitySecretKey}
     */
    @SuppressWarnings({ "java:S1192" })
    public static IdentitySecretKey getIdentitySecretKey(final Config config, final String path) {
        try {
            final String stringValue = config.getString(path);
            return IdentitySecretKey.of(stringValue);
        }
        catch (final ConfigException | IllegalArgumentException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * Gets the {@link KeyAgreementPublicKey} at the given path. Similar to {@link Config}, an
     * exception is thrown for an invalid value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link IdentityPublicKey} value at the requested path
     * @throws DrasylConfigException if value is not convertible to a {@link IdentityPublicKey}
     */
    @SuppressWarnings({ "java:S1192" })
    public static KeyAgreementPublicKey getKeyAgreementPublicKey(final Config config,
                                                                 final String path) {
        try {
            final String stringValue = config.getString(path);
            return KeyAgreementPublicKey.of(stringValue);
        }
        catch (final IllegalArgumentException | ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    /**
     * Gets the {@link KeyAgreementSecretKey} at the given path. Similar to {@link Config}, an
     * exception is thrown for an invalid value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link IdentitySecretKey} value at the requested path
     * @throws DrasylConfigException if value is not convertible to a {@link IdentitySecretKey}
     */
    @SuppressWarnings({ "java:S1192" })
    public static KeyAgreementSecretKey getKeyAgreementSecretKey(final Config config,
                                                                 final String path) {
        try {
            final String stringValue = config.getString(path);
            return KeyAgreementSecretKey.of(stringValue);
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
    public static Set<PeerEndpoint> getEndpointSet(final Config config, final String path) {
        try {
            final List<String> stringListValue = config.getStringList(path);
            final Set<PeerEndpoint> endpointList = new HashSet<>();
            for (final String stringValue : stringListValue) {
                endpointList.add(PeerEndpoint.of(stringValue));
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
    public static PeerEndpoint getEndpoint(final Config config, final String path) {
        try {
            final String stringValue = config.getString(path);
            return PeerEndpoint.of(stringValue);
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

    @SuppressWarnings({ "java:S1192", "java:S2658" })
    private static DrasylPlugin initiatePlugin(final String path,
                                               final String clazzName,
                                               final Config pluginConfig) {
        try {
            @SuppressWarnings("unchecked") final Class<? extends DrasylPlugin> clazz = (Class<? extends DrasylPlugin>) Class.forName(clazzName);
            final Constructor<? extends DrasylPlugin> constructor = clazz.getConstructor(Config.class);
            constructor.setAccessible(true); // NOSONAR

            return constructor.newInstance(pluginConfig);
        }
        catch (final ClassNotFoundException | NoSuchMethodException | InstantiationException |
                     InvocationTargetException | IllegalAccessException e) {
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

    @SuppressWarnings({ "java:S1192", "java:S2658" })
    private static Serializer initiateSerializer(final String path,
                                                 final String clazzName) {
        try {
            @SuppressWarnings("unchecked") final Class<? extends Serializer> clazz = (Class<? extends Serializer>) Class.forName(clazzName);
            final Constructor<? extends Serializer> constructor = clazz.getConstructor();
            constructor.setAccessible(true); // NOSONAR

            return constructor.newInstance();
        }
        catch (final ClassNotFoundException | InstantiationException | IllegalAccessException |
                     InvocationTargetException | NoSuchMethodException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    @SuppressWarnings("java:S2658")
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
    public static Map<DrasylAddress, InetSocketAddress> getStaticRoutes(final Config config,
                                                                        final String path) {
        try {
            final Map<DrasylAddress, InetSocketAddress> routes = new HashMap<>();
            for (final Map.Entry<String, ConfigValue> entry : config.getObject(path).entrySet()) {
                final IdentityPublicKey publicKey = IdentityPublicKey.of(entry.getKey());
                final InetSocketAddress address = socketAddressFromString(entry.getValue().atKey("address").getString("address"));
                if (address.getPort() < 1) {
                    throw new IllegalArgumentException("port missing");
                }

                routes.put(publicKey, address);
            }
            return routes;
        }
        catch (final IllegalArgumentException | ConfigException e) {
            throw new DrasylConfigException(path, e);
        }
    }

    abstract Builder toBuilder();

    /**
     * Parses a file into a Config instance as with
     *
     * @param file the file to parse
     * @return the parsed configuration
     * @throws DrasylConfigException on IO or parse errors
     */
    public static DrasylConfig parseFile(final File file) {
        try {
            return DrasylConfig.of(ConfigFactory.parseFile(file).withFallback(ConfigFactory.load()));
        }
        catch (final ConfigException e) {
            throw new DrasylConfigException(e);
        }
    }

    /**
     * Parses a file into a Config instance as with
     *
     * @param path the path to file to parse
     * @return the parsed configuration
     * @throws DrasylConfigException on IO or parse errors
     */
    public static DrasylConfig parseFile(final Path path) {
        return parseFile(path.toFile());
    }

    /**
     * Parses a file into a Config instance as with
     *
     * @param path the path to file to parse
     * @return the parsed configuration
     * @throws DrasylConfigException on IO or parse errors
     */
    public static DrasylConfig parseFile(final String path) {
        return parseFile(Path.of(path));
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
            return DrasylConfig.of(ConfigFactory.parseString(s).withFallback(ConfigFactory.load()));
        }
        catch (final ConfigException e) {
            throw new DrasylConfigException(e);
        }
    }

    /**
     * Creates a new builder to build a custom {@link DrasylConfig}. The built configuration is
     * derived from the default configuration. The builder must be finalized by calling
     * {@link Builder#build()} to create the resulting {@link DrasylConfig}.
     *
     * @return the new builder
     */
    public static Builder newBuilder() {
        return newBuilder(DEFAULT);
    }

    public static Builder newBuilder(final DrasylConfig config) {
        return config.toBuilder();
    }

    public abstract int getNetworkId();

    @Nullable
    public abstract ProofOfWork getIdentityProofOfWork();

    @Nullable
    public abstract IdentitySecretKey getIdentitySecretKey();

    /**
     * @return the identity specified in {@link #getIdentitySecretKey()}, and
     * {@link #getIdentityProofOfWork()} or {@code null} if some of these properties are not
     * present.
     * @throws IllegalStateException if the key pair returned by {@link #getIdentitySecretKey()} is
     *                               not {@code null} and can not be converted to a key agreement
     *                               key pair OR the {@link #getIdentityProofOfWork()} does not
     *                               match to the identity key pair/required difficulty specified in
     *                               {@link Identity#POW_DIFFICULTY}.
     */
    public Identity getIdentity() {
        if (getIdentityProofOfWork() != null && getIdentitySecretKey() != null) {
            try {
                final Identity identity = Identity.of(getIdentityProofOfWork(), getIdentitySecretKey());
                if (!identity.isValid()) {
                    throw new IllegalStateException("Proof of work does not match to the identity key pair/required difficulty of " + Identity.POW_DIFFICULTY + ".");
                }
                return identity;
            }
            catch (final IllegalArgumentException e) {
                throw new IllegalStateException("Identity key pair can not be converted to a key agreement key pair.", e);
            }
        }
        else {
            return null;
        }
    }

    public abstract Path getIdentityPath();

    public abstract int getMessageBufferSize();

    public abstract boolean isRemoteEnabled();

    public abstract InetAddress getRemoteBindHost();

    public abstract int getRemoteBindPort();

    public abstract Set<PeerEndpoint> getRemoteEndpoints();

    public abstract boolean isRemoteExposeEnabled();

    public abstract Duration getRemotePingInterval();

    public abstract Duration getRemotePingTimeout();

    public abstract Duration getRemotePingCommunicationTimeout();

    public abstract int getRemotePingMaxPeers();

    public abstract Duration getRemoteUniteMinInterval();

    public abstract boolean isRemoteSuperPeerEnabled();

    public abstract Set<PeerEndpoint> getRemoteSuperPeerEndpoints();

    public abstract Map<DrasylAddress, InetSocketAddress> getRemoteStaticRoutes();

    public abstract Duration getRemoteHandshakeTimeout();

    public abstract boolean isRemoteLocalHostDiscoveryEnabled();

    public abstract Path getRemoteLocalHostDiscoveryPath();

    public abstract Duration getRemoteLocalHostDiscoveryLeaseTime();

    public abstract boolean isRemoteLocalHostDiscoveryWatchEnabled();

    public abstract boolean isRemoteLocalNetworkDiscoveryEnabled();

    public abstract int getRemoteMessageMtu();

    public abstract int getRemoteMessageMaxContentLength();

    public abstract Duration getRemoteMessageComposedMessageTransferTimeout();

    public abstract byte getRemoteMessageHopLimit();

    public abstract boolean isRemoteMessageArmProtocolEnabled();

    public abstract int getRemoteMessageArmProtocolSessionMaxCount();

    public abstract Duration getRemoteMessageArmProtocolSessionExpireAfter();

    public abstract boolean isRemoteMessageArmApplicationEnabled();

    public abstract int getRemoteMessageArmApplicationAgreementMaxCount();

    public abstract Duration getRemoteMessageArmApplicationAgreementExpireAfter();

    public abstract Duration getRemoteMessageArmApplicationAgreementRetryInterval();

    public abstract boolean isRemoteMessageArqEnabled();

    public abstract int getRemoteMessageArqWindowSize();

    public abstract Duration getRemoteMessageArqRetryTimeout();

    public abstract Duration getRemoteMessageArqDeadPeerTimeout();

    public abstract Duration getRemoteMessageArqClock();

    public abstract boolean isRemoteTcpFallbackEnabled();

    public abstract InetAddress getRemoteTcpFallbackServerBindHost();

    public abstract int getRemoteTcpFallbackServerBindPort();

    public abstract Duration getRemoteTcpFallbackClientTimeout();

    public abstract InetSocketAddress getRemoteTcpFallbackClientAddress();

    public abstract boolean isIntraVmDiscoveryEnabled();

    public abstract Set<DrasylPlugin> getPlugins();

    public abstract Map<String, Serializer> getSerializationSerializers();

    public abstract Map<Class<?>, String> getSerializationsBindingsInbound();

    public abstract Map<Class<?>, String> getSerializationsBindingsOutbound();

    public abstract Duration getChannelInactivityTimeout();

    @SuppressWarnings("java:S118")
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder networkId(final int networkId);

        public abstract Builder identityProofOfWork(final ProofOfWork identityProofOfWork);

        public abstract Builder identitySecretKey(final IdentitySecretKey identitySecretKey);

        /**
         * Shortcut for calling {@link #identityProofOfWork(ProofOfWork)}, and
         * {@link #identitySecretKey(IdentitySecretKey)}.
         */
        public Builder identity(final Identity identity) {
            return identityProofOfWork(identity.getProofOfWork()).identitySecretKey(identity.getIdentitySecretKey());
        }

        public abstract Builder identityPath(final Path identityPath);

        public abstract Builder messageBufferSize(final int messageBufferSize);

        public abstract Builder remoteBindHost(final InetAddress remoteBindHost);

        public abstract Builder remoteEnabled(final boolean remoteEnabled);

        public abstract Builder remoteBindPort(final int remoteBindPort);

        public abstract Builder remotePingInterval(final Duration remotePingInterval);

        public abstract Builder remotePingTimeout(final Duration remotePingTimeout);

        public abstract Builder remotePingCommunicationTimeout(final Duration remotePingCommunicationTimeout);

        public abstract Builder remoteUniteMinInterval(final Duration remoteUniteMinInterval);

        public abstract Builder remotePingMaxPeers(final int remotePingMaxPeers);

        public abstract Builder remoteEndpoints(final Set<PeerEndpoint> remoteEndpoints);

        public abstract Builder remoteExposeEnabled(final boolean remoteExposeEnabled);

        public abstract Builder remoteStaticRoutes(final Map<DrasylAddress, InetSocketAddress> remoteStaticRoutes);

        public abstract Builder remoteHandshakeTimeout(final Duration remoteHandshakeTimeout);

        public abstract Builder remoteMessageMtu(final int remoteMessageMtu);

        public abstract Builder remoteMessageMaxContentLength(final int remoteMessageMaxContentLength);

        public abstract Builder remoteMessageHopLimit(final byte remoteMessageHopLimit);

        public abstract Builder remoteMessageArmProtocolEnabled(final boolean remoteMessageArmProtocolEnabled);

        public abstract Builder remoteMessageArmProtocolSessionMaxCount(final int remoteMessageArmProtocolSessionMaxCount);

        public abstract Builder remoteMessageArmProtocolSessionExpireAfter(final Duration remoteMessageArmProtocolSessionExpireAfter);

        public abstract Builder remoteMessageArmApplicationEnabled(final boolean remoteMessageArmApplicationEnabled);

        public abstract Builder remoteMessageArmApplicationAgreementMaxCount(final int remoteMessageArmApplicationAgreementMaxCount);

        public abstract Builder remoteMessageArmApplicationAgreementExpireAfter(final Duration remoteMessageArmApplicationAgreementExpireAfter);

        public abstract Builder remoteMessageArmApplicationAgreementRetryInterval(final Duration remoteMessageArmApplicationAgreementRetryInterval);

        public abstract Builder remoteMessageArqEnabled(final boolean remoteMessageArqEnabled);

        public abstract Builder remoteMessageArqWindowSize(final int remoteMessageArqWindowSize);

        public abstract Builder remoteMessageArqRetryTimeout(final Duration remoteMessageArqRetryTimeout);

        public abstract Builder remoteMessageArqDeadPeerTimeout(final Duration remoteMessageArqDeadPeerTimeout);

        public abstract Builder remoteMessageArqClock(final Duration remoteMessageArqClock);

        public abstract Builder remoteMessageComposedMessageTransferTimeout(final Duration messageComposedMessageTransferTimeout);

        public abstract Builder remoteSuperPeerEnabled(final boolean remoteSuperPeerEnabled);

        public abstract Builder remoteSuperPeerEndpoints(final Set<PeerEndpoint> remoteSuperPeerEndpoints);

        public abstract Builder intraVmDiscoveryEnabled(final boolean intraVmDiscoveryEnabled);

        public abstract Builder remoteLocalHostDiscoveryEnabled(final boolean remoteLocalHostDiscoveryEnabled);

        public abstract Builder remoteLocalHostDiscoveryPath(final Path remoteLocalHostDiscoveryPath);

        public abstract Builder remoteLocalHostDiscoveryLeaseTime(final Duration remoteLocalHostDiscoveryLeaseTime);

        public abstract Builder remoteLocalHostDiscoveryWatchEnabled(final boolean remoteLocalHostDiscoveryWatchEnabled);

        public abstract Builder remoteLocalNetworkDiscoveryEnabled(final boolean remoteLocalNetworkDiscoveryEnabled);

        public abstract Builder remoteTcpFallbackEnabled(final boolean remoteTcpFallbackEnabled);

        public abstract Builder remoteTcpFallbackServerBindHost(final InetAddress remoteTcpFallbackServerBindHost);

        public abstract Builder remoteTcpFallbackServerBindPort(final int remoteTcpFallbackServerBindPort);

        public abstract Builder remoteTcpFallbackClientTimeout(final Duration remoteTcpFallbackClientTimeout);

        public abstract Builder remoteTcpFallbackClientAddress(final InetSocketAddress remoteTcpFallbackClientAddress);

        public abstract Builder plugins(final Set<DrasylPlugin> plugins);

        public abstract Builder serializationSerializers(final Map<String, Serializer> serializationSerializers);

        public abstract Builder serializationsBindingsInbound(final Map<Class<?>, String> serializationsBindingsInbound);

        public abstract Builder serializationsBindingsOutbound(final Map<Class<?>, String> serializationsBindingsOutbound);

        abstract Builder channelInactivityTimeout(final Duration channelInactivityTimeout);

        abstract DrasylConfig autoBuild();

        @SuppressWarnings({ "java:S1192", "java:S1541", "java:S3776" })
        public DrasylConfig build() {
            final DrasylConfig config = autoBuild();
            if (config.getMessageBufferSize() < 0) {
                throw new DrasylConfigException(MESSAGE_BUFFER_SIZE, "Must be a non-negative value.");
            }
            if (config.getRemotePingInterval().isNegative() || config.getRemotePingInterval().isZero()) {
                throw new DrasylConfigException(REMOTE_PING_INTERVAL, "Must be a positive value.");
            }
            if (config.getRemotePingTimeout().isNegative() || config.getRemotePingTimeout().isZero()) {
                throw new DrasylConfigException(REMOTE_PING_TIMEOUT, "Must be a positive value.");
            }
            if (config.getRemotePingCommunicationTimeout().isNegative() || config.getRemotePingCommunicationTimeout().isZero()) {
                throw new DrasylConfigException(REMOTE_PING_COMMUNICATION_TIMEOUT, "Must be a positive value.");
            }
            if (config.getRemotePingMaxPeers() < 0) {
                throw new DrasylConfigException(REMOTE_PING_COMMUNICATION_TIMEOUT, "Must be a non-negative value.");
            }
            if (config.getRemoteUniteMinInterval().isNegative()) {
                throw new DrasylConfigException(REMOTE_UNITE_MIN_INTERVAL, "Must be a non-negative value.");
            }
            if (config.isRemoteEnabled() && config.isRemoteSuperPeerEnabled()) {
                for (final PeerEndpoint endpoint : config.getRemoteSuperPeerEndpoints()) {
                    if (endpoint.getNetworkId() != null && !endpoint.getNetworkId().equals(config.getNetworkId())) {
                        throw new DrasylConfigException(REMOTE_SUPER_PEER_ENDPOINTS, "super peer's network id `" + endpoint.getNetworkId() + "` does not match your network id `" + config.getNetworkId() + "`: " + endpoint);
                    }
                }
            }
            if (config.getRemoteLocalHostDiscoveryLeaseTime().isNegative() || config.getRemoteLocalHostDiscoveryLeaseTime().isZero()) {
                throw new DrasylConfigException(REMOTE_LOCAL_HOST_DISCOVERY_LEASE_TIME, "Must be a positive value.");
            }
            if (config.getRemoteMessageMtu() < 1) {
                throw new DrasylConfigException(REMOTE_MESSAGE_MTU, "Must be a positive value.");
            }
            if (config.getRemoteMessageMaxContentLength() < 0) {
                throw new DrasylConfigException(REMOTE_MESSAGE_MAX_CONTENT_LENGTH, "Must be a non-negative value.");
            }
            if (config.getRemoteMessageComposedMessageTransferTimeout().isNegative()) {
                throw new DrasylConfigException(REMOTE_MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT, "Must be a positive value.");
            }
            if (config.getChannelInactivityTimeout().isNegative()) {
                throw new DrasylConfigException(REMOTE_UNITE_MIN_INTERVAL, "Must be a non-negative value.");
            }
            return config;
        }
    }
}
