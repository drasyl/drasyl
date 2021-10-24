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

import org.drasyl.EmbeddedNode;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.PeerEndpoint;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

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
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;
import static test.util.IdentityTestUtil.ID_3;

class WormholeCommandIT {
    private static final Logger LOG = LoggerFactory.getLogger(WormholeCommandIT.class);
    private static final Pattern CODE_PATTERN = Pattern.compile("([0-9A-F]{66,})", CASE_INSENSITIVE);
    private EmbeddedNode superPeer;
    private ByteArrayOutputStream senderOut;
    private ByteArrayOutputStream receiverOut;
    private Thread senderThread = null;
    private Thread receiverThread = null;

    @BeforeEach
    void setUp(final TestInfo info) throws DrasylException {
        LOG.debug(ansi().cyan().swap().format("# %-140s #", "STARTING " + info.getDisplayName()));

        // create super peer
        final DrasylConfig superPeerConfig = DrasylConfig.newBuilder()
                .networkId(0)
                .identity(ID_1)
                .remoteExposeEnabled(false)
                .remoteBindHost(createInetAddress("127.0.0.1"))
                .remoteBindPort(0)
                .remoteSuperPeerEnabled(false)
                .intraVmDiscoveryEnabled(false)
                .remoteLocalHostDiscoveryEnabled(false)
                .remoteLocalNetworkDiscoveryEnabled(false)
                .remoteExposeEnabled(false)
                .remoteTcpFallbackEnabled(false)
                .build();
        superPeer = new EmbeddedNode(superPeerConfig).awaitStarted();
        LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED superPeer"));

        senderOut = new ByteArrayOutputStream();
        receiverOut = new ByteArrayOutputStream();
    }

    @AfterEach
    void tearDown(final TestInfo info) {
        if (superPeer != null) {
            superPeer.close();
        }
        if (senderThread != null) {
            senderThread.interrupt();
        }
        if (receiverThread != null) {
            receiverThread.interrupt();
        }

        LOG.debug(ansi().cyan().swap().format("# %-140s #", "FINISHED " + info.getDisplayName()));
    }

    @Test
    @Timeout(value = 30_000, unit = MILLISECONDS)
    void shouldTransferText(@TempDir final Path path) throws IOException {
        // create sending node
        final DrasylConfig senderConfig = DrasylConfig.newBuilder()
                .networkId(0)
                .identity(ID_2)
                .remoteSuperPeerEndpoints(Set.of(PeerEndpoint.of("udp://127.0.0.1:" + superPeer.getPort() + "?publicKey=" + ID_1.getIdentityPublicKey())))
                .remoteBindHost(createInetAddress("127.0.0.1"))
                .remoteBindPort(0)
                .remoteLocalHostDiscoveryEnabled(false)
                .remoteLocalNetworkDiscoveryEnabled(false)
                .remoteExposeEnabled(false)
                .remoteTcpFallbackEnabled(false)
                .intraVmDiscoveryEnabled(false)
                .build();
        final Path senderPath = path.resolve("sender.conf");
        Files.writeString(senderPath, renderConfig(senderConfig), CREATE);
        senderThread = new Thread(() -> new WormholeCommand(new PrintStream(senderOut, true)).execute(new String[]{
                "wormhole",
                "send",
                "--config",
                senderPath.toString(),
                "--text",
                "\"Hello World\"",
                }));
        senderThread.start();

        // get wormhole code
        final String code = await().atMost(ofSeconds(30)).until(() -> {
            final Matcher matcher = CODE_PATTERN.matcher(senderOut.toString());
            if (matcher.find()) {
                return matcher.group(1);
            }
            else {
                return null;
            }
        }, notNullValue());

        // create receiving node
        final DrasylConfig receiverConfig = DrasylConfig.newBuilder()
                .networkId(0)
                .identity(ID_3)
                .remoteSuperPeerEndpoints(Set.of(PeerEndpoint.of("udp://127.0.0.1:" + superPeer.getPort() + "?publicKey=" + ID_1.getIdentityPublicKey())))
                .remoteBindHost(createInetAddress("127.0.0.1"))
                .remoteBindPort(0)
                .remoteLocalHostDiscoveryEnabled(false)
                .remoteLocalNetworkDiscoveryEnabled(false)
                .remoteExposeEnabled(false)
                .remoteTcpFallbackEnabled(false)
                .intraVmDiscoveryEnabled(false)
                .build();
        final Path receiverPath = path.resolve("receiver.conf");
        Files.writeString(receiverPath, renderConfig(receiverConfig), CREATE);
        receiverThread = new Thread(() -> new WormholeCommand(new PrintStream(receiverOut, true)).execute(new String[]{
                "wormhole",
                "receive",
                "--config",
                receiverPath.toString(),
                code
        }));
        receiverThread.start();

        // receive text
        await().atMost(ofSeconds(30)).untilAsserted(() -> assertThat(receiverOut.toString(), containsString("Hello World")));
    }
}
