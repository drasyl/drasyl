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
import org.junit.jupiter.api.Timeout;
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
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;
import static org.drasyl.util.Ansi.ansi;
import static org.drasyl.util.network.NetworkUtil.createInetAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;
import static test.util.IdentityTestUtil.ID_3;
import static test.util.IdentityTestUtil.ID_4;

class SdonCommandIT {
    private static final Logger LOG = LoggerFactory.getLogger(SdonCommandIT.class);
    private EmbeddedNode superPeer;
    private ByteArrayOutputStream controllerOut;
    private ByteArrayOutputStream device1Out;
    private ByteArrayOutputStream device2Out;
    private Thread controllerThread;
    private Thread deviceThread1;
    private Thread deviceThread2;

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
        device1Out = new ByteArrayOutputStream();
        device2Out = new ByteArrayOutputStream();
    }

    @AfterEach
    void tearDown(final TestInfo info) {
        if (superPeer != null) {
            superPeer.close();
        }
        if (controllerThread != null) {
            controllerThread.interrupt();
        }
        if (deviceThread1 != null) {
            deviceThread1.interrupt();
        }

        LOG.debug(ansi().cyan().swap().format("# %-140s #", "FINISHED " + info.getDisplayName()));
    }

    @Test
    @Timeout(value = 30_000, unit = MILLISECONDS)
    void shouldWork(@TempDir final Path path) throws IOException, InterruptedException {
        // create controller
        final Path networkFile = path.resolve("network.conf");
        Files.writeString(networkFile, "net = create_network()\n" +
                "net:add_node('n1')\n" +
                "net:add_node('n2')\n" +
                "net:add_link('n1', 'n2')\n" +
                "net:set_callback(function(net, dev)\n" +
                "  print('callback called:')\n" +
                "  print(inspect(net))\n" +
                "  print(inspect(dev))\n" +
                "end)\n" +
                "register_network(net)", CREATE);

        final Path controllerPath = path.resolve("controller.identity");
        IdentityManager.writeIdentityFile(controllerPath, ID_2);
        controllerThread = new Thread(() -> new SdonControllerCommand(
                new PrintStream(controllerOut, true),
                System.err,
                null,//Level.TRACE,
                controllerPath.toFile(),
                new InetSocketAddress("127.0.0.1", 0),
                10_000,
                0,
                Map.of(superPeer.identity().getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", superPeer.getPort())),
                networkFile.toFile()).call());
        controllerThread.start();

        // create device1
        final Path devicePath1 = path.resolve("device1.identity");
        IdentityManager.writeIdentityFile(devicePath1, ID_3);
        deviceThread1 = new Thread(() -> new SdonDeviceCommand(
                new PrintStream(device1Out, true),
                System.err,
                null,//Level.TRACE,
                devicePath1.toFile(),
                new InetSocketAddress("127.0.0.1", 0),
                10_000,
                0,
                Map.of(superPeer.identity().getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", superPeer.getPort())),
                ID_2.getIdentityPublicKey(),
                new String[]{ "foo", "bar" }).call());
        deviceThread1.start();

        // create device1
        final Path devicePath2 = path.resolve("device2.identity");
        IdentityManager.writeIdentityFile(devicePath2, ID_4);
        deviceThread2 = new Thread(() -> new SdonDeviceCommand(
                new PrintStream(device2Out, true),
                System.err,
                null,//Level.TRACE,
                devicePath2.toFile(),
                new InetSocketAddress("127.0.0.1", 0),
                10_000,
                0,
                Map.of(superPeer.identity().getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", superPeer.getPort())),
                ID_2.getIdentityPublicKey(),
                new String[]{ "bar", "baz" }).call());
        deviceThread2.start();

        // start
        await("controller start").atMost(ofSeconds(30)).untilAsserted(() -> assertThat(controllerOut.toString(), containsString("Controller listening on address " + ID_2.getAddress())));
        await("device 1 start").atMost(ofSeconds(30)).untilAsserted(() -> assertThat(device1Out.toString(), containsString("Device listening on address " + ID_3.getAddress())));
        await("device 2 start").atMost(ofSeconds(30)).untilAsserted(() -> assertThat(device2Out.toString(), containsString("Device listening on address " + ID_4.getAddress())));

        // device registration
        await("controller registration").atMost(ofSeconds(30)).untilAsserted(() -> assertThat(controllerOut.toString(), allOf(
                containsString("Device " + ID_3.getAddress() + " registered."),
                containsString("Device " + ID_4.getAddress() + " registered.")
        )));
        await("device 1 registration").atMost(ofSeconds(30)).untilAsserted(() -> assertThat(device1Out.toString(), containsString("registered")));
        await("device 2 registration").atMost(ofSeconds(30)).untilAsserted(() -> assertThat(device2Out.toString(), containsString("registered")));
        
        controllerThread.join();
        deviceThread1.join();
        deviceThread2.join();
    }
}
