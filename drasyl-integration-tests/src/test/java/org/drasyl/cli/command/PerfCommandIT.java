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
package org.drasyl.cli.command;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.EmbeddedNode;
import org.drasyl.peer.Endpoint;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import test.util.IdentityTestUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static org.awaitility.Awaitility.await;
import static org.drasyl.util.Ansi.ansi;
import static org.drasyl.util.network.NetworkUtil.createInetAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static test.util.DrasylConfigRenderer.renderConfig;

class PerfCommandIT {
    private static final Logger LOG = LoggerFactory.getLogger(WormholeCommandIT.class);
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("([0-9A-F]{64})", CASE_INSENSITIVE);
    private EmbeddedNode superPeer;
    private ByteArrayOutputStream serverOut;
    private ByteArrayOutputStream clientOut;
    private Thread serverThread = null;
    private Thread clientThread = null;

    @BeforeEach
    void setUp(final TestInfo info) throws DrasylException {
        LOG.debug(ansi().cyan().swap().format("# %-140s #", "STARTING " + info.getDisplayName()));

        // create super peer
        final DrasylConfig superPeerConfig = DrasylConfig.newBuilder()
                .networkId(0)
                .identityProofOfWork(IdentityTestUtil.ID_1.getProofOfWork())
                .identityPublicKey(IdentityTestUtil.ID_1.getIdentityPublicKey())
                .identitySecretKey(IdentityTestUtil.ID_1.getIdentitySecretKey())
                .remoteExposeEnabled(false)
                .remoteBindHost(createInetAddress("127.0.0.1"))
                .remoteBindPort(0)
                .remoteSuperPeerEnabled(false)
                .remoteLocalHostDiscoveryEnabled(false)
                .remoteLocalNetworkDiscoveryEnabled(false)
                .remoteExposeEnabled(false)
                .remoteTcpFallbackEnabled(false)
                .intraVmDiscoveryEnabled(false)
                .build();
        superPeer = new EmbeddedNode(superPeerConfig).awaitStarted();
        LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED superPeer"));

        serverOut = new ByteArrayOutputStream();
        clientOut = new ByteArrayOutputStream();
    }

    @AfterEach
    void tearDown(final TestInfo info) {
        if (superPeer != null) {
            superPeer.close();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
        if (clientThread != null) {
            clientThread.interrupt();
        }

        LOG.debug(ansi().cyan().swap().format("# %-140s #", "FINISHED " + info.getDisplayName()));
    }

    @Test
    @Timeout(value = 30_000, unit = MILLISECONDS)
    void shouldTransferText(@TempDir final Path path) throws IOException {
        // create server
        final DrasylConfig serverConfig = DrasylConfig.newBuilder()
                .networkId(0)
                .identityProofOfWork(IdentityTestUtil.ID_2.getProofOfWork())
                .identityPublicKey(IdentityTestUtil.ID_2.getIdentityPublicKey())
                .identitySecretKey(IdentityTestUtil.ID_2.getIdentitySecretKey())
                .remoteSuperPeerEndpoints(Set.of(Endpoint.of("udp://127.0.0.1:" + superPeer.getPort() + "?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey())))
                .remoteBindHost(createInetAddress("127.0.0.1"))
                .remoteBindPort(0)
                .remoteLocalHostDiscoveryEnabled(false)
                .remoteLocalNetworkDiscoveryEnabled(false)
                .remoteExposeEnabled(false)
                .remoteTcpFallbackEnabled(false)
                .intraVmDiscoveryEnabled(false)
                .build();
        final Path serverPath = path.resolve("server.conf");
        Files.writeString(serverPath, renderConfig(serverConfig), CREATE);
        serverThread = new Thread(() -> new PerfCommand(new PrintStream(serverOut, true)).execute(new String[]{
                "perf",
                "--config",
                serverPath.toString(),
                }));
        serverThread.start();

        // get server address
        final String serverAddress = await().atMost(ofSeconds(30)).until(() -> {
            final Matcher matcher = ADDRESS_PATTERN.matcher(serverOut.toString());
            if (matcher.find()) {
                return matcher.group(1);
            }
            else {
                return null;
            }
        }, notNullValue());

        // create client
        final DrasylConfig clientConfig = DrasylConfig.newBuilder()
                .networkId(0)
                .identityProofOfWork(IdentityTestUtil.ID_3.getProofOfWork())
                .identityPublicKey(IdentityTestUtil.ID_3.getIdentityPublicKey())
                .identitySecretKey(IdentityTestUtil.ID_3.getIdentitySecretKey())
                .remoteSuperPeerEndpoints(Set.of(Endpoint.of("udp://127.0.0.1:" + superPeer.getPort() + "?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey())))
                .remoteBindHost(createInetAddress("127.0.0.1"))
                .remoteBindPort(0)
                .remoteLocalHostDiscoveryEnabled(false)
                .remoteLocalNetworkDiscoveryEnabled(false)
                .remoteExposeEnabled(false)
                .remoteTcpFallbackEnabled(false)
                .intraVmDiscoveryEnabled(false)
                .build();
        final Path clientPath = path.resolve("client.conf");
        Files.writeString(clientPath, renderConfig(clientConfig), CREATE);
        clientThread = new Thread(() -> new PerfCommand(new PrintStream(clientOut, true)).execute(new String[]{
                "perf",
                "--client",
                serverAddress,
                "--config",
                clientPath.toString(),
                "--time",
                "2",
                "--direct"
        }));
        clientThread.start();

        // receive text
        await().atMost(ofSeconds(30)).untilAsserted(() -> {
            assertThat(clientOut.toString(), containsString("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -"));
            assertThat(clientOut.toString(), containsString("Sender:"));
            assertThat(clientOut.toString(), containsString("Receiver:"));
        });
    }
}
