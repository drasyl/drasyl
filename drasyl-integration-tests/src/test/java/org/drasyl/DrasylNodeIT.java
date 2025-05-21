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

import org.awaitility.Awaitility;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

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

@Disabled("Super peer operation is no longer supported in Java")
class DrasylNodeIT {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylNodeIT.class);
    public static final long TIMEOUT = 15000L;
    public static final int MESSAGE_MTU = 1024;

    @BeforeAll
    static void beforeAll() {
        Awaitility.setDefaultTimeout(ofSeconds(20)); // MessageSerializer's inheritance graph construction take some time
    }

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
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                client2 = new EmbeddedNode(config).awaitStarted();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED client2"));

                await("PeerDirectEvent #1").untilAsserted(() -> assertThat(superPeer.readEvent(), instanceOf(PeerDirectEvent.class)));
                await("PeerDirectEvent #2").untilAsserted(() -> assertThat(superPeer.readEvent(), instanceOf(PeerDirectEvent.class)));
                await("PeerDirectEvent #3").untilAsserted(() -> assertThat(client1.readEvent(), instanceOf(PeerDirectEvent.class)));
                await("PeerDirectEvent# #4").untilAsserted(() -> assertThat(client2.readEvent(), instanceOf(PeerDirectEvent.class)));
            }

            @AfterEach
            void tearDown() {
                if (superPeer != null) {
                    superPeer.close();
                }
                if (client1 != null) {
                    client1.close();
                }
                if (client2 != null) {
                    client2.close();
                }
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

                await("NodeOfflineEvent #1").untilAsserted(() -> assertThat(client1.readEvent(), instanceOf(NodeOfflineEvent.class)));
                await("NodeOfflineEvent #2").untilAsserted(() -> assertThat(client2.readEvent(), instanceOf(NodeOfflineEvent.class)));
            }

            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void shouldCreateDirectConnectionOnCommunication() {
                // should trigger direct connection establishment between both peers
                client1.send(client2.identity().getAddress(), "Ping");

                await("PeerDirectEvent #1").untilAsserted(() -> assertThat(client1.readEvent(), instanceOf(PeerDirectEvent.class)));
                await("PeerDirectEvent #2").untilAsserted(() -> assertThat(client2.readEvent(), instanceOf(PeerDirectEvent.class)));

                // should tear down direct connection on inactivity
                await("PeerDirectEvent #3").untilAsserted(() -> assertThat(client1.readEvent(), instanceOf(PeerRelayEvent.class)));
                await("PeerDirectEvent #4").untilAsserted(() -> assertThat(client2.readEvent(), instanceOf(PeerRelayEvent.class)));
            }
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
                await("socket::isBound").untilAsserted(socket::isBound);
                final DrasylConfig config = configBuilder
                        .remoteBindPort(socket.getLocalPort())
                        .build();
                final EmbeddedNode node = new EmbeddedNode(config);
                node.start().exceptionally(new Function<Throwable, Void>() {
                    @Override
                    public Void apply(Throwable t) {
                        t.printStackTrace();
                        return null;
                    }
                });

                await("NodeUpEvent").untilAsserted(() -> assertThat(node.readEvent(), instanceOf(NodeUpEvent.class)));
                await("NodeUnrecoverableErrorEvent").untilAsserted(() -> assertThat(node.readEvent(), instanceOf(NodeUnrecoverableErrorEvent.class)));
                assertNull(node.readEvent());
            }
        }
    }

    private void assertMessageDelivery(final EmbeddedNode sender,
                                       final EmbeddedNode recipient,
                                       final Object msg) {
        sender.send(recipient.identity().getAddress(), msg).toCompletableFuture().join();
        await("assertMessageDelivery").untilAsserted(() -> {
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
