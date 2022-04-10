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
package org.drasyl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.handler.discovery.IntraVmDiscovery;
import org.drasyl.handler.remote.LocalHostDiscovery;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.PeerEndpoint;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.event.NodeOfflineEvent;
import org.drasyl.node.event.NodeUnrecoverableErrorEvent;
import org.drasyl.node.event.NodeUpEvent;
import org.drasyl.node.event.PeerDirectEvent;
import org.drasyl.node.event.PeerEvent;
import org.drasyl.node.event.PeerRelayEvent;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static java.net.InetSocketAddress.createUnresolved;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;
import static org.drasyl.util.Ansi.ansi;
import static org.drasyl.util.RandomUtil.randomBytes;
import static org.drasyl.util.network.NetworkUtil.createInetAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;
import static test.util.IdentityTestUtil.ID_3;
import static test.util.IdentityTestUtil.ID_4;

class DrasylNodeIT {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylNodeIT.class);
    public static final long TIMEOUT = 15000L;
    public static final int MESSAGE_MTU = 1024;

    @BeforeEach
    void setup(final TestInfo info) {
        System.setProperty("io.netty.leakDetection.level", "PARANOID");

        LOG.debug(ansi().cyan().swap().format("# %-140s #", "STARTING " + info.getDisplayName()));
    }

    @AfterEach
    void cleanUp(final TestInfo info) {
        LOG.debug(ansi().cyan().swap().format("# %-140s #", "FINISHED " + info.getDisplayName()));
    }

    @Nested
    class TestRemote {
        /**
         * Network Layout:
         * <pre>
         *        +---+---+
         *        | Super |
         *        | Peer  |
         *        +-+--+--+
         *          |  |
         *     +----+  +-----+
         *     |             |
         * +---+----+   +----+---+
         * |Client 1|   |Client 2|
         * +--------+   +--------+
         * </pre>
         */
        @Nested
        class SuperPeerAndTwoClientWhenOnlyRemoteIsEnabled {
            private EmbeddedNode superPeer;
            private EmbeddedNode client1;
            private EmbeddedNode client2;

            @BeforeEach
            void setUp() throws DrasylException {
                //
                // create nodes
                //
                DrasylConfig config;

                // super peer
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_1)
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(0)
                        .remoteSuperPeerEnabled(false)
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                superPeer = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED superPeer"));

                // client1
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_2)
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(0)
                        .remotePingInterval(ofSeconds(1))
                        .remotePingTimeout(ofSeconds(2))
                        .remotePingCommunicationTimeout(ofSeconds(1))
                        .remoteSuperPeerEndpoints(Set.of(PeerEndpoint.of("udp://127.0.0.1:" + superPeer.getPort() + "?publicKey=" + ID_1.getIdentityPublicKey())))
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                client1 = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED client1"));

                // client2
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_3)
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(0)
                        .remotePingInterval(ofSeconds(1))
                        .remotePingTimeout(ofSeconds(2))
                        .remotePingCommunicationTimeout(ofSeconds(1))
                        .remoteSuperPeerEndpoints(Set.of(PeerEndpoint.of("udp://127.0.0.1:" + superPeer.getPort() + "?publicKey=" + ID_1.getIdentityPublicKey())))
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                client2 = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED client2"));

                await().untilAsserted(() -> assertThat(superPeer.readEvent(), instanceOf(PeerDirectEvent.class)));
                await().untilAsserted(() -> assertThat(superPeer.readEvent(), instanceOf(PeerDirectEvent.class)));
                await().untilAsserted(() -> assertThat(client1.readEvent(), instanceOf(PeerDirectEvent.class)));
                await().untilAsserted(() -> assertThat(client2.readEvent(), instanceOf(PeerDirectEvent.class)));
            }

            @AfterEach
            void tearDown() {
                superPeer.close();
                client1.close();
                client2.close();
            }

            /**
             * This test ensures that sent application messages are delivered to the recipient
             * (either directly or relayed via super peer or a child). All nodes send messages to
             * every other node (including themselves). At the end, a check is made to ensure that
             * all nodes have received all messages.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void applicationMessagesShouldBeDelivered() {
                assertMessageDelivery(superPeer, superPeer, "Hallo Welt");
                assertMessageDelivery(client1, client1, "Hallo Welt");
                assertMessageDelivery(client2, client2, "Hallo Welt");

                assertBidirectionalMessageDelivery(superPeer, client1, "Hallo Welt");
                assertBidirectionalMessageDelivery(superPeer, client2, "Hallo Welt");
                assertBidirectionalMessageDelivery(client1, client2, "Hallo Welt");
            }

            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void applicationMessagesExceedingMtuShouldBeDelivered() {
                final byte[] payload = randomBytes(MESSAGE_MTU);

                assertMessageDelivery(superPeer, superPeer, payload);
                assertMessageDelivery(client1, client1, payload);
                assertMessageDelivery(client2, client2, payload);

                assertBidirectionalMessageDelivery(superPeer, client1, payload);
                assertBidirectionalMessageDelivery(superPeer, client2, payload);
                assertBidirectionalMessageDelivery(client1, client2, payload);
            }

            /**
             * This test checks whether the correct {@link PeerEvent}s are sent out by the other
             * nodes when a node is shut down
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void shuttingDownNodeShouldCloseConnections() {
                superPeer.shutdown();

                await().untilAsserted(() -> assertThat(client1.readEvent(), instanceOf(NodeOfflineEvent.class)));
                await().untilAsserted(() -> assertThat(client2.readEvent(), instanceOf(NodeOfflineEvent.class)));
            }

            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void shouldCreateDirectConnectionOnCommunication() {
                // should trigger direct connection establishment between both peers
                client1.send(client2.identity().getAddress(), "Ping");

                await().untilAsserted(() -> assertThat(client1.readEvent(), instanceOf(PeerDirectEvent.class)));
                await().untilAsserted(() -> assertThat(client2.readEvent(), instanceOf(PeerDirectEvent.class)));

                // should tear down direct connection on inactivity
                await().untilAsserted(() -> assertThat(client1.readEvent(), instanceOf(PeerRelayEvent.class)));
                await().untilAsserted(() -> assertThat(client2.readEvent(), instanceOf(PeerRelayEvent.class)));
            }
        }

        /**
         * Network Layout:
         * <pre>
         * +--------+   +--------+
         * | Node 1 |   | Node 2 |
         * +--------+   +--------+
         * </pre>
         */
        @Nested
        class TwoNodesWithStaticRoutesAndWithoutSuperPeerWhenOnlyRemoteIsEnabled {
            private EmbeddedNode node1;
            private EmbeddedNode node2;

            @BeforeEach
            void setUp() throws DrasylException {
                //
                // create nodes
                //
                DrasylConfig config;

                // node1
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_1)
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(22528)
                        .remotePingInterval(ofSeconds(1))
                        .remotePingTimeout(ofSeconds(2))
                        .remoteSuperPeerEnabled(false)
                        .remoteStaticRoutes(Map.of(ID_2.getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", 22529)))
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node1 = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node1"));

                // node2
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_2)
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(22529)
                        .remotePingInterval(ofSeconds(1))
                        .remotePingTimeout(ofSeconds(2))
                        .remoteSuperPeerEnabled(false)
                        .remoteStaticRoutes(Map.of(ID_1.getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", 22528)))
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node2 = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node2"));

                await().untilAsserted(() -> assertThat(node1.readEvent(), instanceOf(PeerDirectEvent.class)));
                await().untilAsserted(() -> assertThat(node2.readEvent(), instanceOf(PeerDirectEvent.class)));
            }

            @AfterEach
            void tearDown() {
                node1.close();
                node2.close();
            }

            /**
             * This test ensures that sent application messages are delivered to the recipient.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void applicationMessagesShouldBeDelivered() {
                assertBidirectionalMessageDelivery(node1, node2, true);
                assertBidirectionalMessageDelivery(node1, node2, (byte) 23);
                assertBidirectionalMessageDelivery(node1, node2, 'C');
                assertBidirectionalMessageDelivery(node1, node2, 3.141F);
                assertBidirectionalMessageDelivery(node1, node2, 1337);
                assertBidirectionalMessageDelivery(node1, node2, 9001L);
                assertBidirectionalMessageDelivery(node1, node2, (short) 42);
                assertBidirectionalMessageDelivery(node1, node2, new byte[]{
                        (byte) 0,
                        (byte) 1
                });
                assertBidirectionalMessageDelivery(node1, node2, "String");
                assertBidirectionalMessageDelivery(node1, node2, null);
            }
        }

        /**
         * This will test {@link org.drasyl.handler.remote.internet.UnconfirmedAddressResolveHandler}.
         * <p>
         * Network Layout:
         * <pre>
         * +--------+   +--------+
         * | Node 1 |-->| Node 2 |
         * +--------+   +--------+
         * </pre>
         */
        @Nested
        class TwoNodesWithJustOneHavingAStaticRouteWhenOnlyRemoteIsEnabled {
            private EmbeddedNode node1;
            private EmbeddedNode node2;

            @BeforeEach
            void setUp() throws DrasylException {
                //
                // create nodes
                //
                DrasylConfig config;

                // node1
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_1)
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(22528)
                        .remotePingInterval(ofSeconds(1))
                        .remotePingTimeout(ofSeconds(2))
                        .remoteSuperPeerEnabled(false)
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node1 = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node1"));

                // node2
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_2)
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(22529)
                        .remotePingInterval(ofSeconds(1))
                        .remotePingTimeout(ofSeconds(2))
                        .remoteSuperPeerEnabled(false)
                        .remoteStaticRoutes(Map.of(ID_1.getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", 22528)))
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node2 = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node2"));

                await().untilAsserted(() -> assertThat(node2.readEvent(), instanceOf(PeerDirectEvent.class)));
            }

            @AfterEach
            void tearDown() {
                node1.close();
                node2.close();
            }

            /**
             * This test ensures that sent application messages are delivered to the recipient.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void test() throws InterruptedException {
                // 1
                // as node1 has no route to node2 this message should not be delivered
                // sadly the send future will still be completed successfully, because this error is
                // first discovered on the server channel. Because the netty interface does not
                // allow to pass that error to the child channel, this error is just written to the
                // log
                node1.send(node2.identity().getAddress(), "Hello").toCompletableFuture().join();
                // but we can check if node1 has received something!
                Thread.sleep(100L);
                assertNull(node2.readEvent());

                // 2
                // now let node2 write to node1. This cause node1 to discover an unconfirmed inet
                // address for node2. Allowing node2 to route to node2
                assertMessageDelivery(node2, node1, "You ok?");

                // 3
                assertMessageDelivery(node1, node2, "Yeah");
            }
        }

        /**
         * Network Layout:
         * <pre>
         * +-------------------------+
         * |       Same Network      |
         * | +--------+   +--------+ |
         * | | Node 1 |   | Node 2 | |
         * | +--------+   +--------+ |
         * +-------------------------+
         * </pre>
         */
        @Nested
        @Disabled("This test requires a multicast-capable environment")
        class TwoNodesWithinTheSameNetworkWithoutSuperPeerWhenOnlyRemoteIsEnabled {
            private EmbeddedNode node1;
            private EmbeddedNode node2;

            @BeforeEach
            void setUp() throws DrasylException {
                //
                // create nodes
                //
                DrasylConfig config;

                // node1
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_1)
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("0.0.0.0"))
                        .remoteBindPort(0)
                        .remotePingInterval(ofSeconds(1))
                        .remoteSuperPeerEnabled(false)
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node1 = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node1"));

                // node2
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_2)
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("0.0.0.0"))
                        .remoteBindPort(0)
                        .remotePingInterval(ofSeconds(1))
                        .remoteSuperPeerEnabled(false)
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .build();
                node2 = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node2"));

                await().untilAsserted(() -> assertThat(node1.readEvent(), instanceOf(PeerDirectEvent.class)));
                await().untilAsserted(() -> assertThat(node2.readEvent(), instanceOf(PeerDirectEvent.class)));
            }

            @AfterEach
            void tearDown() {
                node1.close();
                node2.close();
            }

            /**
             * This test ensures that sent application messages are delivered to the recipient.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void applicationMessagesShouldBeDelivered() {
                assertBidirectionalMessageDelivery(node1, node2, true);
                assertBidirectionalMessageDelivery(node1, node2, (byte) 23);
                assertBidirectionalMessageDelivery(node1, node2, 'C');
                assertBidirectionalMessageDelivery(node1, node2, 3.141F);
                assertBidirectionalMessageDelivery(node1, node2, 1337);
                assertBidirectionalMessageDelivery(node1, node2, 9001L);
                assertBidirectionalMessageDelivery(node1, node2, (short) 42);
                assertBidirectionalMessageDelivery(node1, node2, new byte[]{
                        (byte) 0,
                        (byte) 1
                });
                assertBidirectionalMessageDelivery(node1, node2, "String");
                assertBidirectionalMessageDelivery(node1, node2, null);
            }
        }

        /**
         * Network Layout:
         * <pre>
         * +-------+
         * | Super |
         * | Peer  |
         * +---+---+
         *     |
         *     | (UDP blocked)
         *     |
         * +---+----+
         * |Client 1|
         * +--------+
         * </pre>
         * <p>
         * We simulate blocked UDP traffic by adding a handler to the client's pipeline dropping all
         * udp messages.
         */
        @Nested
        class SuperPeerAndOneClientWhenOnlyRemoteIsEnabledAndAllUdpTrafficIsBlocked {
            private EmbeddedNode superPeer;
            private EmbeddedNode client;

            @BeforeEach
            void setUp() throws DrasylException {
                //
                // create nodes
                //
                DrasylConfig config;

                // super peer
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_1)
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(0)
                        .remotePingInterval(ofSeconds(1))
                        .remoteSuperPeerEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackServerBindHost(createInetAddress("127.0.0.1"))
                        .remoteTcpFallbackServerBindPort(0)
                        .intraVmDiscoveryEnabled(false)
                        .build();
                superPeer = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED superPeer"));

                // client
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_2)
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(0)
                        .remotePingInterval(ofSeconds(1))
                        .remoteSuperPeerEndpoints(Set.of(PeerEndpoint.of("udp://127.0.0.1:" + superPeer.getPort() + "?publicKey=" + ID_1.getIdentityPublicKey())))
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .intraVmDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(true)
                        .remoteTcpFallbackClientTimeout(ofSeconds(2))
                        .remoteTcpFallbackClientAddress(createUnresolved("127.0.0.1", superPeer.getTcpFallbackPort()))
                        .build();
                client = new EmbeddedNode(config).awaitStarted();
                client.pipeline().addAfter(client.pipeline().context(UdpServer.class).name(), "UDP_BLOCKER", new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(final ChannelHandlerContext ctx,
                                      final Object msg,
                                      final ChannelPromise promise) {
                        LOG.trace("UDP message blocked: {}", msg);
                        ReferenceCountUtil.release(msg);
                        promise.setSuccess();
                    }
                });
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED client"));
            }

            @AfterEach
            void tearDown() {
                superPeer.close();
                client.close();
            }

            @Test
            void correctPeerEventsShouldBeEmitted() {
                await().untilAsserted(() -> assertThat(superPeer.readEvent(), instanceOf(PeerDirectEvent.class)));
                await().untilAsserted(() -> assertThat(client.readEvent(), instanceOf(PeerDirectEvent.class)));
            }
        }
    }

    @Nested
    class TestIntraVmDiscovery {
        /**
         * Network Layout:
         * <pre>
         * +---+----+   +----+---+   +----+---+   +----+---+
         * | Node 1 |   | Node 2 |   | Node 3 |   | Node 4 |
         * +--------+   +--------+   +----+---+   +----+---+
         * </pre>
         */
        @Nested
        class FourNodesWithOnlyIntraVmDiscoverIsEnabled {
            private EmbeddedNode node1;
            private EmbeddedNode node2;
            private EmbeddedNode node3;
            private EmbeddedNode node4;

            @BeforeEach
            void setUp() throws DrasylException {
                //
                // create nodes
                //
                DrasylConfig config;

                // node1
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_1)
                        .remoteExposeEnabled(false)
                        .remoteEnabled(false)
                        .remoteSuperPeerEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node1 = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node1"));

                // node2
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_2)
                        .remoteExposeEnabled(false)
                        .remoteEnabled(false)
                        .remoteSuperPeerEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node2 = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node2"));

                // node3
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_3)
                        .remoteExposeEnabled(false)
                        .remoteEnabled(false)
                        .remoteSuperPeerEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node3 = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node3"));

                // node4
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_4)
                        .remoteExposeEnabled(false)
                        .remoteEnabled(false)
                        .remoteSuperPeerEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node4 = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node4"));
            }

            @AfterEach
            void tearDown() {
                node1.close();
                node2.close();
                node3.close();
                node4.close();
            }

            /**
             * This test checks whether the messages sent via {@link IntraVmDiscovery} are
             * delivered.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void applicationMessagesShouldBeDelivered() {
                for (int i = 0; i < 3; i++) {
                    assertThat(node1.readEvent(), instanceOf(PeerDirectEvent.class));
                    assertThat(node2.readEvent(), instanceOf(PeerDirectEvent.class));
                    assertThat(node3.readEvent(), instanceOf(PeerDirectEvent.class));
                    assertThat(node4.readEvent(), instanceOf(PeerDirectEvent.class));
                }

                assertBidirectionalMessageDelivery(node1, node2, "Hallo Welt");
                assertBidirectionalMessageDelivery(node1, node3, "Hallo Welt");
                assertBidirectionalMessageDelivery(node1, node4, "Hallo Welt");
                assertBidirectionalMessageDelivery(node2, node3, "Hallo Welt");
                assertBidirectionalMessageDelivery(node2, node4, "Hallo Welt");
                assertBidirectionalMessageDelivery(node3, node4, "Hallo Welt");
            }

            /**
             * This test checks whether the {@link IntraVmDiscovery} emits the correct {@link
             * PeerEvent}s.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void correctPeerEventsShouldBeEmitted() {
                for (int i = 0; i < 3; i++) {
                    assertThat(node1.readEvent(), instanceOf(PeerDirectEvent.class));
                    assertThat(node2.readEvent(), instanceOf(PeerDirectEvent.class));
                    assertThat(node3.readEvent(), instanceOf(PeerDirectEvent.class));
                    assertThat(node4.readEvent(), instanceOf(PeerDirectEvent.class));
                }

                assertNull(node1.readEvent());
                assertNull(node2.readEvent());
                assertNull(node3.readEvent());
                assertNull(node4.readEvent());
            }
        }
    }

    @Nested
    class TestLocalHostDiscovery {
        /**
         * Network Layout:
         * <pre>
         * +--------+   +--------+
         * | Node 1 |   | Node 2 |
         * +--------+   +--------+
         * </pre>
         */
        @Nested
        class FourNodesWithOnlyLocalHostDiscoveryEnabled {
            private EmbeddedNode node1;
            private EmbeddedNode node2;
            @SuppressWarnings("FieldCanBeLocal")
            private Path localHostDiscoveryPath;

            @BeforeEach
            void setUp() throws DrasylException, IOException {
                localHostDiscoveryPath = Files.createTempDirectory("test");

                //
                // create nodes
                //
                DrasylConfig config;

                // node1
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_1)
                        .remoteExposeEnabled(false)
                        .remoteEnabled(true)
                        .remoteBindPort(0)
                        .remoteSuperPeerEnabled(false)
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(true)
                        .remoteLocalHostDiscoveryLeaseTime(ofSeconds(5))
                        .remoteLocalHostDiscoveryWatchEnabled(false)
                        .remoteLocalHostDiscoveryPath(localHostDiscoveryPath)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node1 = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node1"));

                // node2
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_2)
                        .remoteExposeEnabled(false)
                        .remoteEnabled(true)
                        .remoteBindPort(0)
                        .remoteSuperPeerEnabled(false)
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(true)
                        .remoteLocalHostDiscoveryLeaseTime(ofSeconds(5))
                        .remoteLocalHostDiscoveryWatchEnabled(false)
                        .remoteLocalHostDiscoveryPath(localHostDiscoveryPath)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node2 = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node2"));
            }

            @AfterEach
            void tearDown() {
                node1.close();
                node2.close();
            }

            /**
             * This test checks whether the {@link LocalHostDiscovery} emits the correct {@link
             * PeerEvent}s and is able to route outgoing messages.
             */
            @Test
            @Timeout(value = TIMEOUT * 5, unit = MILLISECONDS)
            void applicationMessagesShouldBeDelivered() {
                await().untilAsserted(() -> assertThat(node1.readEvent(), instanceOf(PeerDirectEvent.class)));
                await().untilAsserted(() -> assertThat(node2.readEvent(), instanceOf(PeerDirectEvent.class)));

                assertBidirectionalMessageDelivery(node1, node2, "Hallo Welt");
            }
        }
    }

    /**
     * Network Layout:
     * <pre>
     * +---+----+
     * | Node 1 |
     * +--------+
     * </pre>
     */
    @Nested
    class OneNodeWithNoDiscoveryMethodsEnabled {
        private EmbeddedNode node;

        @BeforeEach
        void setUp() throws DrasylException {
            //
            // create nodes
            //
            final DrasylConfig config;

            // node
            config = DrasylConfig.newBuilder()
                    .networkId(0)
                    .identity(ID_1)
                    .remoteExposeEnabled(false)
                    .remoteEnabled(false)
                    .remoteSuperPeerEnabled(false)
                    .intraVmDiscoveryEnabled(false)
                    .remoteLocalHostDiscoveryEnabled(false)
                    .remoteTcpFallbackEnabled(false)
                    .build();
            node = new EmbeddedNode(config).awaitStarted();
            LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node1"));
        }

        @AfterEach
        void tearDown() {
            node.close();
        }

        /**
         * This test ensures that loopback message discovery work.
         */
        @Test
        @Timeout(value = TIMEOUT, unit = MILLISECONDS)
        void applicationMessagesShouldBeDelivered() {
            assertMessageDelivery(node, node, "Hallo Welt");
        }
    }

    @Nested
    class Send {
        /**
         * Network Layout:
         * <pre>
         * +---+----+
         * | Node 1 |
         * +--------+
         * </pre>
         * Non-started
         */
        @Nested
        class SingleNonStartedNode {
            private EmbeddedNode node;

            @BeforeEach
            void setUp() throws DrasylException {
                //
                // create nodes
                //
                final DrasylConfig config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identity(ID_1)
                        .remoteExposeEnabled(false)
                        .remoteEnabled(false)
                        .remoteSuperPeerEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node = new EmbeddedNode(config);
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node"));
            }

            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void sendToSelfShouldThrowException() {
                assertThrows(ExecutionException.class, () -> node.send(node.identity().getAddress(), "Hallo Welt").toCompletableFuture().get());
            }

            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void sendToAnOtherPeerShouldThrowException() {
                assertThrows(ExecutionException.class, () -> node.send(ID_2.getIdentityPublicKey(), "Hallo Welt").toCompletableFuture().get());
            }
        }
    }

    @Nested
    class EventLifecycle {
        private DrasylConfig.Builder configBuilder;

        @BeforeEach
        void setUp() {
            configBuilder = DrasylConfig.newBuilder()
                    .networkId(0)
                    .identity(ID_1)
                    .remoteExposeEnabled(false)
                    .remoteEnabled(true)
                    .remoteBindHost(createInetAddress("127.0.0.1"))
                    .remoteSuperPeerEnabled(false)
                    .remoteLocalHostDiscoveryEnabled(false)
                    .remoteLocalNetworkDiscoveryEnabled(false)
                    .remoteTcpFallbackEnabled(false);
        }

        @Test
        @Timeout(value = TIMEOUT, unit = MILLISECONDS)
        void shouldEmitErrorEventAndCompleteNotExceptionallyIfStartFailed() throws DrasylException, IOException {
            try (final DatagramSocket socket = new DatagramSocket(0)) {
                socket.setReuseAddress(false);
                await().untilAsserted(socket::isBound);
                final DrasylConfig config = configBuilder
                        .remoteBindPort(socket.getLocalPort())
                        .build();
                final EmbeddedNode node = new EmbeddedNode(config);
                node.start();

                await().untilAsserted(() -> assertThat(node.readEvent(), instanceOf(NodeUpEvent.class)));
                await().untilAsserted(() -> assertThat(node.readEvent(), instanceOf(NodeUnrecoverableErrorEvent.class)));
                assertNull(node.readEvent());
            }
        }
    }

    private void assertMessageDelivery(final EmbeddedNode sender,
                                       final EmbeddedNode recipient,
                                       final Object msg) {
        sender.send(recipient.identity().getAddress(), msg).toCompletableFuture().join();
        await().untilAsserted(() -> {
            final Event event = recipient.readEvent();
            assertNotNull(event, String.format("expected message from <%s> to <%s> with payload <%s>", sender, recipient, msg));
            assertThat(event, instanceOf(MessageEvent.class));
            final Object actual = ((MessageEvent) event).getPayload();
            assertTrue(Objects.deepEquals(msg, actual), String.format("expected: <%s> but was: <%s>", msg, actual));
        });
    }

    private void assertBidirectionalMessageDelivery(final EmbeddedNode alice,
                                                    final EmbeddedNode bob,
                                                    final Object msg) {
        assertMessageDelivery(alice, bob, msg);
        assertMessageDelivery(bob, alice, msg);
    }
}
