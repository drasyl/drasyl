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
import io.netty.util.internal.StringUtil;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.IdentitySecretKey;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.identity.KeyAgreementSecretKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.node.handler.plugin.DrasylPlugin;
import org.drasyl.node.handler.serialization.Serializer;
import org.drasyl.util.internal.Nullable;
import org.drasyl.util.internal.UnstableApi;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
import java.util.Map.Entry;
import java.util.Set;

import static org.drasyl.util.InetSocketAddressUtil.socketAddressFromString;

/**
 * This class represents the configuration for a {@link DrasylNode}. For example, it defines the
 * identity and the Super Peer.
 * <p>
 * This is an immutable object.
 */
@UnstableApi
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
    public static final String REMOTE_MESSAGE_HOP_LIMIT = "drasyl.remote.message.hop-limit";
    public static final String REMOTE_MESSAGE_ARM_PROTOCOL_ENABLED = "drasyl.remote.message.arm.protocol.enabled";
    public static final String REMOTE_MESSAGE_ARM_PROTOCOL_SESSION_MAX_COUNT = "drasyl.remote.message.arm.protocol.session.max-count";
    public static final String REMOTE_MESSAGE_ARM_PROTOCOL_SESSION_EXPIRE_AFTER = "drasyl.remote.message.arm.protocol.session.expire-after";
    public static final String REMOTE_MESSAGE_ARM_APPLICATION_ENABLED = "drasyl.remote.message.arm.application.enabled";
    public static final String REMOTE_MESSAGE_ARM_APPLICATION_AGREEMENT_MAX_COUNT = "drasyl.remote.message.arm.application.agreement.max-count";
    public static final String REMOTE_MESSAGE_ARM_APPLICATION_AGREEMENT_EXPIRE_AFTER = "drasyl.remote.message.arm.application.agreement.expire-after";
    public static final String REMOTE_MESSAGE_ARM_APPLICATION_AGREEMENT_RETRY_INTERVAL = "drasyl.remote.message.arm.application.agreement.retry-interval";
    public static final String REMOTE_TCP_FALLBACK_ENABLED = "drasyl.remote.tcp-fallback.enabled";
    public static final String REMOTE_TCP_FALLBACK_SERVER_BIND_HOST = "drasyl.remote.tcp-fallback.server.bind-host";
    public static final String REMOTE_TCP_FALLBACK_SERVER_BIND_PORT = "drasyl.remote.tcp-fallback.server.bind-port";
    public static final String REMOTE_TCP_FALLBACK_CLIENT_CONNECT_PORT = "drasyl.remote.tcp-fallback.client.connect-port";
    public static final String INTRA_VM_DISCOVERY_ENABLED = "drasyl.intra-vm-discovery.enabled";
    public static final String CHANNEL_INACTIVITY_TIMEOUT = "drasyl.channel.inactivity-timeout";
    public static final String PLUGINS = "drasyl.plugins";
    public static final String SNTP_SERVER = "drasyl.sntp-server";
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
            builder.remoteMessageHopLimit(getByte(config, REMOTE_MESSAGE_HOP_LIMIT));
            builder.remoteTcpFallbackEnabled(config.getBoolean(REMOTE_TCP_FALLBACK_ENABLED));
            builder.remoteTcpFallbackServerBindHost(getInetAddress(config, REMOTE_TCP_FALLBACK_SERVER_BIND_HOST));
            builder.remoteTcpFallbackServerBindPort(config.getInt(REMOTE_TCP_FALLBACK_SERVER_BIND_PORT));
            builder.remoteTcpFallbackClientConnectPort(config.getInt(REMOTE_TCP_FALLBACK_CLIENT_CONNECT_PORT));

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

            // intra vm discovery
            builder.intraVmDiscoveryEnabled(config.getBoolean(INTRA_VM_DISCOVERY_ENABLED));

            // plugins
            builder.plugins(Set.copyOf(getPlugins(config, PLUGINS)));

            // sntp server
            builder.sntpServers(getInetSocketAddressList(config, SNTP_SERVER));

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
            for (final Entry<String, ConfigValue> entry : config.getObject(path).entrySet()) {
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

            for (final Entry<String, ConfigValue> entry : config.getObject(path).entrySet()) {
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
            for (final Entry<String, ConfigValue> entry : config.getObject(path).entrySet()) {
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
    public static List<InetSocketAddress> getInetSocketAddressList(final Config config,
                                                                   final String path) {
        try {
            final List<InetSocketAddress> addresses = new ArrayList<>();
            for (final ConfigValue value : config.getList(path)) {
                final InetSocketAddress address = socketAddressFromString(value.atKey("address").getString("address"));
                if (address.getPort() < 1) {
                    throw new IllegalArgumentException("port missing");
                }
                addresses.add(address);
            }

            return addresses;
        }
        catch (final IllegalArgumentException | ConfigException e) {
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
            for (final Entry<String, ConfigValue> entry : config.getObject(path).entrySet()) {
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
     * Parses a file into a Config instance.
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
     * Parses a file path into a Config instance.
     *
     * @param path the path to file to parse
     * @return the parsed configuration
     * @throws DrasylConfigException on IO or parse errors
     */
    public static DrasylConfig parseFile(final Path path) {
        return parseFile(path.toFile());
    }

    /**
     * Parses a file path into a Config instance.
     *
     * @param path the path to file to parse
     * @return the parsed configuration
     * @throws DrasylConfigException on IO or parse errors
     */
    public static DrasylConfig parseFile(final String path) {
        return parseFile(Path.of(path));
    }

    /**
     * Parses a string into a Config instance.
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

    @Deprecated
    public abstract int getMessageBufferSize();

    @Deprecated
    public abstract boolean isRemoteEnabled();

    @Deprecated
    public abstract InetAddress getRemoteBindHost();

    public abstract int getRemoteBindPort();

    @Deprecated
    public abstract boolean isRemoteExposeEnabled();

    @Deprecated
    public abstract Duration getRemotePingInterval();

    public abstract Duration getRemotePingTimeout();

    @Deprecated
    public abstract Duration getRemotePingCommunicationTimeout();

    public abstract int getRemotePingMaxPeers();

    @Deprecated
    public abstract Duration getRemoteUniteMinInterval();

    @Deprecated
    public abstract boolean isRemoteSuperPeerEnabled();

    public abstract Set<PeerEndpoint> getRemoteSuperPeerEndpoints();

    @Deprecated
    public abstract Map<DrasylAddress, InetSocketAddress> getRemoteStaticRoutes();

    @Deprecated
    public abstract Duration getRemoteHandshakeTimeout();

    @Deprecated
    public abstract boolean isRemoteLocalHostDiscoveryEnabled();

    @Deprecated
    public abstract Path getRemoteLocalHostDiscoveryPath();

    @Deprecated
    public abstract Duration getRemoteLocalHostDiscoveryLeaseTime();

    @Deprecated
    public abstract boolean isRemoteLocalHostDiscoveryWatchEnabled();

    @Deprecated
    public abstract boolean isRemoteLocalNetworkDiscoveryEnabled();

    @Deprecated
    public abstract byte getRemoteMessageHopLimit();

    @Deprecated
    public abstract boolean isRemoteMessageArmProtocolEnabled();

    @Deprecated
    public abstract int getRemoteMessageArmProtocolSessionMaxCount();

    @Deprecated
    public abstract Duration getRemoteMessageArmProtocolSessionExpireAfter();

    public abstract boolean isRemoteMessageArmApplicationEnabled();

    public abstract int getRemoteMessageArmApplicationAgreementMaxCount();

    public abstract Duration getRemoteMessageArmApplicationAgreementExpireAfter();

    public abstract Duration getRemoteMessageArmApplicationAgreementRetryInterval();

    @Deprecated
    public abstract boolean isRemoteTcpFallbackEnabled();

    @Deprecated
    public abstract InetAddress getRemoteTcpFallbackServerBindHost();

    @Deprecated
    public abstract int getRemoteTcpFallbackServerBindPort();

    @Deprecated
    public abstract int getRemoteTcpFallbackClientConnectPort();

    public abstract boolean isIntraVmDiscoveryEnabled();

    @Deprecated
    public abstract List<SocketAddress> getSntpServers();

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

        @Deprecated
        public abstract Builder messageBufferSize(final int messageBufferSize);

        @Deprecated
        public abstract Builder remoteBindHost(final InetAddress remoteBindHost);

        @Deprecated
        public abstract Builder remoteEnabled(final boolean remoteEnabled);

        public abstract Builder remoteBindPort(final int remoteBindPort);

        @Deprecated
        public abstract Builder remotePingInterval(final Duration remotePingInterval);

        public abstract Builder remotePingTimeout(final Duration remotePingTimeout);

        @Deprecated
        public abstract Builder remotePingCommunicationTimeout(final Duration remotePingCommunicationTimeout);

        @Deprecated
        public abstract Builder remoteUniteMinInterval(final Duration remoteUniteMinInterval);

        public abstract Builder remotePingMaxPeers(final int remotePingMaxPeers);

        @Deprecated
        public abstract Builder remoteExposeEnabled(final boolean remoteExposeEnabled);

        @Deprecated
        public abstract Builder remoteStaticRoutes(final Map<DrasylAddress, InetSocketAddress> remoteStaticRoutes);

        @Deprecated
        public abstract Builder remoteHandshakeTimeout(final Duration remoteHandshakeTimeout);

        @Deprecated
        public abstract Builder remoteMessageHopLimit(final byte remoteMessageHopLimit);

        @Deprecated
        public abstract Builder remoteMessageArmProtocolEnabled(final boolean remoteMessageArmProtocolEnabled);

        @Deprecated
        public abstract Builder remoteMessageArmProtocolSessionMaxCount(final int remoteMessageArmProtocolSessionMaxCount);

        @Deprecated
        public abstract Builder remoteMessageArmProtocolSessionExpireAfter(final Duration remoteMessageArmProtocolSessionExpireAfter);

        public abstract Builder remoteMessageArmApplicationEnabled(final boolean remoteMessageArmApplicationEnabled);

        public abstract Builder remoteMessageArmApplicationAgreementMaxCount(final int remoteMessageArmApplicationAgreementMaxCount);

        public abstract Builder remoteMessageArmApplicationAgreementExpireAfter(final Duration remoteMessageArmApplicationAgreementExpireAfter);

        public abstract Builder remoteMessageArmApplicationAgreementRetryInterval(final Duration remoteMessageArmApplicationAgreementRetryInterval);

        @Deprecated
        public abstract Builder remoteSuperPeerEnabled(final boolean remoteSuperPeerEnabled);

        public abstract Builder remoteSuperPeerEndpoints(final Set<PeerEndpoint> remoteSuperPeerEndpoints);

        public abstract Builder intraVmDiscoveryEnabled(final boolean intraVmDiscoveryEnabled);

        @Deprecated
        public abstract Builder remoteLocalHostDiscoveryEnabled(final boolean remoteLocalHostDiscoveryEnabled);

        @Deprecated
        public abstract Builder remoteLocalHostDiscoveryPath(final Path remoteLocalHostDiscoveryPath);

        @Deprecated
        public abstract Builder remoteLocalHostDiscoveryLeaseTime(final Duration remoteLocalHostDiscoveryLeaseTime);

        @Deprecated
        public abstract Builder remoteLocalHostDiscoveryWatchEnabled(final boolean remoteLocalHostDiscoveryWatchEnabled);

        @Deprecated
        public abstract Builder remoteLocalNetworkDiscoveryEnabled(final boolean remoteLocalNetworkDiscoveryEnabled);

        @Deprecated
        public abstract Builder remoteTcpFallbackEnabled(final boolean remoteTcpFallbackEnabled);

        @Deprecated
        public abstract Builder remoteTcpFallbackServerBindHost(final InetAddress remoteTcpFallbackServerBindHost);

        @Deprecated
        public abstract Builder remoteTcpFallbackServerBindPort(final int remoteTcpFallbackServerBindPort);

        @Deprecated
        public abstract Builder remoteTcpFallbackClientConnectPort(final int remoteTcpFallbackClientConnectPort);

        public abstract Builder plugins(final Set<DrasylPlugin> plugins);

        @Deprecated
        public abstract Builder sntpServers(final List<InetSocketAddress> sntpServers);

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
            if (config.getChannelInactivityTimeout().isNegative()) {
                throw new DrasylConfigException(REMOTE_UNITE_MIN_INTERVAL, "Must be a non-negative value.");
            }
            for (final Entry<Class<?>, String> entry : config.getSerializationsBindingsInbound().entrySet()) {
                final Class<?> clazz = entry.getKey();
                final String serializerName = entry.getValue();

                if (!config.getSerializationSerializers().containsKey(serializerName)) {
                    throw new DrasylConfigException(SERIALIZATION_BINDINGS_INBOUND, "Inbound binding for class `" + StringUtil.simpleClassName(clazz) + "` points to non-existing serializer `" + serializerName + "`.");
                }
            }
            for (final Entry<Class<?>, String> entry : config.getSerializationsBindingsOutbound().entrySet()) {
                final Class<?> clazz = entry.getKey();
                final String serializerName = entry.getValue();

                if (!config.getSerializationSerializers().containsKey(serializerName)) {
                    throw new DrasylConfigException(SERIALIZATION_BINDINGS_OUTBOUND, "Outbound binding for class `" + StringUtil.simpleClassName(clazz) + "` points to non-existing serializer `" + serializerName + "`.");
                }
            }
            return config;
        }
    }
}
