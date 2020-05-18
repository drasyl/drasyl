package org.drasyl;

import ch.qos.logback.classic.Level;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigMemorySize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;
import static org.drasyl.DrasylNodeConfig.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DrasylNodeConfigTest {
    private Level loglevel;
    private String identityPublicKey;
    private String identityPrivateKey;
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
    private Set<String> serverEndpoints;
    private String serverChannelInitializer;
    private int maxContentLength;
    private boolean superPeerEnabled;
    private Set<String> superPeerEndpoints;
    private String superPeerPublicKey;
    private List<Duration> superPeerRetryDelays;
    private String superPeerChannelInitializer;
    private short superPeerIdleRetries;
    private Duration superPeerIdleTimeout;
    private Config typesafeConfig;
    private String identityPathAsString;
    private Supplier<Set<String>> networkAddressesProvider;

    @BeforeEach
    void setUp() {
        loglevel = Level.WARN;
        identityPublicKey = "";
        identityPrivateKey = "";
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
        superPeerEndpoints = Set.of("ws://foo.bar:123", "wss://example.com");
        superPeerPublicKey = "";
        superPeerRetryDelays = mock(List.class);
        superPeerChannelInitializer = "org.drasyl.core.client.handler.SuperPeerClientInitializer";
        superPeerIdleRetries = 3;
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
        when(typesafeConfig.getString(IDENTITY_PUBLIC_KEY)).thenReturn(identityPublicKey);
        when(typesafeConfig.getString(IDENTITY_PRIVATE_KEY)).thenReturn(identityPrivateKey);
        when(typesafeConfig.getString(IDENTITY_PATH)).thenReturn(identityPathAsString);
        when(typesafeConfig.getBoolean(SERVER_ENABLED)).thenReturn(serverEnabled);
        when(typesafeConfig.getString(SERVER_BIND_HOST)).thenReturn(serverBindHost);
        when(typesafeConfig.getInt(SERVER_BIND_PORT)).thenReturn(serverBindPort);
        when(typesafeConfig.getInt(SERVER_IDLE_RETRIES)).thenReturn(Short.valueOf(serverIdleRetries).intValue());
        when(typesafeConfig.getDuration(SERVER_IDLE_TIMEOUT)).thenReturn(serverIdleTimeout);
        when(typesafeConfig.getInt(FLUSH_BUFFER_SIZE)).thenReturn(flushBufferSize);
        when(typesafeConfig.getDuration(SERVER_MAX_HANDSHAKE_TIMEOUT)).thenReturn(serverHandshakeTimeout);
        when(typesafeConfig.getString(SERVER_CHANNEL_INITIALIZER)).thenReturn(serverChannelInitializer);
        when(typesafeConfig.getMemorySize(MAX_CONTENT_LENGTH)).thenReturn(ConfigMemorySize.ofBytes(maxContentLength));
        when(typesafeConfig.getBoolean(SERVER_SSL_ENABLED)).thenReturn(serverSSLEnabled);
        when(typesafeConfig.getStringList(SERVER_SSL_PROTOCOLS)).thenReturn(serverSSLProtocols);
        when(typesafeConfig.getStringList(SERVER_ENDPOINTS)).thenReturn(new ArrayList(serverEndpoints));
        when(typesafeConfig.getBoolean(SUPER_PEER_ENABLED)).thenReturn(superPeerEnabled);
        when(typesafeConfig.getStringList(SUPER_PEER_ENDPOINTS)).thenReturn(new ArrayList<>(superPeerEndpoints));
        when(typesafeConfig.getString(SUPER_PEER_PUBLIC_KEY)).thenReturn(superPeerPublicKey);
        when(typesafeConfig.getDurationList(SUPER_PEER_RETRY_DELAYS)).thenReturn(superPeerRetryDelays);
        when(typesafeConfig.getString(SUPER_PEER_CHANNEL_INITIALIZER)).thenReturn(superPeerChannelInitializer);
        when(typesafeConfig.getString(USER_AGENT)).thenReturn(userAgent);
        when(networkAddressesProvider.get()).thenReturn(Set.of("192.168.188.112"));

        DrasylNodeConfig config = new DrasylNodeConfig(this.typesafeConfig, networkAddressesProvider);

        assertEquals(serverBindHost, config.getServerBindHost());
        assertEquals(serverBindPort, config.getServerBindPort());
        assertEquals(userAgent, config.getUserAgent());
        assertEquals(identityPublicKey, config.getIdentityPublicKey());
        assertEquals(identityPrivateKey, config.getIdentityPrivateKey());
        assertEquals(Paths.get("drasyl.identity.json"), config.getIdentityPath());
        assertEquals(serverEnabled, config.isServerEnabled());
        assertEquals(serverSSLEnabled, config.getServerSSLEnabled());
        assertEquals(serverIdleRetries, config.getServerIdleRetries());
        assertEquals(serverIdleTimeout, config.getServerIdleTimeout());
        assertEquals(flushBufferSize, config.getFlushBufferSize());
        assertEquals(serverSSLProtocols, config.getServerSSLProtocols());
        assertEquals(serverHandshakeTimeout, config.getServerHandshakeTimeout());
        assertEquals(Set.of("ws://192.168.188.112:22527"), config.getServerEndpoints());
        assertEquals(serverChannelInitializer, config.getServerChannelInitializer());
        assertEquals(maxContentLength, config.getMaxContentLength());
        assertEquals(superPeerEnabled, config.isSuperPeerEnabled());
        assertEquals(superPeerEndpoints, config.getSuperPeerEndpoints());
        assertEquals(superPeerPublicKey, config.getSuperPeerPublicKey());
        assertEquals(superPeerRetryDelays, config.getSuperPeerRetryDelays());
        assertEquals(superPeerChannelInitializer, config.getSuperPeerChannelInitializer());
    }

    @Test
    void toStringShouldMaskSecrets() {
        identityPrivateKey = "07e98a2f8162a4002825f810c0fbd69b0c42bd9cb4f74a21bc7807bc5acb4f5f";

        DrasylNodeConfig config = new DrasylNodeConfig(loglevel, identityPublicKey, identityPrivateKey, identityPath, userAgent, serverBindHost, serverEnabled, serverBindPort, serverIdleRetries, serverIdleTimeout, flushBufferSize, serverSSLEnabled, serverSSLProtocols, serverHandshakeTimeout, serverEndpoints, serverChannelInitializer, maxContentLength, superPeerEnabled, superPeerEndpoints, superPeerPublicKey, superPeerRetryDelays, superPeerChannelInitializer, superPeerIdleRetries, superPeerIdleTimeout);

        assertThat(config.toString(), not(containsString(identityPrivateKey)));
    }
}
