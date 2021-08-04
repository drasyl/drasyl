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

import io.netty.buffer.ByteBuf;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerEvent;
import org.drasyl.peer.Endpoint;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.handler.filter.OutboundMessageFilter;
import org.drasyl.util.RandomUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import test.util.IdentityTestUtil;

import java.io.IOException;
import java.net.DatagramSocket;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.net.InetSocketAddress.createUnresolved;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;
import static org.drasyl.channel.DrasylServerChannelInitializer.UDP_SERVER;
import static org.drasyl.util.Ansi.ansi;
import static org.drasyl.util.network.NetworkUtil.createInetAddress;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                        .identityProofOfWork(IdentityTestUtil.ID_1.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_1.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_1.getIdentitySecretKey())
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
                superPeer = new EmbeddedNode(config).started();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED superPeer"));

                // client1
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(IdentityTestUtil.ID_2.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_2.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_2.getIdentitySecretKey())
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(0)
                        .remotePingInterval(ofSeconds(1))
                        .remotePingTimeout(ofSeconds(2))
                        .remoteSuperPeerEndpoints(Set.of(Endpoint.of("udp://127.0.0.1:" + superPeer.getPort() + "?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey())))
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                client1 = new EmbeddedNode(config).started().online();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED client1"));

                // client2
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(IdentityTestUtil.ID_3.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_3.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_3.getIdentitySecretKey())
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(0)
                        .remotePingInterval(ofSeconds(1))
                        .remotePingTimeout(ofSeconds(2))
                        .remoteSuperPeerEndpoints(Set.of(Endpoint.of("udp://127.0.0.1:" + superPeer.getPort() + "?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey())))
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                client2 = new EmbeddedNode(config).started().online();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED client2"));

                superPeer.events(PeerDirectEvent.class).test().awaitCount(2).assertValueCount(2);
                client1.events(PeerDirectEvent.class).test().awaitCount(1).assertValueCount(1);
                client2.events(PeerDirectEvent.class).test().awaitCount(1).assertValueCount(1);
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
                final TestObserver<MessageEvent> superPeerMessages = superPeer.messages().test();
                final TestObserver<MessageEvent> client1Messages = client1.messages().test();
                final TestObserver<MessageEvent> client2Messages = client2.messages().test();

                //
                // send messages
                //
                final Set<String> identities = Set.of(IdentityTestUtil.ID_1.getIdentityPublicKey().toString(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey().toString(),
                        IdentityTestUtil.ID_3.getIdentityPublicKey().toString());
                for (final String recipient : identities) {
                    superPeer.send(recipient, "Hallo Welt");
                    client1.send(recipient, "Hallo Welt");
                    client2.send(recipient, "Hallo Welt");
                }

                //
                // verify
                //
                superPeerMessages.awaitCount(3).assertValueCount(3)
                        .assertValueAt(0, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(1, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(2, m -> "Hallo Welt".equals(m.getPayload()));
                client1Messages.awaitCount(3).assertValueCount(3)
                        .assertValueAt(0, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(1, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(2, m -> "Hallo Welt".equals(m.getPayload()));
                client2Messages.awaitCount(3).assertValueCount(3)
                        .assertValueAt(0, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(1, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(2, m -> "Hallo Welt".equals(m.getPayload()));
            }

            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void applicationMessagesExceedingMtuShouldBeDelivered() {
                final TestObserver<MessageEvent> superPeerMessages = superPeer.messages().test();
                final TestObserver<MessageEvent> client1Messages = client1.messages().test();
                final TestObserver<MessageEvent> client2Messages = client2.messages().test();

                //
                // send messages
                //
                final byte[] payload = RandomUtil.randomBytes(MESSAGE_MTU);
                final Set<String> identities = Set.of(IdentityTestUtil.ID_1.getIdentityPublicKey().toString(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey().toString(),
                        IdentityTestUtil.ID_3.getIdentityPublicKey().toString());
                for (final String recipient : identities) {
                    superPeer.send(recipient, payload);
                    client1.send(recipient, payload);
                    client2.send(recipient, payload);
                }

                //
                // verify
                //
                superPeerMessages.awaitCount(3).assertValueCount(3)
                        .assertValueAt(0, m -> Objects.deepEquals(m.getPayload(), payload))
                        .assertValueAt(1, m -> Objects.deepEquals(m.getPayload(), payload))
                        .assertValueAt(2, m -> Objects.deepEquals(m.getPayload(), payload));
                client1Messages.awaitCount(3).assertValueCount(3)
                        .assertValueAt(0, m -> Objects.deepEquals(m.getPayload(), payload))
                        .assertValueAt(1, m -> Objects.deepEquals(m.getPayload(), payload))
                        .assertValueAt(2, m -> Objects.deepEquals(m.getPayload(), payload));
                client2Messages.awaitCount(3).assertValueCount(3)
                        .assertValueAt(0, m -> Objects.deepEquals(m.getPayload(), payload))
                        .assertValueAt(1, m -> Objects.deepEquals(m.getPayload(), payload))
                        .assertValueAt(2, m -> Objects.deepEquals(m.getPayload(), payload));
            }

            /**
             * This test checks whether the correct {@link PeerEvent}s are emitted in the correct
             * order.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void correctPeerEventsShouldBeEmitted() {
                //
                // send messages
                //
                final TestObserver<PeerDirectEvent> superPeerEvents = superPeer.events(PeerDirectEvent.class).test();
                final TestObserver<PeerDirectEvent> client1Events = client1.events(PeerDirectEvent.class).test();
                final TestObserver<PeerDirectEvent> client2Events = client2.events(PeerDirectEvent.class).test();

//            superPeer.second().subscribe(e -> System.err.println("SP: " + e));
//            client1.second().subscribe(e -> System.err.println("C1: " + e));
//            client2.second().subscribe(e -> System.err.println("C2: " + e));

                superPeerEvents.awaitCount(2).assertValueCount(2);
                client1Events.awaitCount(1).assertValueCount(1);
                client2Events.awaitCount(1).assertValueCount(1);
            }

            /**
             * This test checks whether the correct {@link PeerEvent}s are sent out by the other
             * nodes when a node is shut down
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void shuttingDownNodeShouldCloseConnections() {
                //
                // send messages
                //
                final TestObserver<NodeOfflineEvent> client1Events = client1.events(NodeOfflineEvent.class).test();
                final TestObserver<NodeOfflineEvent> client2Events = client2.events(NodeOfflineEvent.class).test();

                superPeer.shutdown().join();

                client1Events.awaitCount(1).assertValueCount(1);
                client2Events.awaitCount(1).assertValueCount(1);
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
                        .identityProofOfWork(IdentityTestUtil.ID_1.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_1.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_1.getIdentitySecretKey())
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(22528)
                        .remotePingInterval(ofSeconds(1))
                        .remotePingTimeout(ofSeconds(2))
                        .remoteSuperPeerEnabled(false)
                        .remoteStaticRoutes(Map.of(IdentityTestUtil.ID_2.getIdentityPublicKey(), new InetSocketAddressWrapper("127.0.0.1", 22529)))
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node1 = new EmbeddedNode(config).started();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node1"));

                // node2
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(IdentityTestUtil.ID_2.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_2.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_2.getIdentitySecretKey())
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(22529)
                        .remotePingInterval(ofSeconds(1))
                        .remotePingTimeout(ofSeconds(2))
                        .remoteSuperPeerEnabled(false)
                        .remoteStaticRoutes(Map.of(IdentityTestUtil.ID_1.getIdentityPublicKey(), new InetSocketAddressWrapper("127.0.0.1", 22528)))
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node2 = new EmbeddedNode(config).started();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node2"));

                node1.events(PeerDirectEvent.class).test().awaitCount(1).assertValueCount(1);
                node2.events(PeerDirectEvent.class).test().awaitCount(1).assertValueCount(1);
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
            void applicationMessagesShouldBeDelivered() throws ExecutionException, InterruptedException {
                final TestObserver<MessageEvent> node1Messages = node1.messages().test();
                final TestObserver<MessageEvent> node2Messages = node2.messages().test();

                //
                // send messages
                //
                node1.send(IdentityTestUtil.ID_2.getIdentityPublicKey(), null).toCompletableFuture().get();
                node2.send(IdentityTestUtil.ID_1.getIdentityPublicKey(), null).toCompletableFuture().get();

                //
                // verify
                //
                node1Messages.awaitCount(1).assertValueCount(1);
                node2Messages.awaitCount(1).assertValueCount(1);
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
                        .identityProofOfWork(IdentityTestUtil.ID_1.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_1.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_1.getIdentitySecretKey())
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
                node1 = new EmbeddedNode(config).started();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node1"));

                // node2
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(IdentityTestUtil.ID_2.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_2.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_2.getIdentitySecretKey())
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("0.0.0.0"))
                        .remoteBindPort(0)
                        .remotePingInterval(ofSeconds(1))
                        .remoteSuperPeerEnabled(false)
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .build();
                node2 = new EmbeddedNode(config).started();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node2"));

                node1.events(PeerDirectEvent.class).test().awaitCount(1).assertValueCount(1);
                node2.events(PeerDirectEvent.class).test().awaitCount(1).assertValueCount(1);
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
            void applicationMessagesShouldBeDelivered() throws ExecutionException, InterruptedException {
                final TestObserver<MessageEvent> node1Messages = node1.messages().test();
                final TestObserver<MessageEvent> node2Messages = node2.messages().test();

                //
                // send messages
                //
                node1.send(IdentityTestUtil.ID_2.getIdentityPublicKey(), true).toCompletableFuture().get();
                node1.send(IdentityTestUtil.ID_2.getIdentityPublicKey(), (byte) 23).toCompletableFuture().get();
                node1.send(IdentityTestUtil.ID_2.getIdentityPublicKey(), 'C').toCompletableFuture().get();
                node1.send(IdentityTestUtil.ID_2.getIdentityPublicKey(), 3.141F).toCompletableFuture().get();
                node1.send(IdentityTestUtil.ID_2.getIdentityPublicKey(), 1337).toCompletableFuture().get();
                node1.send(IdentityTestUtil.ID_2.getIdentityPublicKey(), 9001L).toCompletableFuture().get();
                node1.send(IdentityTestUtil.ID_2.getIdentityPublicKey(), (short) 42).toCompletableFuture().get();
                node1.send(IdentityTestUtil.ID_2.getIdentityPublicKey(), new byte[]{
                        (byte) 0,
                        (byte) 1
                }).toCompletableFuture().get();
                node1.send(IdentityTestUtil.ID_2.getIdentityPublicKey(), "String").toCompletableFuture().get();
                node1.send(IdentityTestUtil.ID_2.getIdentityPublicKey(), null).toCompletableFuture().get();

                node2.send(IdentityTestUtil.ID_1.getIdentityPublicKey(), true).toCompletableFuture().get();
                node2.send(IdentityTestUtil.ID_1.getIdentityPublicKey(), (byte) 23).toCompletableFuture().get();
                node2.send(IdentityTestUtil.ID_1.getIdentityPublicKey(), 'C').toCompletableFuture().get();
                node2.send(IdentityTestUtil.ID_1.getIdentityPublicKey(), 3.141F).toCompletableFuture().get();
                node2.send(IdentityTestUtil.ID_1.getIdentityPublicKey(), 1337).toCompletableFuture().get();
                node2.send(IdentityTestUtil.ID_1.getIdentityPublicKey(), 9001L).toCompletableFuture().get();
                node2.send(IdentityTestUtil.ID_1.getIdentityPublicKey(), (short) 42).toCompletableFuture().get();
                node2.send(IdentityTestUtil.ID_1.getIdentityPublicKey(), new byte[]{
                        (byte) 0,
                        (byte) 1
                }).toCompletableFuture().get();
                node2.send(IdentityTestUtil.ID_1.getIdentityPublicKey(), "String").toCompletableFuture().get();
                node2.send(IdentityTestUtil.ID_1.getIdentityPublicKey(), null).toCompletableFuture().get();

                //
                // verify
                //
                node1Messages.awaitCount(10).assertValueCount(10);
                node2Messages.awaitCount(10).assertValueCount(10);
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

            @SuppressWarnings("ConstantConditions")
            @BeforeEach
            void setUp() throws DrasylException {
                //
                // create nodes
                //
                DrasylConfig config;

                // super peer
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(IdentityTestUtil.ID_1.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_1.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_1.getIdentitySecretKey())
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(0)
                        .remotePingInterval(ofSeconds(1))
                        .remoteSuperPeerEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteTcpFallbackServerBindHost(createInetAddress("127.0.0.1"))
                        .remoteTcpFallbackServerBindPort(0)
                        .intraVmDiscoveryEnabled(false)
                        .build();
                superPeer = new EmbeddedNode(config).started();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED superPeer"));

                // client
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(IdentityTestUtil.ID_2.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_2.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_2.getIdentitySecretKey())
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(0)
                        .remotePingInterval(ofSeconds(1))
                        .remoteSuperPeerEndpoints(Set.of(Endpoint.of("udp://127.0.0.1:" + superPeer.getPort() + "?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey())))
                        .remoteLocalHostDiscoveryEnabled(false)
                        .intraVmDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(true)
                        .remoteTcpFallbackClientTimeout(ofSeconds(2))
                        .remoteTcpFallbackClientAddress(createUnresolved("127.0.0.1", superPeer.getTcpFallbackPort()))
                        .build();
                client = new EmbeddedNode(config).started();
                client.pipeline().addAfter(UDP_SERVER, "UDP_BLOCKER", new OutboundMessageFilter<ByteBuf, Address>() {
                    @Override
                    protected boolean accept(final HandlerContext ctx,
                                             final Address sender,
                                             final ByteBuf msg) {
                        return false; // drop all messages
                    }

                    @Override
                    protected void messageRejected(final HandlerContext ctx,
                                                   final Address sender,
                                                   final ByteBuf msg,
                                                   final CompletableFuture<Void> future) {
                        LOG.trace("UDP message blocked: {}", msg);
                        future.complete(null);
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
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void correctPeerEventsShouldBeEmitted() {
                superPeer.events(PeerDirectEvent.class).test()
                        .awaitCount(1)
                        .assertValueCount(1);
                client.events(NodeOnlineEvent.class).test()
                        .awaitCount(1)
                        .assertValueCount(1);
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
                        .identityProofOfWork(IdentityTestUtil.ID_1.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_1.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_1.getIdentitySecretKey())
                        .remoteExposeEnabled(false)
                        .remoteEnabled(false)
                        .remoteSuperPeerEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node1 = new EmbeddedNode(config).started();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node1"));

                // node2
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(IdentityTestUtil.ID_2.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_2.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_2.getIdentitySecretKey())
                        .remoteExposeEnabled(false)
                        .remoteEnabled(false)
                        .remoteSuperPeerEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node2 = new EmbeddedNode(config).started();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node2"));

                // node3
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(IdentityTestUtil.ID_3.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_3.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_3.getIdentitySecretKey())
                        .remoteExposeEnabled(false)
                        .remoteEnabled(false)
                        .remoteSuperPeerEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node3 = new EmbeddedNode(config).started();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node3"));

                // node4
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(IdentityTestUtil.ID_4.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_4.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_4.getIdentitySecretKey())
                        .remoteExposeEnabled(false)
                        .remoteEnabled(false)
                        .remoteSuperPeerEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteLocalNetworkDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node4 = new EmbeddedNode(config).started();
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
             * This test checks whether the messages sent via {@link org.drasyl.intravm.IntraVmDiscovery}
             * are delivered.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void applicationMessagesShouldBeDelivered() {
                node1.events(PeerDirectEvent.class).test().awaitCount(3).assertValueCount(3);
                node2.events(PeerDirectEvent.class).test().awaitCount(3).assertValueCount(3);
                node3.events(PeerDirectEvent.class).test().awaitCount(3).assertValueCount(3);
                node4.events(PeerDirectEvent.class).test().awaitCount(3).assertValueCount(3);

                final TestObserver<MessageEvent> node1Messages = node1.messages().test();
                final TestObserver<MessageEvent> nodes2Messages = node2.messages().test();
                final TestObserver<MessageEvent> node3Messages = node3.messages().test();
                final TestObserver<MessageEvent> node4Messages = node4.messages().test();

                //
                // send messages
                //
                final Set<String> identities = Set.of(IdentityTestUtil.ID_1.getIdentityPublicKey().toString(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey().toString(),
                        IdentityTestUtil.ID_3.getIdentityPublicKey().toString(),
                        IdentityTestUtil.ID_4.getIdentityPublicKey().toString());
                for (final String recipient : identities) {
                    node1.send(recipient, "Hallo Welt");
                    node2.send(recipient, "Hallo Welt");
                    node3.send(recipient, "Hallo Welt");
                    node4.send(recipient, "Hallo Welt");
                }

                //
                // verify
                //
                node1Messages.awaitCount(4).assertValueCount(4)
                        .assertValueAt(0, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(1, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(2, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(3, m -> "Hallo Welt".equals(m.getPayload()));
                nodes2Messages.awaitCount(4).assertValueCount(4)
                        .assertValueAt(0, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(1, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(2, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(3, m -> "Hallo Welt".equals(m.getPayload()));
                node3Messages.awaitCount(4).assertValueCount(4)
                        .assertValueAt(0, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(1, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(2, m -> "Hallo Welt".equals(m.getPayload()));
                node4Messages.awaitCount(4).assertValueCount(4)
                        .assertValueAt(0, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(1, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(2, m -> "Hallo Welt".equals(m.getPayload()))
                        .assertValueAt(3, m -> "Hallo Welt".equals(m.getPayload()));
            }

            /**
             * This test checks whether the {@link org.drasyl.intravm.IntraVmDiscovery} emits the
             * correct {@link PeerEvent}s.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void correctPeerEventsShouldBeEmitted() {
                final TestObserver<PeerDirectEvent> node1Events = node1.events(PeerDirectEvent.class).test();
                final TestObserver<PeerDirectEvent> node2Events = node2.events(PeerDirectEvent.class).test();
                final TestObserver<PeerDirectEvent> node3Events = node3.events(PeerDirectEvent.class).test();
                final TestObserver<PeerDirectEvent> node4Events = node4.events(PeerDirectEvent.class).test();

                node1Events.awaitCount(3).assertValueCount(3);
                node2Events.awaitCount(3).assertValueCount(3);
                node3Events.awaitCount(3).assertValueCount(3);
                node4Events.awaitCount(3).assertValueCount(3);
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

            @BeforeEach
            void setUp(@TempDir final Path localHostDiscoveryPath) throws DrasylException {
                //
                // create nodes
                //
                DrasylConfig config;

                // node1
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(IdentityTestUtil.ID_1.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_1.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_1.getIdentitySecretKey())
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
                node1 = new EmbeddedNode(config).started();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node1"));

                // node2
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(IdentityTestUtil.ID_2.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_2.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_2.getIdentitySecretKey())
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
                node2 = new EmbeddedNode(config).started();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node2"));
            }

            @AfterEach
            void tearDown() {
                node1.close();
                node2.close();
            }

            /**
             * This test checks whether the {@link org.drasyl.localhost.LocalHostDiscovery} emits
             * the correct {@link PeerEvent}s and is able to route outgoing messages.
             */
            @Test
            @Timeout(value = TIMEOUT * 5, unit = MILLISECONDS)
            void applicationMessagesShouldBeDelivered() throws ExecutionException, InterruptedException {
                node1.events(PeerDirectEvent.class).test()
                        .awaitCount(1).awaitCount(1);
                node2.events(PeerDirectEvent.class).test()
                        .awaitCount(1).awaitCount(1);

                final TestObserver<MessageEvent> node1Messages = node1.messages().test();
                final TestObserver<MessageEvent> nodes2Messages = node2.messages().test();

                //
                // send messages
                //
                node1.send(IdentityTestUtil.ID_2.getIdentityPublicKey(), "Hallo Welt").toCompletableFuture().get();
                node2.send(IdentityTestUtil.ID_1.getIdentityPublicKey(), "Hallo Welt").toCompletableFuture().get();

                //
                // verify
                //
                node1Messages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(MessageEvent.of(IdentityTestUtil.ID_2.getIdentityPublicKey(), "Hallo Welt"));
                nodes2Messages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(MessageEvent.of(IdentityTestUtil.ID_1.getIdentityPublicKey(), "Hallo Welt"));
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
                    .identityProofOfWork(IdentityTestUtil.ID_1.getProofOfWork())
                    .identityPublicKey(IdentityTestUtil.ID_1.getIdentityPublicKey())
                    .identitySecretKey(IdentityTestUtil.ID_1.getIdentitySecretKey())
                    .remoteExposeEnabled(false)
                    .remoteEnabled(false)
                    .remoteSuperPeerEnabled(false)
                    .intraVmDiscoveryEnabled(false)
                    .remoteLocalHostDiscoveryEnabled(false)
                    .remoteTcpFallbackEnabled(false)
                    .build();
            node = new EmbeddedNode(config).started();
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
        void applicationMessagesShouldBeDelivered() throws ExecutionException, InterruptedException {
            final TestObserver<MessageEvent> node1Messages = node.messages().test();

            node.send(IdentityTestUtil.ID_1.getIdentityPublicKey(), "Hallo Welt").toCompletableFuture().get();

            node1Messages.awaitCount(1).assertValueCount(1)
                    .assertValue(m -> "Hallo Welt".equals(m.getPayload()));
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
                        .identityProofOfWork(IdentityTestUtil.ID_1.getProofOfWork())
                        .identityPublicKey(IdentityTestUtil.ID_1.getIdentityPublicKey())
                        .identitySecretKey(IdentityTestUtil.ID_1.getIdentitySecretKey())
                        .remoteExposeEnabled(false)
                        .remoteEnabled(false)
                        .remoteSuperPeerEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteTcpFallbackEnabled(false)
                        .build();
                node = new EmbeddedNode(config);
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED node"));
            }

            @AfterEach
            void tearDown() {
                node.close();
            }

            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void sendToSelfShouldThrowException() {
                assertThrows(ExecutionException.class, () -> node.send(IdentityTestUtil.ID_1.getIdentityPublicKey(), "Hallo Welt").toCompletableFuture().get());
            }

            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void sendToAnOtherPeerShouldThrowException() {
                assertThrows(ExecutionException.class, () -> node.send(IdentityTestUtil.ID_2.getIdentityPublicKey(), "Hallo Welt").toCompletableFuture().get());
            }
        }
    }

    @Nested
    class Start {
        private DrasylConfig.Builder configBuilder;

        @BeforeEach
        void setUp() {
            configBuilder = DrasylConfig.newBuilder()
                    .networkId(0)
                    .identityProofOfWork(IdentityTestUtil.ID_1.getProofOfWork())
                    .identityPublicKey(IdentityTestUtil.ID_1.getIdentityPublicKey())
                    .identitySecretKey(IdentityTestUtil.ID_1.getIdentitySecretKey())
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
                final DrasylConfig config = configBuilder
                        .remoteBindPort(socket.getLocalPort())
                        .build();
                final EmbeddedNode node = new EmbeddedNode(config);
                final TestObserver<Event> events = node.events().test();
                final CompletableFuture<Void> future = node.start();

                await().untilAsserted(() -> assertTrue(future.isDone()));
                events.assertError(e -> e instanceof Exception);
            }
        }
    }
}
