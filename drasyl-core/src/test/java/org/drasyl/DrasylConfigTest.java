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
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigObject;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.connection.client.DefaultClientChannelInitializer;
import org.drasyl.peer.connection.server.DefaultServerChannelInitializer;
import org.drasyl.plugins.PluginEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

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
import static org.drasyl.DrasylConfig.MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT;
import static org.drasyl.DrasylConfig.MESSAGE_HOP_LIMIT;
import static org.drasyl.DrasylConfig.MESSAGE_MAX_CONTENT_LENGTH;
import static org.drasyl.DrasylConfig.MONITORING_ENABLED;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_DATABASE;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_PASSWORD;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_REPORTING_FREQUENCY;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_URI;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_USER;
import static org.drasyl.DrasylConfig.PLUGINS;
import static org.drasyl.DrasylConfig.SERVER_BIND_HOST;
import static org.drasyl.DrasylConfig.SERVER_BIND_PORT;
import static org.drasyl.DrasylConfig.SERVER_CHANNEL_INITIALIZER;
import static org.drasyl.DrasylConfig.SERVER_ENABLED;
import static org.drasyl.DrasylConfig.SERVER_ENDPOINTS;
import static org.drasyl.DrasylConfig.SERVER_HANDSHAKE_TIMEOUT;
import static org.drasyl.DrasylConfig.SERVER_IDLE_RETRIES;
import static org.drasyl.DrasylConfig.SERVER_IDLE_TIMEOUT;
import static org.drasyl.DrasylConfig.SERVER_SSL_ENABLED;
import static org.drasyl.DrasylConfig.SERVER_SSL_PROTOCOLS;
import static org.drasyl.DrasylConfig.SUPER_PEER_CHANNEL_INITIALIZER;
import static org.drasyl.DrasylConfig.SUPER_PEER_ENABLED;
import static org.drasyl.DrasylConfig.SUPER_PEER_ENDPOINTS;
import static org.drasyl.DrasylConfig.SUPER_PEER_HANDSHAKE_TIMEOUT;
import static org.drasyl.DrasylConfig.SUPER_PEER_PUBLIC_KEY;
import static org.drasyl.DrasylConfig.SUPER_PEER_RETRY_DELAYS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class DrasylConfigTest {
    private Level loglevel;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock
    private CompressedPublicKey identityPublicKey;
    @Mock
    private CompressedPrivateKey identityPrivateKey;
    @Mock
    private Path identityPath;
    private String serverBindHost;
    private boolean serverEnabled;
    private int serverBindPort;
    private short serverIdleRetries;
    private Duration serverIdleTimeout;
    private int flushBufferSize;
    private boolean serverSSLEnabled;
    private final Set<String> serverSSLProtocols = Set.of("TLSv1.3", "TLSv1.2");
    private Duration serverHandshakeTimeout;
    private Set<URI> serverEndpoints;
    private Class<? extends ChannelInitializer<SocketChannel>> serverChannelInitializer;
    private int messageMaxContentLength;
    private short messageHopLimit;
    private boolean superPeerEnabled;
    private Set<URI> superPeerEndpoints;
    @Mock
    private CompressedPublicKey superPeerPublicKey;
    private final List<Duration> superPeerRetryDelays = List.of(ofSeconds(0), ofSeconds(1), ofSeconds(2), ofSeconds(4), ofSeconds(8));
    private Class<? extends ChannelInitializer<SocketChannel>> superPeerChannelInitializer;
    private short superPeerIdleRetries;
    private Duration superPeerIdleTimeout;
    @Mock
    private Config typesafeConfig;
    private String identityPathAsString;
    @Mock
    private Supplier<Set<String>> networkAddressesProvider;
    private Duration superPeerHandshakeTimeout;
    private boolean intraVmDiscoveryEnabled;
    private boolean directConnectionsEnabled;
    private int directConnectionsMaxConcurrentConnections;
    private final List<Duration> directConnectionsRetryDelays = List.of(ofSeconds(0), ofSeconds(1), ofSeconds(2), ofSeconds(4), ofSeconds(8));
    private Duration directConnectionsHandshakeTimeout;
    private Class<? extends ChannelInitializer<SocketChannel>> directConnectionsChannelInitializer;
    private short directConnectionsIdleRetries;
    private Duration directConnectionsIdleTimeout;
    private Duration composedMessageTransferTimeout;
    private boolean monitoringEnabled;
    private String monitoringInfluxUri;
    private String monitoringInfluxUser;
    private String monitoringInfluxPassword;
    private String monitoringInfluxDatabase;
    private Duration monitoringInfluxReportingFrequency;
    private List<PluginEnvironment> pluginEnvironments;

    @BeforeEach
    void setUp() {
        loglevel = Level.WARN;
        serverBindHost = "0.0.0.0";
        serverEnabled = true;
        serverBindPort = 22527;
        serverIdleRetries = 3;
        serverIdleTimeout = ofSeconds(60);
        flushBufferSize = 256;
        serverSSLEnabled = false;
        serverHandshakeTimeout = ofSeconds(30);
        serverEndpoints = Set.of();
        serverChannelInitializer = DefaultServerChannelInitializer.class;
        messageMaxContentLength = 1024;
        messageHopLimit = 64;
        superPeerEnabled = true;
        superPeerEndpoints = Set.of(URI.create("ws://foo.bar:123"), URI.create("wss://example.com"));
        superPeerChannelInitializer = DefaultClientChannelInitializer.class;
        superPeerIdleRetries = 3;
        superPeerHandshakeTimeout = ofSeconds(30);
        superPeerIdleTimeout = ofSeconds(60);
        identityPathAsString = "drasyl.identity.json";
        intraVmDiscoveryEnabled = true;
        directConnectionsEnabled = true;
        directConnectionsMaxConcurrentConnections = 10;
        directConnectionsIdleRetries = 3;
        directConnectionsHandshakeTimeout = ofSeconds(30);
        directConnectionsIdleTimeout = ofSeconds(60);
        directConnectionsChannelInitializer = DefaultClientChannelInitializer.class;
        composedMessageTransferTimeout = ofSeconds(60);
        monitoringEnabled = true;
        monitoringInfluxUri = "http://localhost:8086";
        monitoringInfluxUser = "";
        monitoringInfluxPassword = "";
        monitoringInfluxDatabase = "drasyl";
        monitoringInfluxReportingFrequency = ofSeconds(60);
        pluginEnvironments = List.of();
    }

    @Nested
    class Constructor {
        @Test
        void shouldReadConfigProperly() {
            when(typesafeConfig.getString(SERVER_BIND_HOST)).thenReturn(serverBindHost);
            when(typesafeConfig.getInt(SERVER_BIND_PORT)).thenReturn(serverBindPort);
            when(typesafeConfig.getInt(IDENTITY_PROOF_OF_WORK)).thenReturn(-1);
            when(typesafeConfig.getString(IDENTITY_PUBLIC_KEY)).thenReturn("");
            when(typesafeConfig.getString(IDENTITY_PRIVATE_KEY)).thenReturn("");
            when(typesafeConfig.getString(IDENTITY_PATH)).thenReturn(identityPathAsString);
            when(typesafeConfig.getBoolean(SERVER_ENABLED)).thenReturn(serverEnabled);
            when(typesafeConfig.getString(SERVER_BIND_HOST)).thenReturn(serverBindHost);
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
            when(typesafeConfig.getBoolean(SUPER_PEER_ENABLED)).thenReturn(superPeerEnabled);
            when(typesafeConfig.getStringList(SUPER_PEER_ENDPOINTS)).thenReturn(List.of("ws://foo.bar:123", "wss://example.com"));
            when(typesafeConfig.getString(SUPER_PEER_PUBLIC_KEY)).thenReturn("");
            when(typesafeConfig.getDurationList(SUPER_PEER_RETRY_DELAYS)).thenReturn(superPeerRetryDelays);
            when(typesafeConfig.getDuration(SUPER_PEER_HANDSHAKE_TIMEOUT)).thenReturn(superPeerHandshakeTimeout);
            when(typesafeConfig.getString(SUPER_PEER_CHANNEL_INITIALIZER)).thenReturn(superPeerChannelInitializer.getCanonicalName());
            when(typesafeConfig.getBoolean(INTRA_VM_DISCOVERY_ENABLED)).thenReturn(intraVmDiscoveryEnabled);
            when(typesafeConfig.getBoolean(DIRECT_CONNECTIONS_ENABLED)).thenReturn(directConnectionsEnabled);
            when(typesafeConfig.getDurationList(DIRECT_CONNECTIONS_RETRY_DELAYS)).thenReturn(directConnectionsRetryDelays);
            when(typesafeConfig.getDuration(DIRECT_CONNECTIONS_HANDSHAKE_TIMEOUT)).thenReturn(directConnectionsHandshakeTimeout);
            when(typesafeConfig.getString(DIRECT_CONNECTIONS_CHANNEL_INITIALIZER)).thenReturn(directConnectionsChannelInitializer.getCanonicalName());
            when(typesafeConfig.getDuration(MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT)).thenReturn(composedMessageTransferTimeout);
            when(typesafeConfig.getBoolean(MONITORING_ENABLED)).thenReturn(monitoringEnabled);
            when(typesafeConfig.getString(MONITORING_INFLUX_URI)).thenReturn(monitoringInfluxUri);
            when(typesafeConfig.getString(MONITORING_INFLUX_USER)).thenReturn(monitoringInfluxUser);
            when(typesafeConfig.getString(MONITORING_INFLUX_PASSWORD)).thenReturn(monitoringInfluxPassword);
            when(typesafeConfig.getString(MONITORING_INFLUX_DATABASE)).thenReturn(monitoringInfluxDatabase);
            when(typesafeConfig.getDuration(MONITORING_INFLUX_REPORTING_FREQUENCY)).thenReturn(monitoringInfluxReportingFrequency);
            when(typesafeConfig.getObject(PLUGINS)).thenReturn(mock(ConfigObject.class));

            DrasylConfig config = new DrasylConfig(typesafeConfig);

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
            assertEquals(messageMaxContentLength, config.getMessageMaxContentLength());
            assertEquals(messageHopLimit, config.getMessageHopLimit());
            assertEquals(superPeerEnabled, config.isSuperPeerEnabled());
            assertEquals(superPeerEndpoints, config.getSuperPeerEndpoints());
            assertNull(config.getSuperPeerPublicKey());
            assertEquals(superPeerRetryDelays, config.getSuperPeerRetryDelays());
            assertEquals(superPeerHandshakeTimeout, config.getSuperPeerHandshakeTimeout());
            assertEquals(superPeerChannelInitializer, config.getSuperPeerChannelInitializer());
            assertEquals(intraVmDiscoveryEnabled, config.isIntraVmDiscoveryEnabled());
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
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldMaskSecrets() throws CryptoException {
            identityPrivateKey = CompressedPrivateKey.of("07e98a2f8162a4002825f810c0fbd69b0c42bd9cb4f74a21bc7807bc5acb4f5f");

            DrasylConfig config = new DrasylConfig(loglevel, proofOfWork, identityPublicKey, identityPrivateKey, identityPath,
                    serverBindHost, serverEnabled, serverBindPort, serverIdleRetries, serverIdleTimeout, flushBufferSize,
                    serverSSLEnabled, serverSSLProtocols, serverHandshakeTimeout, serverEndpoints, serverChannelInitializer,
                    messageMaxContentLength, messageHopLimit, composedMessageTransferTimeout, superPeerEnabled, superPeerEndpoints,
                    superPeerPublicKey, superPeerRetryDelays, superPeerHandshakeTimeout, superPeerChannelInitializer, superPeerIdleRetries,
                    superPeerIdleTimeout, intraVmDiscoveryEnabled, directConnectionsEnabled, directConnectionsMaxConcurrentConnections,
                    directConnectionsRetryDelays, directConnectionsHandshakeTimeout, directConnectionsChannelInitializer,
                    directConnectionsIdleRetries, directConnectionsIdleTimeout, monitoringEnabled, monitoringInfluxUri, monitoringInfluxUser,
                    monitoringInfluxPassword, monitoringInfluxDatabase, monitoringInfluxReportingFrequency, pluginEnvironments);

            assertThat(config.toString(), not(containsString(identityPrivateKey.getCompressedKey())));
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() {
            DrasylConfig config1 = DrasylConfig.newBuilder().build();
            DrasylConfig config2 = DrasylConfig.newBuilder().build();

            assertEquals(config1, config2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() {
            DrasylConfig config1 = DrasylConfig.newBuilder().build();
            DrasylConfig config2 = DrasylConfig.newBuilder().build();

            assertEquals(config1.hashCode(), config2.hashCode());
        }
    }

    @Nested
    class Builder {
        @Test
        void shouldCreateCorrectConfig() {
            DrasylConfig config = DrasylConfig.newBuilder()
                    .loglevel(DEFAULT.getLoglevel())
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
                    .messageMaxContentLength(DEFAULT.getMessageMaxContentLength())
                    .messageHopLimit(DEFAULT.getMessageHopLimit())
                    .superPeerEnabled(DEFAULT.isSuperPeerEnabled())
                    .superPeerEndpoints(DEFAULT.getSuperPeerEndpoints())
                    .superPeerPublicKey(DEFAULT.getSuperPeerPublicKey())
                    .superPeerRetryDelays(DEFAULT.getSuperPeerRetryDelays())
                    .superPeerHandshakeTimeout(DEFAULT.getSuperPeerHandshakeTimeout())
                    .superPeerChannelInitializer(DEFAULT.getSuperPeerChannelInitializer())
                    .superPeerIdleRetries(DEFAULT.getSuperPeerIdleRetries())
                    .superPeerIdleTimeout(DEFAULT.getSuperPeerIdleTimeout())
                    .intraVmDiscoveryEnabled(DEFAULT.isIntraVmDiscoveryEnabled())
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
                    .pluginEnvironments(DEFAULT.getPluginEnvironments())
                    .build();

            assertEquals(DEFAULT, config);
        }
    }
}
