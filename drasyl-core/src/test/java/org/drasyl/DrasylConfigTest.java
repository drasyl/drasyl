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
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigObject;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.connection.client.DefaultClientChannelInitializer;
import org.drasyl.peer.connection.server.DefaultServerChannelInitializer;
import org.drasyl.plugins.DrasylPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.time.Duration.ofSeconds;
import static org.drasyl.DrasylConfig.DEFAULT;
import static org.drasyl.DrasylConfig.DIRECT_CONNECTIONS_CHANNEL_INITIALIZER;
import static org.drasyl.DrasylConfig.DIRECT_CONNECTIONS_ENABLED;
import static org.drasyl.DrasylConfig.DIRECT_CONNECTIONS_HANDSHAKE_TIMEOUT;
import static org.drasyl.DrasylConfig.DIRECT_CONNECTIONS_RETRY_DELAYS;
import static org.drasyl.DrasylConfig.FLUSH_BUFFER_SIZE;
import static org.drasyl.DrasylConfig.IDENTITY_PATH;
import static org.drasyl.DrasylConfig.IDENTITY_PRIVATE_KEY;
import static org.drasyl.DrasylConfig.IDENTITY_PROOF_OF_WORK;
import static org.drasyl.DrasylConfig.IDENTITY_PUBLIC_KEY;
import static org.drasyl.DrasylConfig.INTRA_VM_DISCOVERY_ENABLED;
import static org.drasyl.DrasylConfig.LOCAL_HOST_DISCOVERY_ENABLED;
import static org.drasyl.DrasylConfig.LOCAL_HOST_DISCOVERY_LEASE_TIME;
import static org.drasyl.DrasylConfig.LOCAL_HOST_DISCOVERY_PATH;
import static org.drasyl.DrasylConfig.MARSHALLING_INBOUND_ALLOWED_PACKAGES;
import static org.drasyl.DrasylConfig.MARSHALLING_INBOUND_ALLOWED_TYPES;
import static org.drasyl.DrasylConfig.MARSHALLING_INBOUND_ALLOW_ALL_PRIMITIVES;
import static org.drasyl.DrasylConfig.MARSHALLING_INBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES;
import static org.drasyl.DrasylConfig.MARSHALLING_OUTBOUND_ALLOWED_PACKAGES;
import static org.drasyl.DrasylConfig.MARSHALLING_OUTBOUND_ALLOWED_TYPES;
import static org.drasyl.DrasylConfig.MARSHALLING_OUTBOUND_ALLOW_ALL_PRIMITIVES;
import static org.drasyl.DrasylConfig.MARSHALLING_OUTBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES;
import static org.drasyl.DrasylConfig.MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT;
import static org.drasyl.DrasylConfig.MESSAGE_HOP_LIMIT;
import static org.drasyl.DrasylConfig.MESSAGE_MAX_CONTENT_LENGTH;
import static org.drasyl.DrasylConfig.MONITORING_ENABLED;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_DATABASE;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_PASSWORD;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_REPORTING_FREQUENCY;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_URI;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_USER;
import static org.drasyl.DrasylConfig.NETWORK_ID;
import static org.drasyl.DrasylConfig.PLUGINS;
import static org.drasyl.DrasylConfig.SERVER_BIND_HOST;
import static org.drasyl.DrasylConfig.SERVER_BIND_PORT;
import static org.drasyl.DrasylConfig.SERVER_CHANNEL_INITIALIZER;
import static org.drasyl.DrasylConfig.SERVER_ENABLED;
import static org.drasyl.DrasylConfig.SERVER_ENDPOINTS;
import static org.drasyl.DrasylConfig.SERVER_EXPOSE_ENABLED;
import static org.drasyl.DrasylConfig.SERVER_HANDSHAKE_TIMEOUT;
import static org.drasyl.DrasylConfig.SERVER_IDLE_RETRIES;
import static org.drasyl.DrasylConfig.SERVER_IDLE_TIMEOUT;
import static org.drasyl.DrasylConfig.SERVER_SSL_ENABLED;
import static org.drasyl.DrasylConfig.SERVER_SSL_PROTOCOLS;
import static org.drasyl.DrasylConfig.SUPER_PEER_CHANNEL_INITIALIZER;
import static org.drasyl.DrasylConfig.SUPER_PEER_ENABLED;
import static org.drasyl.DrasylConfig.SUPER_PEER_ENDPOINTS;
import static org.drasyl.DrasylConfig.SUPER_PEER_HANDSHAKE_TIMEOUT;
import static org.drasyl.DrasylConfig.SUPER_PEER_RETRY_DELAYS;
import static org.drasyl.DrasylConfig.getURI;
import static org.drasyl.util.NetworkUtil.createInetAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class DrasylConfigTest {
    private final Set<String> serverSSLProtocols = Set.of("TLSv1.3", "TLSv1.2");
    private final List<Duration> superPeerRetryDelays = List.of(ofSeconds(0), ofSeconds(1), ofSeconds(2), ofSeconds(4), ofSeconds(8));
    private final List<Duration> directConnectionsRetryDelays = List.of(ofSeconds(0), ofSeconds(1), ofSeconds(2), ofSeconds(4), ofSeconds(8));
    private int networkId;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock
    private CompressedPublicKey identityPublicKey;
    @Mock
    private CompressedPrivateKey identityPrivateKey;
    @Mock
    private Path identityPath;
    private InetAddress serverBindHost;
    private boolean serverEnabled;
    private int serverBindPort;
    private short serverIdleRetries;
    private Duration serverIdleTimeout;
    private int flushBufferSize;
    private boolean serverSSLEnabled;
    private Duration serverHandshakeTimeout;
    private Set<Endpoint> serverEndpoints;
    private Class<? extends ChannelInitializer<SocketChannel>> serverChannelInitializer;
    private boolean serverExposeEnabled;
    private int messageMaxContentLength;
    private short messageHopLimit;
    private boolean superPeerEnabled;
    private Set<Endpoint> superPeerEndpoints;
    private Class<? extends ChannelInitializer<SocketChannel>> superPeerChannelInitializer;
    private short superPeerIdleRetries;
    private Duration superPeerIdleTimeout;
    @Mock
    private Config typesafeConfig;
    private String identityPathAsString;
    private Duration superPeerHandshakeTimeout;
    private boolean intraVmDiscoveryEnabled;
    private boolean localHostDiscoveryEnabled;
    private String localHostDiscoveryPathAsString;
    private Duration localHostDiscoveryLeaseTime;
    private boolean directConnectionsEnabled;
    private int directConnectionsMaxConcurrentConnections;
    private Duration directConnectionsHandshakeTimeout;
    private Class<? extends ChannelInitializer<SocketChannel>> directConnectionsChannelInitializer;
    private short directConnectionsIdleRetries;
    private Duration directConnectionsIdleTimeout;
    private Duration composedMessageTransferTimeout;
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

    @BeforeEach
    void setUp() {
        networkId = 1337;
        serverBindHost = createInetAddress("0.0.0.0");
        serverEnabled = true;
        serverBindPort = 22527;
        serverIdleRetries = 3;
        serverIdleTimeout = ofSeconds(60);
        flushBufferSize = 256;
        serverSSLEnabled = false;
        serverHandshakeTimeout = ofSeconds(30);
        serverEndpoints = Set.of();
        serverChannelInitializer = DefaultServerChannelInitializer.class;
        serverExposeEnabled = true;
        messageMaxContentLength = 1024;
        messageHopLimit = 64;
        superPeerEnabled = true;
        superPeerEndpoints = Set.of(Endpoint.of("ws://foo.bar:123#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"), Endpoint.of("wss://example.com#033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55"));
        superPeerChannelInitializer = DefaultClientChannelInitializer.class;
        superPeerIdleRetries = 3;
        superPeerHandshakeTimeout = ofSeconds(30);
        superPeerIdleTimeout = ofSeconds(60);
        identityPathAsString = "drasyl.identity.json";
        intraVmDiscoveryEnabled = true;
        localHostDiscoveryEnabled = true;
        localHostDiscoveryPathAsString = "foo/bar";
        localHostDiscoveryLeaseTime = ofSeconds(60);
        directConnectionsEnabled = true;
        directConnectionsMaxConcurrentConnections = 10;
        directConnectionsIdleRetries = 3;
        directConnectionsHandshakeTimeout = ofSeconds(30);
        directConnectionsIdleTimeout = ofSeconds(60);
        directConnectionsChannelInitializer = DefaultClientChannelInitializer.class;
        composedMessageTransferTimeout = ofSeconds(60);
        monitoringEnabled = true;
        monitoringInfluxUri = URI.create("http://localhost:8086");
        monitoringInfluxUser = "";
        monitoringInfluxPassword = "";
        monitoringInfluxDatabase = "drasyl";
        monitoringInfluxReportingFrequency = ofSeconds(60);
        plugins = Set.of();
        marshallingInboundAllowedTypes = List.of();
        marshallingInboundAllowAllPrimitives = true;
        marshallingInboundAllowArrayOfDefinedTypes = true;
        marshallingInboundAllowedPackages = List.of();
        marshallingOutboundAllowedTypes = List.of();
        marshallingOutboundAllowAllPrimitives = true;
        marshallingOutboundAllowArrayOfDefinedTypes = true;
        marshallingOutboundAllowedPackages = List.of();
    }

    @Nested
    class Constructor {
        @Test
        @SuppressWarnings("java:S5961")
        void shouldReadConfigProperly() {
            when(typesafeConfig.getInt(NETWORK_ID)).thenReturn(networkId);
            when(typesafeConfig.getInt(IDENTITY_PROOF_OF_WORK)).thenReturn(-1);
            when(typesafeConfig.getString(IDENTITY_PUBLIC_KEY)).thenReturn("");
            when(typesafeConfig.getString(IDENTITY_PRIVATE_KEY)).thenReturn("");
            when(typesafeConfig.getString(IDENTITY_PATH)).thenReturn(identityPathAsString);
            when(typesafeConfig.getBoolean(SERVER_ENABLED)).thenReturn(serverEnabled);
            when(typesafeConfig.getString(SERVER_BIND_HOST)).thenReturn(serverBindHost.getHostAddress());
            when(typesafeConfig.getInt(SERVER_BIND_PORT)).thenReturn(serverBindPort);
            when(typesafeConfig.getInt(SERVER_IDLE_RETRIES)).thenReturn(Short.valueOf(serverIdleRetries).intValue());
            when(typesafeConfig.getDuration(SERVER_IDLE_TIMEOUT)).thenReturn(serverIdleTimeout);
            when(typesafeConfig.getInt(FLUSH_BUFFER_SIZE)).thenReturn(flushBufferSize);
            when(typesafeConfig.getDuration(SERVER_HANDSHAKE_TIMEOUT)).thenReturn(serverHandshakeTimeout);
            when(typesafeConfig.getString(SERVER_CHANNEL_INITIALIZER)).thenReturn(serverChannelInitializer.getCanonicalName());
            when(typesafeConfig.getMemorySize(MESSAGE_MAX_CONTENT_LENGTH)).thenReturn(ConfigMemorySize.ofBytes(messageMaxContentLength));
            when(typesafeConfig.getInt(MESSAGE_HOP_LIMIT)).thenReturn((int) messageHopLimit);
            when(typesafeConfig.getBoolean(SERVER_SSL_ENABLED)).thenReturn(serverSSLEnabled);
            when(typesafeConfig.getStringList(SERVER_SSL_PROTOCOLS)).thenReturn(new ArrayList<>(serverSSLProtocols));
            when(typesafeConfig.getStringList(SERVER_ENDPOINTS)).thenReturn(List.of());
            when(typesafeConfig.getBoolean(SERVER_EXPOSE_ENABLED)).thenReturn(serverExposeEnabled);
            when(typesafeConfig.getBoolean(SUPER_PEER_ENABLED)).thenReturn(superPeerEnabled);
            when(typesafeConfig.getStringList(SUPER_PEER_ENDPOINTS)).thenReturn(List.of("ws://foo.bar:123#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22", "wss://example.com#033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55"));
            when(typesafeConfig.getDurationList(SUPER_PEER_RETRY_DELAYS)).thenReturn(superPeerRetryDelays);
            when(typesafeConfig.getDuration(SUPER_PEER_HANDSHAKE_TIMEOUT)).thenReturn(superPeerHandshakeTimeout);
            when(typesafeConfig.getString(SUPER_PEER_CHANNEL_INITIALIZER)).thenReturn(superPeerChannelInitializer.getCanonicalName());
            when(typesafeConfig.getBoolean(INTRA_VM_DISCOVERY_ENABLED)).thenReturn(intraVmDiscoveryEnabled);
            when(typesafeConfig.getBoolean(LOCAL_HOST_DISCOVERY_ENABLED)).thenReturn(localHostDiscoveryEnabled);
            when(typesafeConfig.getString(LOCAL_HOST_DISCOVERY_PATH)).thenReturn(localHostDiscoveryPathAsString);
            when(typesafeConfig.getDuration(LOCAL_HOST_DISCOVERY_LEASE_TIME)).thenReturn(localHostDiscoveryLeaseTime);
            when(typesafeConfig.getBoolean(DIRECT_CONNECTIONS_ENABLED)).thenReturn(directConnectionsEnabled);
            when(typesafeConfig.getDurationList(DIRECT_CONNECTIONS_RETRY_DELAYS)).thenReturn(directConnectionsRetryDelays);
            when(typesafeConfig.getDuration(DIRECT_CONNECTIONS_HANDSHAKE_TIMEOUT)).thenReturn(directConnectionsHandshakeTimeout);
            when(typesafeConfig.getString(DIRECT_CONNECTIONS_CHANNEL_INITIALIZER)).thenReturn(directConnectionsChannelInitializer.getCanonicalName());
            when(typesafeConfig.getDuration(MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT)).thenReturn(composedMessageTransferTimeout);
            when(typesafeConfig.getBoolean(MONITORING_ENABLED)).thenReturn(monitoringEnabled);
            when(typesafeConfig.getString(MONITORING_INFLUX_URI)).thenReturn(monitoringInfluxUri.toString());
            when(typesafeConfig.getString(MONITORING_INFLUX_USER)).thenReturn(monitoringInfluxUser);
            when(typesafeConfig.getString(MONITORING_INFLUX_PASSWORD)).thenReturn(monitoringInfluxPassword);
            when(typesafeConfig.getString(MONITORING_INFLUX_DATABASE)).thenReturn(monitoringInfluxDatabase);
            when(typesafeConfig.getDuration(MONITORING_INFLUX_REPORTING_FREQUENCY)).thenReturn(monitoringInfluxReportingFrequency);
            when(typesafeConfig.getObject(PLUGINS)).thenReturn(mock(ConfigObject.class));
            when(typesafeConfig.getStringList(MARSHALLING_INBOUND_ALLOWED_TYPES)).thenReturn(marshallingInboundAllowedTypes);
            when(typesafeConfig.getBoolean(MARSHALLING_INBOUND_ALLOW_ALL_PRIMITIVES)).thenReturn(marshallingInboundAllowAllPrimitives);
            when(typesafeConfig.getBoolean(MARSHALLING_INBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES)).thenReturn(marshallingInboundAllowArrayOfDefinedTypes);
            when(typesafeConfig.getStringList(MARSHALLING_INBOUND_ALLOWED_PACKAGES)).thenReturn(marshallingInboundAllowedPackages);
            when(typesafeConfig.getStringList(MARSHALLING_OUTBOUND_ALLOWED_TYPES)).thenReturn(marshallingOutboundAllowedTypes);
            when(typesafeConfig.getBoolean(MARSHALLING_OUTBOUND_ALLOW_ALL_PRIMITIVES)).thenReturn(marshallingOutboundAllowAllPrimitives);
            when(typesafeConfig.getBoolean(MARSHALLING_OUTBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES)).thenReturn(marshallingOutboundAllowArrayOfDefinedTypes);
            when(typesafeConfig.getStringList(MARSHALLING_OUTBOUND_ALLOWED_PACKAGES)).thenReturn(marshallingOutboundAllowedPackages);

            final DrasylConfig config = new DrasylConfig(typesafeConfig);

            assertEquals(networkId, config.getNetworkId());
            assertEquals(serverBindHost, config.getServerBindHost());
            assertEquals(serverBindPort, config.getServerBindPort());
            assertNull(config.getIdentityProofOfWork());
            assertNull(config.getIdentityPublicKey());
            assertNull(config.getIdentityPrivateKey());
            assertEquals(Paths.get("drasyl.identity.json"), config.getIdentityPath());
            assertEquals(serverEnabled, config.isServerEnabled());
            assertEquals(serverSSLEnabled, config.getServerSSLEnabled());
            assertEquals(serverIdleRetries, config.getServerIdleRetries());
            assertEquals(serverIdleTimeout, config.getServerIdleTimeout());
            assertEquals(flushBufferSize, config.getFlushBufferSize());
            assertEquals(serverSSLProtocols, config.getServerSSLProtocols());
            assertEquals(serverHandshakeTimeout, config.getServerHandshakeTimeout());
            assertEquals(Set.of(), config.getServerEndpoints());
            assertEquals(serverChannelInitializer, config.getServerChannelInitializer());
            assertEquals(serverExposeEnabled, config.isServerExposeEnabled());
            assertEquals(messageMaxContentLength, config.getMessageMaxContentLength());
            assertEquals(messageHopLimit, config.getMessageHopLimit());
            assertEquals(superPeerEnabled, config.isSuperPeerEnabled());
            assertEquals(superPeerEndpoints, config.getSuperPeerEndpoints());
            assertEquals(superPeerRetryDelays, config.getSuperPeerRetryDelays());
            assertEquals(superPeerHandshakeTimeout, config.getSuperPeerHandshakeTimeout());
            assertEquals(superPeerChannelInitializer, config.getSuperPeerChannelInitializer());
            assertEquals(intraVmDiscoveryEnabled, config.isIntraVmDiscoveryEnabled());
            assertEquals(localHostDiscoveryEnabled, config.isLocalHostDiscoveryEnabled());
            assertEquals(Path.of(localHostDiscoveryPathAsString), config.getLocalHostDiscoveryPath());
            assertEquals(localHostDiscoveryLeaseTime, config.getLocalHostDiscoveryLeaseTime());
            assertEquals(directConnectionsEnabled, config.areDirectConnectionsEnabled());
            assertEquals(directConnectionsRetryDelays, config.getDirectConnectionsRetryDelays());
            assertEquals(directConnectionsHandshakeTimeout, config.getDirectConnectionsHandshakeTimeout());
            assertEquals(directConnectionsChannelInitializer, config.getDirectConnectionsChannelInitializer());
            assertEquals(composedMessageTransferTimeout, config.getMessageComposedMessageTransferTimeout());
            assertEquals(monitoringEnabled, config.isMonitoringEnabled());
            assertEquals(monitoringInfluxUri, config.getMonitoringInfluxUri());
            assertEquals(monitoringInfluxUser, config.getMonitoringInfluxUser());
            assertEquals(monitoringInfluxPassword, config.getMonitoringInfluxPassword());
            assertEquals(monitoringInfluxDatabase, config.getMonitoringInfluxDatabase());
            assertEquals(monitoringInfluxReportingFrequency, config.getMonitoringInfluxReportingFrequency());
            assertEquals(marshallingInboundAllowedTypes, config.getMarshallingInboundAllowedTypes());
            assertEquals(marshallingInboundAllowAllPrimitives, config.isMarshallingInboundAllowAllPrimitives());
            assertEquals(marshallingInboundAllowArrayOfDefinedTypes, config.isMarshallingInboundAllowArrayOfDefinedTypes());
            assertEquals(marshallingInboundAllowedPackages, config.getMarshallingInboundAllowedPackages());
            assertEquals(marshallingOutboundAllowedTypes, config.getMarshallingOutboundAllowedTypes());
            assertEquals(marshallingOutboundAllowAllPrimitives, config.isMarshallingOutboundAllowAllPrimitives());
            assertEquals(marshallingOutboundAllowArrayOfDefinedTypes, config.isMarshallingOutboundAllowArrayOfDefinedTypes());
            assertEquals(marshallingOutboundAllowedPackages, config.getMarshallingOutboundAllowedPackages());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldMaskSecrets() throws CryptoException {
            identityPrivateKey = CompressedPrivateKey.of("07e98a2f8162a4002825f810c0fbd69b0c42bd9cb4f74a21bc7807bc5acb4f5f");

            final DrasylConfig config = new DrasylConfig(networkId, proofOfWork, identityPublicKey, identityPrivateKey, identityPath,
                    serverBindHost, serverEnabled, serverBindPort, serverIdleRetries, serverIdleTimeout, flushBufferSize,
                    serverSSLEnabled, serverSSLProtocols, serverHandshakeTimeout, serverEndpoints, serverChannelInitializer,
                    serverExposeEnabled, messageMaxContentLength, messageHopLimit, composedMessageTransferTimeout, superPeerEnabled, superPeerEndpoints,
                    superPeerRetryDelays, superPeerHandshakeTimeout, superPeerChannelInitializer, superPeerIdleRetries,
                    superPeerIdleTimeout, intraVmDiscoveryEnabled, localHostDiscoveryEnabled, Path.of(localHostDiscoveryPathAsString),
                    localHostDiscoveryLeaseTime, directConnectionsEnabled, directConnectionsMaxConcurrentConnections,
                    directConnectionsRetryDelays, directConnectionsHandshakeTimeout, directConnectionsChannelInitializer,
                    directConnectionsIdleRetries, directConnectionsIdleTimeout, monitoringEnabled, monitoringInfluxUri, monitoringInfluxUser,
                    monitoringInfluxPassword, monitoringInfluxDatabase, monitoringInfluxReportingFrequency, plugins,
                    marshallingInboundAllowedTypes, marshallingInboundAllowAllPrimitives, marshallingInboundAllowArrayOfDefinedTypes, marshallingInboundAllowedPackages,
                    marshallingOutboundAllowedTypes, marshallingOutboundAllowAllPrimitives, marshallingOutboundAllowArrayOfDefinedTypes, marshallingOutboundAllowedPackages);

            assertThat(config.toString(), not(containsString(identityPrivateKey.getCompressedKey())));
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

            assertThrows(ConfigException.class, () -> getURI(config, "foo.bar"));
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
                    .serverBindHost(DEFAULT.getServerBindHost())
                    .serverEnabled(DEFAULT.isServerEnabled())
                    .serverBindPort(DEFAULT.getServerBindPort())
                    .serverIdleRetries(DEFAULT.getServerIdleRetries())
                    .serverIdleTimeout(DEFAULT.getServerIdleTimeout())
                    .flushBufferSize(DEFAULT.getFlushBufferSize())
                    .serverSSLEnabled(DEFAULT.getServerSSLEnabled())
                    .serverSSLProtocols(DEFAULT.getServerSSLProtocols())
                    .serverHandshakeTimeout(DEFAULT.getServerHandshakeTimeout())
                    .serverEndpoints(DEFAULT.getServerEndpoints())
                    .serverChannelInitializer(DEFAULT.getServerChannelInitializer())
                    .serverExposeEnabled(DEFAULT.isServerExposeEnabled())
                    .messageMaxContentLength(DEFAULT.getMessageMaxContentLength())
                    .messageHopLimit(DEFAULT.getMessageHopLimit())
                    .superPeerEnabled(DEFAULT.isSuperPeerEnabled())
                    .superPeerEndpoints(DEFAULT.getSuperPeerEndpoints())
                    .superPeerRetryDelays(DEFAULT.getSuperPeerRetryDelays())
                    .superPeerHandshakeTimeout(DEFAULT.getSuperPeerHandshakeTimeout())
                    .superPeerChannelInitializer(DEFAULT.getSuperPeerChannelInitializer())
                    .superPeerIdleRetries(DEFAULT.getSuperPeerIdleRetries())
                    .superPeerIdleTimeout(DEFAULT.getSuperPeerIdleTimeout())
                    .intraVmDiscoveryEnabled(DEFAULT.isIntraVmDiscoveryEnabled())
                    .localHostDiscoveryLeaseTime(DEFAULT.getLocalHostDiscoveryLeaseTime())
                    .directConnectionsEnabled(DEFAULT.areDirectConnectionsEnabled())
                    .directConnectionsRetryDelays(DEFAULT.getDirectConnectionsRetryDelays())
                    .directConnectionsHandshakeTimeout(DEFAULT.getDirectConnectionsHandshakeTimeout())
                    .directConnectionsChannelInitializer(DEFAULT.getDirectConnectionsChannelInitializer())
                    .directConnectionsIdleRetries(DEFAULT.getDirectConnectionsIdleRetries())
                    .directConnectionsIdleTimeout(DEFAULT.getDirectConnectionsIdleTimeout())
                    .messageComposedMessageTransferTimeout(DEFAULT.getMessageComposedMessageTransferTimeout())
                    .monitoringEnabled(DEFAULT.isMonitoringEnabled())
                    .monitoringInfluxUri(DEFAULT.getMonitoringInfluxUri())
                    .monitoringInfluxUser(DEFAULT.getMonitoringInfluxUser())
                    .monitoringInfluxPassword(DEFAULT.getMonitoringInfluxPassword())
                    .monitoringInfluxDatabase(DEFAULT.getMonitoringInfluxDatabase())
                    .monitoringInfluxReportingFrequency(DEFAULT.getMonitoringInfluxReportingFrequency())
                    .plugins(DEFAULT.getPlugins())
                    .marshallingInboundAllowedTypes(DEFAULT.getMarshallingInboundAllowedTypes())
                    .marshallingInboundAllowAllPrimitives(DEFAULT.isMarshallingInboundAllowAllPrimitives())
                    .marshallingInboundAllowArrayOfDefinedTypes(DEFAULT.isMarshallingInboundAllowArrayOfDefinedTypes())
                    .marshallingInboundAllowedPackages(DEFAULT.getMarshallingInboundAllowedPackages())
                    .marshallingOutboundAllowedTypes(DEFAULT.getMarshallingOutboundAllowedTypes())
                    .marshallingOutboundAllowAllPrimitives(DEFAULT.isMarshallingOutboundAllowAllPrimitives())
                    .marshallingOutboundAllowArrayOfDefinedTypes(DEFAULT.isMarshallingOutboundAllowArrayOfDefinedTypes())
                    .marshallingOutboundAllowedPackages(DEFAULT.getMarshallingOutboundAllowedPackages())
                    .build();

            assertEquals(DEFAULT, config);
        }
    }
}