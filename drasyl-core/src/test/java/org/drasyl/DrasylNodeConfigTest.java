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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.connection.server.NodeServerChannelInitializer;
import org.drasyl.peer.connection.superpeer.SuperPeerClientChannelInitializer;
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
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;
import static org.drasyl.DrasylNodeConfig.FLUSH_BUFFER_SIZE;
import static org.drasyl.DrasylNodeConfig.IDENTITY_PATH;
import static org.drasyl.DrasylNodeConfig.IDENTITY_PRIVATE_KEY;
import static org.drasyl.DrasylNodeConfig.IDENTITY_PROOF_OF_WORK;
import static org.drasyl.DrasylNodeConfig.IDENTITY_PUBLIC_KEY;
import static org.drasyl.DrasylNodeConfig.INTRA_VM_DISCOVERY_ENABLED;
import static org.drasyl.DrasylNodeConfig.MESSAGE_HOP_LIMIT;
import static org.drasyl.DrasylNodeConfig.MESSAGE_MAX_CONTENT_LENGTH;
import static org.drasyl.DrasylNodeConfig.SERVER_BIND_HOST;
import static org.drasyl.DrasylNodeConfig.SERVER_BIND_PORT;
import static org.drasyl.DrasylNodeConfig.SERVER_CHANNEL_INITIALIZER;
import static org.drasyl.DrasylNodeConfig.SERVER_ENABLED;
import static org.drasyl.DrasylNodeConfig.SERVER_ENDPOINTS;
import static org.drasyl.DrasylNodeConfig.SERVER_HANDSHAKE_TIMEOUT;
import static org.drasyl.DrasylNodeConfig.SERVER_IDLE_RETRIES;
import static org.drasyl.DrasylNodeConfig.SERVER_IDLE_TIMEOUT;
import static org.drasyl.DrasylNodeConfig.SERVER_SSL_ENABLED;
import static org.drasyl.DrasylNodeConfig.SERVER_SSL_PROTOCOLS;
import static org.drasyl.DrasylNodeConfig.SUPER_PEER_CHANNEL_INITIALIZER;
import static org.drasyl.DrasylNodeConfig.SUPER_PEER_ENABLED;
import static org.drasyl.DrasylNodeConfig.SUPER_PEER_ENDPOINTS;
import static org.drasyl.DrasylNodeConfig.SUPER_PEER_HANDSHAKE_TIMEOUT;
import static org.drasyl.DrasylNodeConfig.SUPER_PEER_PUBLIC_KEY;
import static org.drasyl.DrasylNodeConfig.SUPER_PEER_RETRY_DELAYS;
import static org.drasyl.DrasylNodeConfig.USER_AGENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class DrasylNodeConfigTest {
    private Level loglevel;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock
    private CompressedPublicKey identityPublicKey;
    @Mock
    private CompressedPrivateKey identityPrivateKey;
    @Mock
    private Path identityPath;
    private String userAgent;
    private String serverBindHost;
    private boolean serverEnabled;
    private int serverBindPort;
    private short serverIdleRetries;
    private Duration serverIdleTimeout;
    private int flushBufferSize;
    private boolean serverSSLEnabled;
    @Mock
    private List<String> serverSSLProtocols;
    private Duration serverHandshakeTimeout;
    private Set<URI> serverEndpoints;
    private Class<? extends ChannelInitializer<SocketChannel>> serverChannelInitializer;
    private int messageMaxContentLength;
    private short messageHopLimit;
    private boolean superPeerEnabled;
    private Set<URI> superPeerEndpoints;
    @Mock
    private CompressedPublicKey superPeerPublicKey;
    @Mock
    private List<Duration> superPeerRetryDelays;
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

    @BeforeEach
    void setUp() {
        loglevel = Level.WARN;
        userAgent = "";
        serverBindHost = "0.0.0.0";
        serverEnabled = true;
        serverBindPort = 22527;
        serverIdleRetries = 3;
        serverIdleTimeout = ofSeconds(60);
        flushBufferSize = 256;
        serverSSLEnabled = false;
        serverHandshakeTimeout = ofSeconds(30);
        serverEndpoints = Set.of();
        serverChannelInitializer = NodeServerChannelInitializer.class;
        messageMaxContentLength = 1024;
        messageHopLimit = 64;
        superPeerEnabled = true;
        superPeerEndpoints = Set.of(URI.create("ws://foo.bar:123"), URI.create("wss://example.com"));
        superPeerChannelInitializer = SuperPeerClientChannelInitializer.class;
        superPeerIdleRetries = 3;
        superPeerHandshakeTimeout = ofSeconds(30);
        superPeerIdleTimeout = ofSeconds(60);
        identityPathAsString = "drasyl.identity.json";
        intraVmDiscoveryEnabled = true;
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
            when(typesafeConfig.getStringList(SERVER_SSL_PROTOCOLS)).thenReturn(serverSSLProtocols);
            when(typesafeConfig.getStringList(SERVER_ENDPOINTS)).thenReturn(List.of());
            when(typesafeConfig.getBoolean(SUPER_PEER_ENABLED)).thenReturn(superPeerEnabled);
            when(typesafeConfig.getStringList(SUPER_PEER_ENDPOINTS)).thenReturn(List.of("ws://foo.bar:123", "wss://example.com"));
            when(typesafeConfig.getString(SUPER_PEER_PUBLIC_KEY)).thenReturn("");
            when(typesafeConfig.getDurationList(SUPER_PEER_RETRY_DELAYS)).thenReturn(superPeerRetryDelays);
            when(typesafeConfig.getDuration(SUPER_PEER_HANDSHAKE_TIMEOUT)).thenReturn(superPeerHandshakeTimeout);
            when(typesafeConfig.getString(SUPER_PEER_CHANNEL_INITIALIZER)).thenReturn(superPeerChannelInitializer.getCanonicalName());
            when(typesafeConfig.getString(USER_AGENT)).thenReturn(userAgent);
            when(networkAddressesProvider.get()).thenReturn(Set.of("192.168.188.112"));
            when(typesafeConfig.getBoolean(INTRA_VM_DISCOVERY_ENABLED)).thenReturn(intraVmDiscoveryEnabled);

            DrasylNodeConfig config = new DrasylNodeConfig(typesafeConfig, networkAddressesProvider);

            assertEquals(serverBindHost, config.getServerBindHost());
            assertEquals(serverBindPort, config.getServerBindPort());
            assertEquals(userAgent, config.getUserAgent());
            assertNull(config.getProofOfWork());
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
            assertEquals(Set.of(URI.create("ws://192.168.188.112:22527")), config.getServerEndpoints());
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
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldMaskSecrets() throws CryptoException {
            identityPrivateKey = CompressedPrivateKey.of("07e98a2f8162a4002825f810c0fbd69b0c42bd9cb4f74a21bc7807bc5acb4f5f");

            DrasylNodeConfig config = new DrasylNodeConfig(loglevel, proofOfWork, identityPublicKey, identityPrivateKey, identityPath, userAgent, serverBindHost, serverEnabled, serverBindPort, serverIdleRetries, serverIdleTimeout, flushBufferSize, serverSSLEnabled, serverSSLProtocols, serverHandshakeTimeout, serverEndpoints, serverChannelInitializer, messageMaxContentLength, messageHopLimit, superPeerEnabled, superPeerEndpoints, superPeerPublicKey, superPeerRetryDelays, superPeerHandshakeTimeout, superPeerChannelInitializer, superPeerIdleRetries, superPeerIdleTimeout, intraVmDiscoveryEnabled);

            assertThat(config.toString(), not(containsString(identityPrivateKey.getCompressedKey())));
        }
    }
}
