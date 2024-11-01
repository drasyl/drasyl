/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.sdon;

import org.awaitility.Awaitility;
import org.drasyl.EmbeddedNode;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.identity.IdentityManager;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.time.Duration.ofSeconds;
import static org.drasyl.util.Ansi.ansi;
import static org.drasyl.util.network.NetworkUtil.createInetAddress;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;
import static test.util.IdentityTestUtil.ID_3;

class SdonCommandIT {
    private static final Logger LOG = LoggerFactory.getLogger(SdonCommandIT.class);
    private EmbeddedNode superPeer;
    private ByteArrayOutputStream controllerOut;
    private ByteArrayOutputStream deviceOut;
    private Thread controllerThread;
    private Thread deviceThread;

    @BeforeAll
    static void beforeAll() {
        Awaitility.setDefaultTimeout(ofSeconds(20)); // MessageSerializer's inheritance graph construction take some time
    }

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

        controllerOut = new ByteArrayOutputStream();
        deviceOut = new ByteArrayOutputStream();
    }

    @AfterEach
    void tearDown(final TestInfo info) {
        if (superPeer != null) {
            superPeer.close();
        }
        if (controllerThread != null) {
            controllerThread.interrupt();
        }
        if (deviceThread != null) {
            deviceThread.interrupt();
        }

        LOG.debug(ansi().cyan().swap().format("# %-140s #", "FINISHED " + info.getDisplayName()));
    }

    @Test
    //@Timeout(value = 30_000, unit = MILLISECONDS)
    void shouldTransferText(@TempDir final Path path) throws IOException, InterruptedException {
        // create controller
        final Path networkFile = path.resolve("network.conf");
        Files.writeString(networkFile, "net = create_network()\n" +
                "register_network(net)", CREATE);

        final Path controllerPath = path.resolve("controller.identity");
        IdentityManager.writeIdentityFile(controllerPath, ID_2);
        controllerThread = new Thread(() -> new SdonControllerCommand(
                new PrintStream(controllerOut, true),
                System.err,
                null,
                controllerPath.toFile(),
                new InetSocketAddress("127.0.0.1", 0),
                10_000,
                0,
                Map.of(superPeer.identity().getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", superPeer.getPort())),
                networkFile.toFile()).call());
        controllerThread.start();

        // create device
        final Path devicePath = path.resolve("device.identity");
        IdentityManager.writeIdentityFile(devicePath, ID_3);
        deviceThread = new Thread(() -> new SdonDeviceCommand(
                new PrintStream(deviceOut, true),
                System.err,
                null,
                devicePath.toFile(),
                new InetSocketAddress("127.0.0.1", 0),
                10_000,
                0,
                Map.of(superPeer.identity().getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", superPeer.getPort())),
                ID_2.getIdentityPublicKey(),
                new String[]{ "foo", "bar" }).call());
        deviceThread.start();

        while (true) {
            Thread.sleep(1000);
        }

//        controllerThread.join();
//        deviceThread.join();
    }
}
