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
package org.drasyl.cli.wormholearq;

import ch.qos.logback.classic.Level;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import org.drasyl.EmbeddedNode;
import org.drasyl.cli.wormholearq.WormholeSendCommand.Payload;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.identity.IdentityManager;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static org.awaitility.Awaitility.await;
import static org.drasyl.util.Ansi.ansi;
import static org.drasyl.util.RandomUtil.randomString;
import static org.drasyl.util.network.NetworkUtil.createInetAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;
import static test.util.IdentityTestUtil.ID_3;

class WormholearqCommandIT {
    private static final Logger LOG = LoggerFactory.getLogger(WormholearqCommandIT.class);
    private static final Pattern CODE_PATTERN = Pattern.compile("([0-9A-F]{66,})", CASE_INSENSITIVE);
    private EmbeddedNode superPeer;
    private ByteArrayOutputStream senderOut;
    private ByteArrayOutputStream receiverOut;
    private Thread senderThread;
    private Thread receiverThread;

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
        // create server
        final Path senderPath = path.resolve("sender.identity");
        IdentityManager.writeIdentityFile(senderPath, ID_2);
        final EventLoopGroup senderGroup = new DefaultEventLoopGroup(1);
        final EventLoopGroup udpServerGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);
        senderThread = new Thread(() -> new WormholeSendCommand(
                new PrintStream(senderOut, true),
                System.err,
                senderGroup,
                senderGroup,
                udpServerGroup,
                Level.WARN,
                senderPath.toFile(),
                new InetSocketAddress("127.0.0.1", 0),
                10_000,
                0,
                Map.of(superPeer.identity().getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", superPeer.getPort())),
                "",
                new Payload("Hello World", null),
                10,
                10
        ).call());
        senderThread.start();

        // get wormhole code
        final Pair<IdentityPublicKey, String> code = await("get wormhole code").atMost(ofSeconds(30)).until(() -> {
            final Matcher matcher = CODE_PATTERN.matcher(senderOut.toString());
            if (matcher.find()) {
                return new org.drasyl.cli.wormhole.WormholeCodeConverter().convert(matcher.group(1));
            }
            else {
                return null;
            }
        }, notNullValue());

        // create receiving node
        final Path receiverPath = path.resolve("receiver.identity");
        IdentityManager.writeIdentityFile(receiverPath, ID_3);
        final EventLoopGroup receiverGroup = new DefaultEventLoopGroup(1);
        receiverThread = new Thread(() -> new WormholeReceiveCommand(
                new PrintStream(receiverOut, true),
                System.err,
                receiverGroup,
                receiverGroup,
                udpServerGroup,
                Level.WARN,
                receiverPath.toFile(),
                new InetSocketAddress("127.0.0.1", 0),
                10_000,
                0,
                Map.of(superPeer.identity().getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", superPeer.getPort())),
                code,
                10
        ).call());
        receiverThread.start();

        // receive text
        await().atMost(ofSeconds(30)).untilAsserted(() -> assertThat(receiverOut.toString(), containsString("Hello World")));
    }

    @Test
    @Timeout(value = 30_000, unit = MILLISECONDS)
    void shouldTransferFile(@TempDir final Path path) throws IOException {
        // create file
        final File file = path.resolve("WormholeCommandIT-" + randomString(5) + ".bin").toFile();

        final RandomAccessFile f = new RandomAccessFile(file, "rw");
        f.setLength(1024 * 10);
        f.close();

        // create server
        final Path senderPath = path.resolve("sender.identity");
        IdentityManager.writeIdentityFile(senderPath, ID_2);
        final EventLoopGroup senderParentGroup = new DefaultEventLoopGroup(1);
        final EventLoopGroup senderChildGroup = senderParentGroup;
        final EventLoopGroup udpServerGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);
        senderThread = new Thread(() -> new WormholeSendCommand(
                new PrintStream(senderOut, true),
                System.err,
                senderParentGroup,
                senderChildGroup,
                udpServerGroup,
                Level.WARN,
                senderPath.toFile(),
                new InetSocketAddress("127.0.0.1", 0),
                10_000,
                0,
                Map.of(superPeer.identity().getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", superPeer.getPort())),
                "",
                new Payload(null, file),
                150,
                150
        ).call());
        senderThread.start();

        // get wormhole code
        final Pair<IdentityPublicKey, String> code = await("get wormhole code").atMost(ofSeconds(30)).until(() -> {
            final Matcher matcher = CODE_PATTERN.matcher(senderOut.toString());
            if (matcher.find()) {
                return new WormholeCodeConverter().convert(matcher.group(1));
            }
            else {
                return null;
            }
        }, notNullValue());

        try {
            // create receiving node
            final Path receiverPath = path.resolve("receiver.identity");
            IdentityManager.writeIdentityFile(receiverPath, ID_3);
            final EventLoopGroup receiverParentGroup = new DefaultEventLoopGroup(1);
            final EventLoopGroup receiverChildGroup = receiverParentGroup;
            receiverThread = new Thread(() -> new WormholeReceiveCommand(
                    new PrintStream(receiverOut, true),
                    System.err,
                    receiverParentGroup,
                    receiverChildGroup,
                    udpServerGroup,
                    Level.WARN,
                    receiverPath.toFile(),
                    new InetSocketAddress("127.0.0.1", 0),
                    10_000,
                    0,
                    Map.of(superPeer.identity().getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", superPeer.getPort())),
                    code,
                    10
            ).call());
            receiverThread.start();

            // receive text
            await().atMost(ofSeconds(30)).untilAsserted(() -> assertThat(receiverOut.toString(), containsString("Received file written to")));
        }
        finally {
            Files.deleteIfExists(Path.of(file.getName()));
        }
    }
}
