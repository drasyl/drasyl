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
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigObject;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.plugin.DrasylPlugin;
import org.drasyl.serialization.Serializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.time.Duration.ofSeconds;
import static org.drasyl.DrasylConfig.DEFAULT;
import static org.drasyl.DrasylConfig.IDENTITY_PATH;
import static org.drasyl.DrasylConfig.IDENTITY_PRIVATE_KEY;
import static org.drasyl.DrasylConfig.IDENTITY_PROOF_OF_WORK;
import static org.drasyl.DrasylConfig.IDENTITY_PUBLIC_KEY;
import static org.drasyl.DrasylConfig.INTRA_VM_DISCOVERY_ENABLED;
import static org.drasyl.DrasylConfig.MESSAGE_BUFFER_SIZE;
import static org.drasyl.DrasylConfig.MONITORING_ENABLED;
import static org.drasyl.DrasylConfig.MONITORING_HOST_TAG;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_DATABASE;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_PASSWORD;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_REPORTING_FREQUENCY;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_URI;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_USER;
import static org.drasyl.DrasylConfig.NETWORK_ID;
import static org.drasyl.DrasylConfig.PLUGINS;
import static org.drasyl.DrasylConfig.REMOTE_BIND_HOST;
import static org.drasyl.DrasylConfig.REMOTE_BIND_PORT;
import static org.drasyl.DrasylConfig.REMOTE_ENABLED;
import static org.drasyl.DrasylConfig.REMOTE_ENDPOINTS;
import static org.drasyl.DrasylConfig.REMOTE_EXPOSE_ENABLED;
import static org.drasyl.DrasylConfig.REMOTE_LOCAL_HOST_DISCOVERY_ENABLED;
import static org.drasyl.DrasylConfig.REMOTE_LOCAL_HOST_DISCOVERY_LEASE_TIME;
import static org.drasyl.DrasylConfig.REMOTE_LOCAL_HOST_DISCOVERY_PATH;
import static org.drasyl.DrasylConfig.REMOTE_LOCAL_HOST_DISCOVERY_WATCH_ENABLED;
import static org.drasyl.DrasylConfig.REMOTE_MESSAGE_ARM_ENABLED;
import static org.drasyl.DrasylConfig.REMOTE_MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT;
import static org.drasyl.DrasylConfig.REMOTE_MESSAGE_HOP_LIMIT;
import static org.drasyl.DrasylConfig.REMOTE_MESSAGE_MAX_CONTENT_LENGTH;
import static org.drasyl.DrasylConfig.REMOTE_MESSAGE_MTU;
import static org.drasyl.DrasylConfig.REMOTE_PING_COMMUNICATION_TIMEOUT;
import static org.drasyl.DrasylConfig.REMOTE_PING_INTERVAL;
import static org.drasyl.DrasylConfig.REMOTE_PING_TIMEOUT;
import static org.drasyl.DrasylConfig.REMOTE_SUPER_PEER_ENABLED;
import static org.drasyl.DrasylConfig.REMOTE_SUPER_PEER_ENDPOINTS;
import static org.drasyl.DrasylConfig.REMOTE_TCP_FALLBACK_CLIENT_ADDRESS;
import static org.drasyl.DrasylConfig.REMOTE_TCP_FALLBACK_CLIENT_TIMEOUT;
import static org.drasyl.DrasylConfig.REMOTE_TCP_FALLBACK_ENABLED;
import static org.drasyl.DrasylConfig.REMOTE_TCP_FALLBACK_SERVER_BIND_HOST;
import static org.drasyl.DrasylConfig.REMOTE_TCP_FALLBACK_SERVER_BIND_PORT;
import static org.drasyl.DrasylConfig.SERIALIZATION_BINDINGS_INBOUND;
import static org.drasyl.DrasylConfig.SERIALIZATION_BINDINGS_OUTBOUND;
import static org.drasyl.DrasylConfig.SERIALIZATION_SERIALIZERS;
import static org.drasyl.DrasylConfig.getByte;
import static org.drasyl.DrasylConfig.getEndpoint;
import static org.drasyl.DrasylConfig.getEndpointList;
import static org.drasyl.DrasylConfig.getInetAddress;
import static org.drasyl.DrasylConfig.getInetSocketAddress;
import static org.drasyl.DrasylConfig.getPath;
import static org.drasyl.DrasylConfig.getPlugins;
import static org.drasyl.DrasylConfig.getPrivateKey;
import static org.drasyl.DrasylConfig.getPublicKey;
import static org.drasyl.DrasylConfig.getSerializationBindings;
import static org.drasyl.DrasylConfig.getSerializationSerializers;
import static org.drasyl.DrasylConfig.getStaticRoutes;
import static org.drasyl.DrasylConfig.getURI;
import static org.drasyl.util.network.NetworkUtil.createInetAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class DrasylConfigTest {
    private int networkId;
    @Mock
    private CompressedPublicKey identityPublicKey;
    @Mock
    private ProofOfWork identityProofOfWork;
    @Mock
    private CompressedPrivateKey identityPrivateKey;
    @Mock
    private Path identityPath;
    private int messageBufferSize;
    private InetAddress remoteBindHost;
    private boolean remoteEnabled;
    private int remoteBindPort;
    private Duration remotePingInterval;
    private Set<Endpoint> serverEndpoints;
    private boolean remoteExposeEnabled;
    @SuppressWarnings("unused")
    private int remoteMessageMtu;
    private int remoteMessageMaxContentLength;
    private byte remoteMessageHopLimit;
    private boolean remoteMessageArmEnabled;
    private boolean superPeerEnabled;
    private Set<Endpoint> superPeerEndpoints;
    private Map<CompressedPublicKey, InetSocketAddressWrapper> remoteStaticRoutes;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Config typesafeConfig;
    private String identityPathAsString;
    private boolean intraVmDiscoveryEnabled;
    private boolean remoteLocalHostDiscoveryEnabled;
    private String remoteLocalHostDiscoveryPathAsString;
    private Duration remoteLocalHostDiscoveryLeaseTime;
    private boolean remoteLocalHostDiscoveryWatchEnabled;
    private Duration composedMessageTransferTimeout;
    private boolean remoteLocalNetworkDiscoveryEnabled;
    private boolean remoteTcpFallbackEnabled;
    private InetAddress remoteTcpFallbackServerBindHost;
    private int remoteTcpFallbackServerBindPort;
    private Duration remoteTcpFallbackClientTimeout;
    private InetSocketAddress remoteTcpFallbackClientAddress;
    private boolean monitoringEnabled;
    private String monitoringHostTag;
    private URI monitoringInfluxUri;
    private String monitoringInfluxUser;
    private String monitoringInfluxPassword;
    private String monitoringInfluxDatabase;
    private Duration monitoringInfluxReportingFrequency;
    private Set<DrasylPlugin> plugins;
    private Map<String, Serializer> serializationSerializers;
    private Map<Class<?>, String> serializationsBindingsInbound;
    private Map<Class<?>, String> serializationsBindingsOutbound;
    @SuppressWarnings("unused")
    private Duration remotePingTimeout;
    @SuppressWarnings("unused")
    private Duration remotePingCommunicationTimeout;
    @SuppressWarnings("unused")
    private Duration remoteUniteMinInterval;
    @SuppressWarnings("unused")
    private int remotePingMaxPeers;

    @BeforeEach
    void setUp() {
        networkId = 1337;
        remoteBindHost = createInetAddress("0.0.0.0");
        remoteEnabled = true;
        remoteBindPort = 22527;
        remotePingInterval = ofSeconds(30);
        serverEndpoints = Set.of();
        remoteExposeEnabled = true;
        remoteMessageMaxContentLength = 1024;
        remoteMessageHopLimit = (byte) 64;
        remoteMessageArmEnabled = false;
        superPeerEnabled = true;
        superPeerEndpoints = Set.of(Endpoint.of("udp://foo.bar:123?publicKey=030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22&networkId=1337"));
        remoteTcpFallbackEnabled = true;
        remoteTcpFallbackServerBindHost = createInetAddress("0.0.0.0");
        remoteTcpFallbackServerBindPort = 443;
        remoteTcpFallbackClientTimeout = ofSeconds(60);
        remoteTcpFallbackClientAddress = InetSocketAddress.createUnresolved("127.0.0.1", 443);
        remoteStaticRoutes = Map.of();
        identityPathAsString = "drasyl.identity.json";
        messageBufferSize = 0;
        intraVmDiscoveryEnabled = true;
        remoteLocalHostDiscoveryEnabled = true;
        remoteLocalHostDiscoveryPathAsString = "foo/bar";
        remoteLocalHostDiscoveryLeaseTime = ofSeconds(40);
        remoteLocalHostDiscoveryWatchEnabled = true;
        composedMessageTransferTimeout = ofSeconds(60);
        remoteLocalNetworkDiscoveryEnabled = true;
        monitoringEnabled = true;
        monitoringHostTag = "test.example.com";
        monitoringInfluxUri = URI.create("http://localhost:8086");
        monitoringInfluxUser = "";
        monitoringInfluxPassword = "";
        monitoringInfluxDatabase = "drasyl";
        monitoringInfluxReportingFrequency = ofSeconds(70);
        plugins = Set.of();
        serializationSerializers = Map.of("string", new MySerializer());
        serializationsBindingsInbound = Map.of();
        serializationsBindingsOutbound = Map.of();
        remotePingCommunicationTimeout = ofSeconds(80);
        remoteUniteMinInterval = ofSeconds(90);
        remotePingTimeout = ofSeconds(10);
        remoteMessageMtu = 1024;
    }

    @Nested
    class Constructor {
        @Test
        @SuppressWarnings("java:S5961")
        void shouldReadConfigProperly(@Mock final ConfigObject configObject) {
            when(typesafeConfig.getInt(NETWORK_ID)).thenReturn(networkId);
            when(typesafeConfig.getInt(IDENTITY_PROOF_OF_WORK)).thenReturn(-1);
            when(typesafeConfig.getString(IDENTITY_PUBLIC_KEY)).thenReturn("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3");
            when(typesafeConfig.getString(IDENTITY_PRIVATE_KEY)).thenReturn("0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d");
            when(typesafeConfig.getInt(IDENTITY_PROOF_OF_WORK)).thenReturn(123);
            when(typesafeConfig.getString(IDENTITY_PATH)).thenReturn(identityPathAsString);
            when(typesafeConfig.getInt(MESSAGE_BUFFER_SIZE)).thenReturn(messageBufferSize);
            when(typesafeConfig.getBoolean(REMOTE_ENABLED)).thenReturn(remoteEnabled);
            when(typesafeConfig.getString(REMOTE_BIND_HOST)).thenReturn(remoteBindHost.getHostAddress());
            when(typesafeConfig.getInt(REMOTE_BIND_PORT)).thenReturn(remoteBindPort);
            when(typesafeConfig.getDuration(REMOTE_PING_INTERVAL)).thenReturn(remotePingInterval);
            when(typesafeConfig.getDuration(REMOTE_PING_TIMEOUT)).thenReturn(remotePingTimeout);
            when(typesafeConfig.getDuration(REMOTE_PING_COMMUNICATION_TIMEOUT)).thenReturn(remotePingCommunicationTimeout);
            when(typesafeConfig.getMemorySize(REMOTE_MESSAGE_MTU)).thenReturn(ConfigMemorySize.ofBytes(remoteMessageMtu));
            when(typesafeConfig.getMemorySize(REMOTE_MESSAGE_MAX_CONTENT_LENGTH)).thenReturn(ConfigMemorySize.ofBytes(remoteMessageMaxContentLength));
            when(typesafeConfig.getInt(REMOTE_MESSAGE_HOP_LIMIT)).thenReturn((int) remoteMessageHopLimit);
            when(typesafeConfig.getBoolean(REMOTE_MESSAGE_ARM_ENABLED)).thenReturn(remoteMessageArmEnabled);
            when(typesafeConfig.getStringList(REMOTE_ENDPOINTS)).thenReturn(List.of());
            when(typesafeConfig.getBoolean(REMOTE_EXPOSE_ENABLED)).thenReturn(remoteExposeEnabled);
            when(typesafeConfig.getBoolean(REMOTE_SUPER_PEER_ENABLED)).thenReturn(superPeerEnabled);
            when(typesafeConfig.getStringList(REMOTE_SUPER_PEER_ENDPOINTS)).thenReturn(List.of("udp://foo.bar:123?publicKey=030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22&networkId=1337"));
            when(typesafeConfig.getBoolean(REMOTE_LOCAL_HOST_DISCOVERY_ENABLED)).thenReturn(remoteLocalHostDiscoveryEnabled);
            when(typesafeConfig.getString(REMOTE_LOCAL_HOST_DISCOVERY_PATH)).thenReturn(remoteLocalHostDiscoveryPathAsString);
            when(typesafeConfig.getDuration(REMOTE_LOCAL_HOST_DISCOVERY_LEASE_TIME)).thenReturn(remoteLocalHostDiscoveryLeaseTime);
            when(typesafeConfig.getBoolean(REMOTE_LOCAL_HOST_DISCOVERY_WATCH_ENABLED)).thenReturn(remoteLocalHostDiscoveryWatchEnabled);
            when(typesafeConfig.getDuration(REMOTE_MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT)).thenReturn(composedMessageTransferTimeout);
            when(typesafeConfig.getBoolean(REMOTE_TCP_FALLBACK_ENABLED)).thenReturn(remoteTcpFallbackEnabled);
            when(typesafeConfig.getString(REMOTE_TCP_FALLBACK_SERVER_BIND_HOST)).thenReturn(remoteTcpFallbackServerBindHost.getHostAddress());
            when(typesafeConfig.getInt(REMOTE_TCP_FALLBACK_SERVER_BIND_PORT)).thenReturn(remoteTcpFallbackServerBindPort);
            when(typesafeConfig.getDuration(REMOTE_TCP_FALLBACK_CLIENT_TIMEOUT)).thenReturn(remoteTcpFallbackClientTimeout);
            when(typesafeConfig.getString(REMOTE_TCP_FALLBACK_CLIENT_ADDRESS)).thenReturn(remoteTcpFallbackClientAddress.getHostName() + ":" + remoteTcpFallbackClientAddress.getPort());
            when(typesafeConfig.getBoolean(INTRA_VM_DISCOVERY_ENABLED)).thenReturn(intraVmDiscoveryEnabled);
            when(typesafeConfig.getBoolean(MONITORING_ENABLED)).thenReturn(monitoringEnabled);
            when(typesafeConfig.getString(MONITORING_HOST_TAG)).thenReturn(monitoringHostTag);
            when(typesafeConfig.getString(MONITORING_INFLUX_URI)).thenReturn(monitoringInfluxUri.toString());
            when(typesafeConfig.getString(MONITORING_INFLUX_USER)).thenReturn(monitoringInfluxUser);
            when(typesafeConfig.getString(MONITORING_INFLUX_PASSWORD)).thenReturn(monitoringInfluxPassword);
            when(typesafeConfig.getString(MONITORING_INFLUX_DATABASE)).thenReturn(monitoringInfluxDatabase);
            when(typesafeConfig.getDuration(MONITORING_INFLUX_REPORTING_FREQUENCY)).thenReturn(monitoringInfluxReportingFrequency);
            when(typesafeConfig.getObject(SERIALIZATION_SERIALIZERS)).thenReturn(ConfigFactory.parseString("serializers { string = \"" + MySerializer.class.getName() + "\" }").getObject("serializers"));
            when(typesafeConfig.getObject(SERIALIZATION_BINDINGS_INBOUND)).thenReturn(configObject);
            when(typesafeConfig.getObject(SERIALIZATION_BINDINGS_OUTBOUND)).thenReturn(configObject);
            when(typesafeConfig.getObject(PLUGINS)).thenReturn(ConfigFactory.parseString("plugins { \"" + MyPlugin.class.getName() + "\" { enabled = true } }").getObject("plugins"));

            final DrasylConfig config = new DrasylConfig(typesafeConfig);

            assertEquals(networkId, config.getNetworkId());
            assertEquals(remoteBindHost, config.getRemoteBindHost());
            assertEquals(remoteBindPort, config.getRemoteBindPort());
            assertEquals(ProofOfWork.of(123), config.getIdentityProofOfWork());
            assertEquals(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), config.getIdentityPublicKey());
            assertEquals(CompressedPrivateKey.of("0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d"), config.getIdentityPrivateKey());
            assertEquals(Paths.get("drasyl.identity.json"), config.getIdentityPath());
            assertEquals(messageBufferSize, config.getMessageBufferSize());
            assertEquals(remoteEnabled, config.isRemoteEnabled());
            assertEquals(remotePingInterval, config.getRemotePingInterval());
            assertEquals(Set.of(), config.getRemoteEndpoints());
            assertEquals(remoteExposeEnabled, config.isRemoteExposeEnabled());
            assertEquals(remoteMessageMtu, config.getRemoteMessageMtu());
            assertEquals(remoteMessageMaxContentLength, config.getRemoteMessageMaxContentLength());
            assertEquals(remoteMessageHopLimit, config.getRemoteMessageHopLimit());
            assertEquals(remoteMessageArmEnabled, config.isRemoteMessageArmEnabled());
            assertEquals(superPeerEnabled, config.isRemoteSuperPeerEnabled());
            assertEquals(superPeerEndpoints, config.getRemoteSuperPeerEndpoints());
            assertEquals(remoteLocalHostDiscoveryEnabled, config.isRemoteLocalHostDiscoveryEnabled());
            assertEquals(Path.of(remoteLocalHostDiscoveryPathAsString), config.getRemoteLocalHostDiscoveryPath());
            assertEquals(remoteLocalHostDiscoveryLeaseTime, config.getRemoteLocalHostDiscoveryLeaseTime());
            assertEquals(remoteLocalHostDiscoveryWatchEnabled, config.isRemoteLocalHostDiscoveryWatchEnabled());
            assertEquals(remoteTcpFallbackEnabled, config.isRemoteTcpFallbackEnabled());
            assertEquals(remoteTcpFallbackServerBindHost, config.getRemoteTcpFallbackServerBindHost());
            assertEquals(remoteTcpFallbackServerBindPort, config.getRemoteTcpFallbackServerBindPort());
            assertEquals(remoteTcpFallbackClientTimeout, config.getRemoteTcpFallbackClientTimeout());
            assertEquals(remoteTcpFallbackClientAddress, config.getRemoteTcpFallbackClientAddress());
            assertEquals(intraVmDiscoveryEnabled, config.isIntraVmDiscoveryEnabled());
            assertEquals(composedMessageTransferTimeout, config.getRemoteMessageComposedMessageTransferTimeout());
            assertEquals(monitoringEnabled, config.isMonitoringEnabled());
            assertEquals(monitoringHostTag, config.getMonitoringHostTag());
            assertEquals(monitoringInfluxUri, config.getMonitoringInfluxUri());
            assertEquals(monitoringInfluxUser, config.getMonitoringInfluxUser());
            assertEquals(monitoringInfluxPassword, config.getMonitoringInfluxPassword());
            assertEquals(monitoringInfluxDatabase, config.getMonitoringInfluxDatabase());
            assertEquals(monitoringInfluxReportingFrequency, config.getMonitoringInfluxReportingFrequency());
            assertEquals(serializationSerializers, config.getSerializationSerializers());
            assertEquals(serializationsBindingsInbound, config.getSerializationsBindingsInbound());
            assertEquals(serializationsBindingsOutbound, config.getSerializationsBindingsOutbound());
        }

        @Test
        void shouldThrowExceptionIfSuperPeerNetworkidMismatch() {
            final Config config = ConfigFactory.parseString("drasyl.network-id = 1\ndrasyl.remote.super-peer.endoint = \"http://localhost.de\"");

            assertThrows(DrasylConfigException.class, () -> new DrasylConfig(config));
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldMaskSecrets() {
            identityPrivateKey = CompressedPrivateKey.of("07e98a2f8162a4002825f810c0fbd69b0c42bd9cb4f74a21bc7807bc5acb4f5f");

            final DrasylConfig config = new DrasylConfig(
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
                    serverEndpoints,
                    remoteExposeEnabled,
                    superPeerEnabled,
                    superPeerEndpoints,
                    remoteStaticRoutes,
                    remoteMessageMaxContentLength,
                    remoteMessageHopLimit,
                    remoteMessageArmEnabled,
                    composedMessageTransferTimeout,
                    remoteMessageMtu,
                    remoteLocalHostDiscoveryEnabled,
                    Path.of(remoteLocalHostDiscoveryPathAsString),
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
                    plugins,
                    serializationSerializers,
                    serializationsBindingsInbound,
                    serializationsBindingsOutbound
            );

            assertThat(config.toString(), not(containsString(identityPrivateKey.toString())));
        }
    }

    @Nested
    class Immutable {
        @SuppressWarnings("java:S5778")
        @Test
        void constructorShouldCreateImmutableConfig() {
            final DrasylConfig config = new DrasylConfig();

            assertThrows(UnsupportedOperationException.class, () -> config.getSerializationSerializers().put("foo", null));
            assertThrows(UnsupportedOperationException.class, () -> config.getSerializationsBindingsInbound().put(String.class, "foo"));
            assertThrows(UnsupportedOperationException.class, () -> config.getSerializationsBindingsOutbound().put(String.class, "foo"));
            assertThrows(UnsupportedOperationException.class, () -> config.getPlugins().add(null));
            assertThrows(UnsupportedOperationException.class, () -> config.getRemoteEndpoints().add(null));
            assertThrows(UnsupportedOperationException.class, () -> config.getRemoteSuperPeerEndpoints().add(null));
        }

        @SuppressWarnings("java:S5778")
        @Test
        void builderShouldCreateImmutableConfig() {
            final DrasylConfig config = DrasylConfig.newBuilder()
                    .serializationSerializers(new HashMap<>())
                    .serializationsBindingsInbound(new HashMap<>())
                    .serializationsBindingsOutbound(new HashMap<>())
                    .plugins(new HashSet<>())
                    .remoteEndpoints(new HashSet<>())
                    .build();

            assertThrows(UnsupportedOperationException.class, () -> config.getSerializationSerializers().put("foo", null));
            assertThrows(UnsupportedOperationException.class, () -> config.getSerializationsBindingsInbound().put(String.class, "foo"));
            assertThrows(UnsupportedOperationException.class, () -> config.getSerializationsBindingsOutbound().put(String.class, "foo"));
            assertThrows(UnsupportedOperationException.class, () -> config.getPlugins().add(null));
            assertThrows(UnsupportedOperationException.class, () -> config.getRemoteEndpoints().add(null));
            assertThrows(UnsupportedOperationException.class, () -> config.getRemoteSuperPeerEndpoints().add(null));
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() {
            final DrasylConfig config1 = DrasylConfig.newBuilder().build();
            final DrasylConfig config2 = DrasylConfig.newBuilder().build();

            assertEquals(config1, config2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() {
            final DrasylConfig config1 = DrasylConfig.newBuilder().build();
            final DrasylConfig config2 = DrasylConfig.newBuilder().build();

            assertEquals(config1.hashCode(), config2.hashCode());
        }
    }

    @Nested
    class GetUri {
        @Test
        void shouldReturnUriAtPath() {
            final Config config = ConfigFactory.parseString("foo.bar = \"http://localhost.de\"");

            assertEquals(URI.create("http://localhost.de"), getURI(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = \"hallo world\"");

            assertThrows(DrasylConfigException.class, () -> getURI(config, "foo.bar"));
        }
    }

    @Nested
    class GetPublicKey {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = bla");

            assertThrows(DrasylConfigException.class, () -> getPublicKey(config, "foo.bar"));
        }
    }

    @Nested
    class GetPrivateKey {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = bla");

            assertThrows(DrasylConfigException.class, () -> getPrivateKey(config, "foo.bar"));
        }
    }

    @Nested
    class GetPath {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("");

            assertThrows(DrasylConfigException.class, () -> getPath(config, "foo.bar"));
        }
    }

    @Nested
    class GetShort {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = 123456789");

            assertThrows(DrasylConfigException.class, () -> getByte(config, "foo.bar"));
        }
    }

    @Nested
    class GetEndpointList {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = [\"http://foo.bar\"]");

            assertThrows(DrasylConfigException.class, () -> getEndpointList(config, "foo.bar"));
        }
    }

    @Nested
    class GetEndpoint {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = \"http://foo.bar\"");

            assertThrows(DrasylConfigException.class, () -> getEndpoint(config, "foo.bar"));
        }
    }

    @Nested
    class GetInetAddress {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = baz");

            assertThrows(DrasylConfigException.class, () -> getInetAddress(config, "foo.bar"));
        }
    }

    @Nested
    class GetInetSocketAddress {
        @Test
        void shouldParseIPv4Address() {
            final Config config = ConfigFactory.parseString("foo.bar = \"203.0.113.149:22527\"");

            assertEquals(InetSocketAddress.createUnresolved("203.0.113.149", 22527), getInetSocketAddress(config, "foo.bar"));
        }

        @Test
        void shouldParseIPv6Address() {
            final Config config = ConfigFactory.parseString("foo.bar = \"[2001:db8::1]:8080\"");

            assertEquals(InetSocketAddress.createUnresolved("[2001:db8::1]", 8080), getInetSocketAddress(config, "foo.bar"));
        }

        @Test
        void shouldParseHostname() {
            final Config config = ConfigFactory.parseString("foo.bar = \"production.env.drasyl.org:1234\"");

            assertEquals(InetSocketAddress.createUnresolved("production.env.drasyl.org", 1234), getInetSocketAddress(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionAddressWithoutHostname() {
            final Config config = ConfigFactory.parseString("foo.bar = \"1234\"");

            assertThrows(DrasylConfigException.class, () -> getInetSocketAddress(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionAddressWithoutPort() {
            final Config config = ConfigFactory.parseString("foo.bar = \"production.env.drasyl.org\"");

            assertThrows(DrasylConfigException.class, () -> getInetSocketAddress(config, "foo.bar"));
        }
    }

    @Nested
    class GetPlugins {
        @Test
        void shouldThrowExceptionForNonExistingClasses() {
            final Config config = ConfigFactory.parseString("foo.bar { \"non.existing.class\" { enabled = true } }");

            assertThrows(DrasylConfigException.class, () -> getPlugins(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForClassWithMissingMethod() {
            final Config config = ConfigFactory.parseString("foo.bar { \"" + MyPluginWithMissingMethod.class.getName() + "\" { enabled = true } }");

            assertThrows(DrasylConfigException.class, () -> getPlugins(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForClassWithInvocationTargetException() {
            final Config config = ConfigFactory.parseString("foo.bar { \"" + MyPluginWithInvocationTargetException.class.getName() + "\" { enabled = true } }");

            assertThrows(DrasylConfigException.class, () -> getPlugins(config, "foo.bar"));
        }
    }

    static class MyPlugin implements DrasylPlugin {
        @SuppressWarnings("unused")
        public MyPlugin(final Config config) {
        }
    }

    static class MyPluginWithMissingMethod implements DrasylPlugin {
    }

    static class MyPluginWithInvocationTargetException implements DrasylPlugin {
        @SuppressWarnings("unused")
        public MyPluginWithInvocationTargetException(final Config config) throws IllegalAccessException {
            throw new IllegalAccessException("boom");
        }
    }

    @Nested
    class GetSerializationSerializers {
        @Test
        void shouldThrowExceptionForNonExistingClasses() {
            final Config config = ConfigFactory.parseString("foo.bar { string = \"org.drasyl.serialization.NotExistingSerializer\" }");

            assertThrows(DrasylConfigException.class, () -> getSerializationSerializers(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForClassWithMissingMethod() {
            final Config config = ConfigFactory.parseString("foo.bar { string = \"" + MySerializerWithMissingMethod.class.getName() + "\" }");

            assertThrows(DrasylConfigException.class, () -> getSerializationSerializers(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForClassWithInvocationTargetException() {
            final Config config = ConfigFactory.parseString("foo.bar { string = \"" + MySerializerWithInvocationTargetException.class.getName() + "\" }");

            assertThrows(DrasylConfigException.class, () -> getSerializationSerializers(config, "foo.bar"));
        }
    }

    static class MySerializer implements Serializer {
        public MySerializer() {
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            return o != null && getClass() == o.getClass();
        }

        @Override
        public byte[] toByteArray(final Object o) {
            return new byte[0];
        }

        @Override
        public <T> T fromByteArray(final byte[] bytes, final Class<T> type) {
            return null;
        }
    }

    static class MySerializerWithMissingMethod implements Serializer {
        @SuppressWarnings("unused")
        public MySerializerWithMissingMethod(final String foo) {
        }

        @Override
        public byte[] toByteArray(final Object o) {
            return new byte[0];
        }

        @Override
        public <T> T fromByteArray(final byte[] bytes, final Class<T> type) {
            return null;
        }
    }

    static class MySerializerWithInvocationTargetException implements Serializer {
        public MySerializerWithInvocationTargetException() throws IllegalAccessException {
            throw new IllegalAccessException("boom");
        }

        @Override
        public byte[] toByteArray(final Object o) {
            return new byte[0];
        }

        @Override
        public <T> T fromByteArray(final byte[] bytes, final Class<T> type) {
            return null;
        }
    }

    @Nested
    class GetSerializationBindings {
        @Test
        void shouldThrowExceptionForNonExistingClasses() {
            final Config config = ConfigFactory.parseString("foo.bar { \"testing.NotExisting\" = string }");

            final Set<String> serializers = Set.of("string");
            assertThrows(DrasylConfigException.class, () -> getSerializationBindings(config, "foo.bar", serializers));
        }

        @Test
        void shouldThrowExceptionForNonExistingSerializer() {
            final Config config = ConfigFactory.parseString("foo.bar { \"" + String.class.getName() + "\" = string }");

            final Set<String> serializers = Set.of();
            assertThrows(DrasylConfigException.class, () -> getSerializationBindings(config, "foo.bar", serializers));
        }
    }

    @Nested
    class GetStaticRoutes {
        @Test
        void shouldReturnCorrectRoutes() {
            final Config config = ConfigFactory.parseString("foo.bar { 033e8af97c541a5479e11b2860f9053e12df85f402cee33ebe0b55aa068a936a4b = \"140.211.24.157:22527\" }");

            assertEquals(
                    Map.of(CompressedPublicKey.of("033e8af97c541a5479e11b2860f9053e12df85f402cee33ebe0b55aa068a936a4b"), new InetSocketAddress("140.211.24.157", 22527)),
                    getStaticRoutes(config, "foo.bar")
            );
        }

        @Test
        void shouldThrowExceptionForInvalidPublicKey() {
            final Config config = ConfigFactory.parseString("foo.bar { 033e8af97c541aee33ebe0b55aa068a936a4b = \"140.211.24.157:22527\" }");

            assertThrows(DrasylConfigException.class, () -> getStaticRoutes(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForInvalidAddress() {
            final Config config = ConfigFactory.parseString("foo.bar { 033e8af97c541a5479e11b2860f9053e12df85f402cee33ebe0b55aa068a936a4b = \"140.211.24.157\" }");

            assertThrows(DrasylConfigException.class, () -> getStaticRoutes(config, "foo.bar"));
        }
    }

    @Nested
    class ParseFile {
        @Test
        void shouldReadConfigFromFile(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "drasyl.conf");
            Files.writeString(path, "drasyl.network.id = 1337\ndrasyl.remote.super-peer.endpoints = [\"udp://example.org:22527?publicKey=07e98a2f8162a4002825f810c0fbd69b0c42bd9cb4f74a21bc7807bc5acb4f5f&networkId=1337\"]", StandardOpenOption.CREATE);

            assertEquals(1337, DrasylConfig.parseFile(path.toFile()).getNetworkId());
        }
    }

    @Nested
    class ParseString {
        @Test
        void shouldReadConfigFromString() {
            assertEquals(1337, DrasylConfig.parseString("drasyl.network.id = 1337\ndrasyl.remote.super-peer.endpoints = [\"udp://example.org:22527?publicKey=07e98a2f8162a4002825f810c0fbd69b0c42bd9cb4f74a21bc7807bc5acb4f5f&networkId=1337\"]").getNetworkId());
        }
    }

    @Nested
    class Builder {
        @Test
        void shouldCreateCorrectConfig() {
            final DrasylConfig config = DrasylConfig.newBuilder()
                    .networkId(DEFAULT.getNetworkId())
                    .identityProofOfWork(DEFAULT.getIdentityProofOfWork())
                    .identityPublicKey(DEFAULT.getIdentityPublicKey())
                    .identityPrivateKey(DEFAULT.getIdentityPrivateKey())
                    .identityPath(DEFAULT.getIdentityPath())
                    .messageBufferSize(DEFAULT.getMessageBufferSize())
                    .remoteBindHost(DEFAULT.getRemoteBindHost())
                    .remoteEnabled(DEFAULT.isRemoteEnabled())
                    .remoteBindPort(DEFAULT.getRemoteBindPort())
                    .remotePingInterval(DEFAULT.getRemotePingInterval())
                    .remotePingTimeout(DEFAULT.getRemotePingTimeout())
                    .remotePingCommunicationTimeout(DEFAULT.getRemotePingCommunicationTimeout())
                    .remoteUniteMinInterval(DEFAULT.getRemoteUniteMinInterval())
                    .remotePingMaxPeers(DEFAULT.getRemotePingMaxPeers())
                    .remoteEndpoints(DEFAULT.getRemoteEndpoints())
                    .remoteExposeEnabled(DEFAULT.isRemoteExposeEnabled())
                    .remoteStaticRoutes(DEFAULT.getRemoteStaticRoutes())
                    .remoteMessageMtu(DEFAULT.getRemoteMessageMtu())
                    .remoteMessageMaxContentLength(DEFAULT.getRemoteMessageMaxContentLength())
                    .remoteMessageComposedMessageTransferTimeout(DEFAULT.getRemoteMessageComposedMessageTransferTimeout())
                    .remoteMessageHopLimit(DEFAULT.getRemoteMessageHopLimit())
                    .remoteMessageArmEnabled(DEFAULT.isRemoteMessageArmEnabled())
                    .remoteSuperPeerEnabled(DEFAULT.isRemoteSuperPeerEnabled())
                    .remoteSuperPeerEndpoints(DEFAULT.getRemoteSuperPeerEndpoints())
                    .intraVmDiscoveryEnabled(DEFAULT.isIntraVmDiscoveryEnabled())
                    .remoteLocalHostDiscoveryEnabled(DEFAULT.isRemoteLocalHostDiscoveryEnabled())
                    .remoteLocalHostDiscoveryPath(DEFAULT.getRemoteLocalHostDiscoveryPath())
                    .remoteLocalHostDiscoveryLeaseTime(DEFAULT.getRemoteLocalHostDiscoveryLeaseTime())
                    .remoteLocalNetworkDiscoveryEnabled(DEFAULT.isRemoteLocalNetworkDiscoveryEnabled())
                    .remoteTcpFallbackEnabled(DEFAULT.isRemoteTcpFallbackEnabled())
                    .remoteTcpFallbackServerBindHost(DEFAULT.getRemoteTcpFallbackServerBindHost())
                    .remoteTcpFallbackServerBindPort(DEFAULT.getRemoteTcpFallbackServerBindPort())
                    .remoteTcpFallbackClientTimeout(DEFAULT.getRemoteTcpFallbackClientTimeout())
                    .remoteTcpFallbackClientAddress(DEFAULT.getRemoteTcpFallbackClientAddress())
                    .monitoringEnabled(DEFAULT.isMonitoringEnabled())
                    .monitoringHostTag(DEFAULT.getMonitoringHostTag())
                    .monitoringInfluxUri(DEFAULT.getMonitoringInfluxUri())
                    .monitoringInfluxUser(DEFAULT.getMonitoringInfluxUser())
                    .monitoringInfluxPassword(DEFAULT.getMonitoringInfluxPassword())
                    .monitoringInfluxDatabase(DEFAULT.getMonitoringInfluxDatabase())
                    .monitoringInfluxReportingFrequency(DEFAULT.getMonitoringInfluxReportingFrequency())
                    .plugins(DEFAULT.getPlugins())
                    .serializationSerializers(DEFAULT.getSerializationSerializers())
                    .serializationsBindingsInbound(DEFAULT.getSerializationsBindingsInbound())
                    .serializationsBindingsOutbound(DEFAULT.getSerializationsBindingsOutbound())
                    .build();

            assertEquals(DEFAULT, config);
        }
    }
}
