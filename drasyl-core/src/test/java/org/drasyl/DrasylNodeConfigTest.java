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
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.drasyl.DrasylNodeConfig.IDENTITY_PUBLIC_KEY;
import static org.drasyl.DrasylNodeConfig.MAX_CONTENT_LENGTH;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DrasylNodeConfigTest {
    private Level loglevel;
    private CompressedPublicKey identityPublicKey;
    private CompressedPrivateKey identityPrivateKey;
    private Path identityPath;
    private String userAgent;
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
    private String serverChannelInitializer;
    private int maxContentLength;
    private boolean superPeerEnabled;
    private Set<URI> superPeerEndpoints;
    private CompressedPublicKey superPeerPublicKey;
    private List<Duration> superPeerRetryDelays;
    private String superPeerChannelInitializer;
    private short superPeerIdleRetries;
    private Duration superPeerIdleTimeout;
    private Config typesafeConfig;
    private String identityPathAsString;
    private Supplier<Set<String>> networkAddressesProvider;
    private Duration superPeerHandshakeTimeout;

    @BeforeEach
    void setUp() {
        loglevel = Level.WARN;
        identityPublicKey = mock(CompressedPublicKey.class);
        identityPrivateKey = mock(CompressedPrivateKey.class);
        identityPath = mock(Path.class);
        userAgent = "";
        serverBindHost = "0.0.0.0";
        serverEnabled = true;
        serverBindPort = 22527;
        serverIdleRetries = 3;
        serverIdleTimeout = ofSeconds(60);
        flushBufferSize = 256;
        serverSSLEnabled = false;
        serverSSLProtocols = mock(List.class);
        serverHandshakeTimeout = ofSeconds(30);
        serverEndpoints = Set.of();
        serverChannelInitializer = "org.drasyl.core.server.handler.NodeServerInitializer";
        maxContentLength = 1024;
        superPeerEnabled = true;
        superPeerEndpoints = Set.of(URI.create("ws://foo.bar:123"), URI.create("wss://example.com"));
        superPeerPublicKey = mock(CompressedPublicKey.class);
        superPeerRetryDelays = mock(List.class);
        superPeerChannelInitializer = "org.drasyl.core.client.handler.SuperPeerClientInitializer";
        superPeerIdleRetries = 3;
        superPeerHandshakeTimeout = ofSeconds(30);
        superPeerIdleTimeout = ofSeconds(60);
        typesafeConfig = mock(Config.class);
        identityPathAsString = "drasyl.identity.json";
        networkAddressesProvider = mock(Supplier.class);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void shouldReadConfigProperly() {
        when(typesafeConfig.getString(SERVER_BIND_HOST)).thenReturn(serverBindHost);
        when(typesafeConfig.getInt(SERVER_BIND_PORT)).thenReturn(serverBindPort);
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
        when(typesafeConfig.getString(SERVER_CHANNEL_INITIALIZER)).thenReturn(serverChannelInitializer);
        when(typesafeConfig.getMemorySize(MAX_CONTENT_LENGTH)).thenReturn(ConfigMemorySize.ofBytes(maxContentLength));
        when(typesafeConfig.getBoolean(SERVER_SSL_ENABLED)).thenReturn(serverSSLEnabled);
        when(typesafeConfig.getStringList(SERVER_SSL_PROTOCOLS)).thenReturn(serverSSLProtocols);
        when(typesafeConfig.getStringList(SERVER_ENDPOINTS)).thenReturn(List.of());
        when(typesafeConfig.getBoolean(SUPER_PEER_ENABLED)).thenReturn(superPeerEnabled);
        when(typesafeConfig.getStringList(SUPER_PEER_ENDPOINTS)).thenReturn(List.of("ws://foo.bar:123", "wss://example.com"));
        when(typesafeConfig.getString(SUPER_PEER_PUBLIC_KEY)).thenReturn("");
        when(typesafeConfig.getDurationList(SUPER_PEER_RETRY_DELAYS)).thenReturn(superPeerRetryDelays);
        when(typesafeConfig.getDuration(SUPER_PEER_HANDSHAKE_TIMEOUT)).thenReturn(superPeerHandshakeTimeout);
        when(typesafeConfig.getString(SUPER_PEER_CHANNEL_INITIALIZER)).thenReturn(superPeerChannelInitializer);
        when(typesafeConfig.getString(USER_AGENT)).thenReturn(userAgent);
        when(networkAddressesProvider.get()).thenReturn(Set.of("192.168.188.112"));

        DrasylNodeConfig config = new DrasylNodeConfig(this.typesafeConfig, networkAddressesProvider);

        assertEquals(serverBindHost, config.getServerBindHost());
        assertEquals(serverBindPort, config.getServerBindPort());
        assertEquals(userAgent, config.getUserAgent());
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
        assertEquals(maxContentLength, config.getMaxContentLength());
        assertEquals(superPeerEnabled, config.isSuperPeerEnabled());
        assertEquals(superPeerEndpoints, config.getSuperPeerEndpoints());
        assertNull(config.getSuperPeerPublicKey());
        assertEquals(superPeerRetryDelays, config.getSuperPeerRetryDelays());
        assertEquals(superPeerHandshakeTimeout, config.getSuperPeerHandshakeTimeout());
        assertEquals(superPeerChannelInitializer, config.getSuperPeerChannelInitializer());
    }

    @Test
    void toStringShouldMaskSecrets() throws CryptoException {
        identityPrivateKey = CompressedPrivateKey.of("07e98a2f8162a4002825f810c0fbd69b0c42bd9cb4f74a21bc7807bc5acb4f5f");

        DrasylNodeConfig config = new DrasylNodeConfig(loglevel, identityPublicKey, identityPrivateKey, identityPath, userAgent, serverBindHost, serverEnabled, serverBindPort, serverIdleRetries, serverIdleTimeout, flushBufferSize, serverSSLEnabled, serverSSLProtocols, serverHandshakeTimeout, serverEndpoints, serverChannelInitializer, maxContentLength, superPeerEnabled, superPeerEndpoints, superPeerPublicKey, superPeerRetryDelays, superPeerHandshakeTimeout, superPeerChannelInitializer, superPeerIdleRetries, superPeerIdleTimeout);

        assertThat(config.toString(), not(containsString(identityPrivateKey.getCompressedKey())));
    }
}
