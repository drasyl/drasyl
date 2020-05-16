package org.drasyl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;

class DrasylNodeConfigTest {
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

    @BeforeEach
    void setUp() {
        identityPublicKey = "";
        identityPrivateKey = "";
        identityPath = mock(Path.class);
        userAgent = "";
        serverBindHost = "0.0.0.0";
        serverEnabled = true;
        serverBindPort = 0;
        serverIdleRetries = 3;
        serverIdleTimeout = Duration.ofSeconds(60);
        flushBufferSize = 256;
        serverSSLEnabled = false;
        serverSSLProtocols = mock(List.class);
        serverHandshakeTimeout = Duration.ofSeconds(30);
        serverEndpoints = mock(Set.class);
        serverChannelInitializer = "org.drasyl.core.server.handler.NodeServerInitializer";
        maxContentLength = 1024;
        superPeerEnabled = true;
        superPeerEndpoints = mock(Set.class);
        superPeerPublicKey = "";
        superPeerRetryDelays = mock(List.class);
        superPeerChannelInitializer = "org.drasyl.core.client.handler.SuperPeerClientInitializer";
        superPeerIdleRetries = 3;
        superPeerIdleTimeout = Duration.ofSeconds(60);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void toStringShouldMaskSecrets() {
        identityPrivateKey = "07e98a2f8162a4002825f810c0fbd69b0c42bd9cb4f74a21bc7807bc5acb4f5f";

        DrasylNodeConfig config = new DrasylNodeConfig(identityPublicKey, identityPrivateKey, identityPath, userAgent, serverBindHost, serverEnabled, serverBindPort, serverIdleRetries, serverIdleTimeout, flushBufferSize, serverSSLEnabled, serverSSLProtocols, serverHandshakeTimeout, serverEndpoints, serverChannelInitializer, maxContentLength, superPeerEnabled, superPeerEndpoints, superPeerPublicKey, superPeerRetryDelays, superPeerChannelInitializer, superPeerIdleRetries, superPeerIdleTimeout);

        assertThat(config.toString(), not(containsString(identityPrivateKey)));
    }
}