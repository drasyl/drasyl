/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.core.server;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.awaitility.Durations;
import org.drasyl.core.common.message.*;
import org.drasyl.core.models.CompressedKeyPair;
import org.drasyl.core.models.CompressedPrivateKey;
import org.drasyl.core.models.CompressedPublicKey;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.DrasylNodeConfig;
import org.drasyl.core.node.Messenger;
import org.drasyl.core.node.PeersManager;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.node.connections.NettyPeerConnection;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityManager;
import org.drasyl.core.server.testutils.ANSI_COLOR;
import org.drasyl.core.server.testutils.BetterArrayList;
import org.drasyl.core.server.testutils.TestHelper;
import org.drasyl.core.server.testutils.TestServerConnection;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.junit.Ignore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.awaitility.Awaitility.with;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//import net.jcip.annotations.NotThreadSafe;

//@NotThreadSafe
@Execution(ExecutionMode.SAME_THREAD)
public class NodeServerIT {
    private IdentityManager identityManager;
    public static final long TIMEOUT = 10000L;
    private NodeServer server;
    private final BetterArrayList<NettyPeerConnection> clientConnections = new BetterArrayList<>();
    DrasylNodeConfig config;
    private static EventLoopGroup workerGroup;
    private static EventLoopGroup bossGroup;

    @BeforeAll
    public static void beforeAll() {
        workerGroup = new NioEventLoopGroup();
        bossGroup = new NioEventLoopGroup(1);
    }

    @AfterAll
    public static void afterAll() {
        workerGroup.shutdownGracefully().syncUninterruptibly();
        bossGroup.shutdownGracefully().syncUninterruptibly();
    }

    @BeforeEach
    public void setup() throws DrasylException, CryptoException {
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        identityManager = mock(IdentityManager.class);
        PeersManager peersManager = new PeersManager();
        Messenger messenger = new Messenger(identityManager, peersManager, event -> {
        });

        config = new DrasylNodeConfig(
                ConfigFactory.load("configs/ClientTest.conf"));

        server = new NodeServer(identityManager, messenger, peersManager, config, workerGroup, bossGroup);

        TestHelper.waitUntilNetworkAvailable(config.getServerBindPort());
        server.open();

        CompressedKeyPair keyPair = mock(CompressedKeyPair.class);
        CompressedPublicKey publicKey = CompressedPublicKey.of("0343bc674c4e58a289d3904a16f83177581770d32e3ee0d63b7c75ee2b32c733b1");
        CompressedPrivateKey privateKey = CompressedPrivateKey.of("0c5d76039113707512c15d23f27c963fa2b636672ae86c66f68e588203556775");

        when(identityManager.getKeyPair()).thenReturn(keyPair);
        when(keyPair.getPublicKey()).thenReturn(publicKey);
        when(keyPair.getPrivateKey()).thenReturn(privateKey);
        when(identityManager.getIdentity()).thenReturn(Identity.of(publicKey));
    }

    @AfterEach
    public void cleanUp() {
        clientConnections.forEach(s -> s.send(new LeaveMessage()));
        clientConnections.clear();

        server.close();
    }

    @Test
    public void handshakeTest() {
        TestHelper.println("STARTING handshakeTest2()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        try {
            CountDownLatch lock = new CountDownLatch(1);

            TestServerConnection session = TestServerConnection.build(server);
            clientConnections.add(session);

            session.send(new JoinMessage(identityManager.getKeyPair().getPublicKey(), Set.of()),
                    WelcomeMessage.class).blockingSubscribe(response -> {
                lock.countDown();
                session.send(new LeaveMessage());
            });

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        }
        catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED handshakeTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void multipleHandshakeTest() {
        TestHelper.println("STARTING multipleHandshakeTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(2);

        try {
            TestServerConnection session1 = TestServerConnection.build(server);
            clientConnections.add(session1);
            TestServerConnection session2 = TestServerConnection.build(server);
            clientConnections.add(session2);

            session1.send(new JoinMessage(CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3"), Set.of()),
                    WelcomeMessage.class).subscribe(response -> lock.countDown());
            session2.send(new JoinMessage(CompressedPublicKey.of("0340a4f2adbddeedc8f9ace30e3f18713a3405f43f4871b4bac9624fe80d2056a7"), Set.of()),
                    WelcomeMessage.class).subscribe(response -> lock.countDown());

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        }
        catch (InterruptedException | ExecutionException | CryptoException e) {
            e.printStackTrace();
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED multipleHandshakeTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void getClientsStockingTest() {
        TestHelper.println("STARTING getClientsStockingTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(1);

        try {
            TestServerConnection session1 = TestServerConnection.buildAutoJoin(server);
            clientConnections.add(session1);
            TestServerConnection session2 = TestServerConnection.buildAutoJoin(server);
            clientConnections.add(session2);

            with().pollInSameThread().await().pollDelay(0, TimeUnit.NANOSECONDS).atMost(Durations.FIVE_MINUTES)
                    .until(() -> server.getPeersManager().getChildren().size() >= 2);

            session1.send(new RequestClientsStocktakingMessage(), ClientsStocktakingMessage.class).subscribe(response -> {
                assertTrue(response.getIdentities().contains(session1.getIdentity()));
                assertTrue(response.getIdentities().contains(session2.getIdentity()));
                lock.countDown();
            });

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        }
        catch (InterruptedException | ExecutionException | CryptoException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED getClientsStockingTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void forwardTest() {
        TestHelper.println("STARTING forwardTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(2);

        try {
            TestServerConnection session1 = TestServerConnection.buildAutoJoin(server);
            clientConnections.add(session1);
            TestServerConnection session2 = TestServerConnection.buildAutoJoin(server);
            clientConnections.add(session2);

            ApplicationMessage msg = new ApplicationMessage(session1.getIdentity(), session2.getIdentity(), new byte[]{
                    0x00,
                    0x01,
                    0x02
            });

            session2.addListener(message -> {
                if (message instanceof ApplicationMessage) {
                    ApplicationMessage f = (ApplicationMessage) message;
                    lock.countDown();
                    assertEquals(msg, f);
                }
            });

            session1.send(msg, StatusMessage.class).subscribe(response -> lock.countDown());

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        }
        catch (InterruptedException | ExecutionException | CryptoException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED forwardTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void emptyBlobTest() {
        TestHelper.println("STARTING emptyBlobTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(2);

        try {
            TestServerConnection session1 = TestServerConnection.buildAutoJoin(server);
            clientConnections.add(session1);
            TestServerConnection session2 = TestServerConnection.buildAutoJoin(server);
            clientConnections.add(session1);

            ApplicationMessage msg = new ApplicationMessage(session1.getIdentity(), session2.getIdentity(), new byte[]{});

            session2.addListener(message -> {
                if (message instanceof ApplicationMessage) {
                    ApplicationMessage f = (ApplicationMessage) message;
                    lock.countDown();
                    assertEquals(msg, f);
                }
            });

            session1.send(msg, StatusMessage.class).subscribe(response -> lock.countDown());

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        }
        catch (InterruptedException | ExecutionException | CryptoException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED emptyBlobTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void timeoutTest() {
        TestHelper.println("STARTING timeoutTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
        CountDownLatch lock = new CountDownLatch(1);

        try {
            TestServerConnection session = TestServerConnection.build(server);
            clientConnections.add(session);

            session.addListener(message -> {
                if (message instanceof MessageExceptionMessage) {
                    MessageExceptionMessage e = (MessageExceptionMessage) message;

                    assertEquals(
                            "Handshake did not take place successfully in "
                                    + server.getConfig().getServerHandshakeTimeout().toMillis() + " ms. Connection is closed.",
                            e.getException());
                    lock.countDown();
                }
            });

            lock.await(server.getConfig().getServerHandshakeTimeout().toMillis() + 2000, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());

            assertTrue(session.isClosed().get(10, TimeUnit.SECONDS));
        }
        catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED timeoutTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void timeoutNegativeTest() {
        TestHelper.println("STARTING timeoutNegativeTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
        Logger clientLogger = (Logger) LoggerFactory.getLogger(ClientConnection.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        clientLogger.addAppender(listAppender);

        try {
            TestServerConnection session = TestServerConnection.buildAutoJoin(server);
            clientConnections.add(session);

            session.addListener(message -> assertThat(message, is(not(instanceOf(MessageExceptionMessage.class)))));

            // Wait until timeout
            Thread.sleep(server.getConfig().getServerHandshakeTimeout().toMillis() + 2000);// NOSONAR

            for (ILoggingEvent event : ImmutableList.copyOf(listAppender.list)) {
                if (event.getLevel().equals(Level.INFO)) {
                    assertNotEquals(event.getMessage(), "{} Handshake did not take place successfully in {} ms. " + "Connection is closed.");
                }
            }
        }
        catch (InterruptedException | ExecutionException | CryptoException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED timeoutNegativeTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void invalidMessageTest() {
        TestHelper.println("STARTING invalidMessageTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
        CountDownLatch lock = new CountDownLatch(2);

        try {
            TestServerConnection session = TestServerConnection.build(server);
            clientConnections.add(session);

            session.addListener(message -> {
                if (message instanceof MessageExceptionMessage) {
                    MessageExceptionMessage e = (MessageExceptionMessage) message;

                    assertEquals("java.lang.IllegalArgumentException: Your request dit not contain a valid Message: Unrecognized token 'invalid': was expecting ('true', 'false' or 'null')\n" +
                            " at [Source: (String)\"invalid message\"; line: 1, column: 8]", e.getException());

                    lock.countDown();
                }
                if (message instanceof ConnectionExceptionMessage) {
                    ConnectionExceptionMessage e = (ConnectionExceptionMessage) message;

                    assertEquals("Exception occurred during initialization stage. The connection will shut down.", e.getException());

                    lock.countDown();
                }
            });

            session.sendRawString("invalid message");

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        }
        catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED invalidMessageTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void newSessionWithSameIdentityShouldReplaceAndCloseExistingSession() {
        TestHelper.println("STARTING multipleHandshakeWithSameIDTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(1);

        try {
            TestServerConnection session1 = TestServerConnection.build(server);
            clientConnections.add(session1);
            TestServerConnection session2 = TestServerConnection.build(server);
            clientConnections.add(session2);

            session1.send(new JoinMessage(CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3"), Set.of()),
                    WelcomeMessage.class).blockingGet();
            session1.addListener(message -> {
                if (message instanceof LeaveMessage) {
                    lock.countDown();
                }
            });
            session2.send(new JoinMessage(CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3"), Set.of()),
                    WelcomeMessage.class).blockingGet();

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
            assertTrue(session1.isClosed().get(10, TimeUnit.SECONDS));
        }
        catch (InterruptedException | ExecutionException | CryptoException | TimeoutException e) {
            e.printStackTrace();
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED multipleHandshakeWithSameIDTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void multipleHandshakeWithObjectTest() {
        TestHelper.println("STARTING multipleHandshakeWithObjectTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(1);

        try {
            TestServerConnection session = TestServerConnection.buildAutoJoin(server);
            clientConnections.add(session);

            session.send(new JoinMessage(CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3"), Set.of()), MessageExceptionMessage.class)
                    .subscribe(response -> {
                        Assert.assertEquals("This client has already an open "
                                + "session with this node server. No need to authenticate twice.", response.getException());
                        lock.countDown();
                    });

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        }
        catch (InterruptedException | ExecutionException | CryptoException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED multipleHandshakeWithObjectTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void idleTimoutTest() {
        TestHelper.println("STARTING idleTimoutTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(2);
        try {
            TestServerConnection session = TestServerConnection.build(server, false);
            clientConnections.add(session);

            session.addListener(message -> {
                if (message instanceof MessageExceptionMessage) {
                    MessageExceptionMessage m = (MessageExceptionMessage) message;
                    assertThat(m.getException(),
                            is(equalTo("Max retries for ping/pong requests reached. Connection will be closed.")));
                    lock.countDown();
                }

                if (message instanceof PingMessage) {
                    lock.countDown();
                }
            });

            // Wait until timeout
            Thread.sleep(server.getConfig().getServerIdleTimeout().toMillis() * (server.getConfig().getServerIdleRetries() + 1) + 1000);// NOSONAR

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        }
        catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED idleTimoutTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void idleTimoutNegativeTest() {
        TestHelper.println("STARTING idleTimoutNegativeTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        try {
            TestServerConnection session = TestServerConnection.buildAutoJoin(server);
            clientConnections.add(session);

            session.addListener(message -> assertThat(message, is(not(instanceOf(MessageExceptionMessage.class)))));

            // Wait until timeout
            Thread.sleep(server.getConfig().getServerIdleTimeout().toMillis() * (server.getConfig().getServerIdleRetries() + 1) + 1000);// NOSONAR
        }
        catch (InterruptedException | ExecutionException | CryptoException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED idleTimoutNegativeTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void serverPingResponseTest() {
        TestHelper.println("STARTING serverPingResponseTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(1);
        try {
            TestServerConnection session = TestServerConnection.build(server, false);
            clientConnections.add(session);

            session.addListener(message -> {
                if (message instanceof PongMessage) {
                    lock.countDown();
                }
            });

            session.send(new PingMessage());

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        }
        catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED serverPingResponseTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void sendBeforeAuthTest() {
        TestHelper.println("STARTING sendBeforeAuthTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
        CountDownLatch lock = new CountDownLatch(1);

        try {
            TestServerConnection session = TestServerConnection.build(server);
            clientConnections.add(session);

            ResponseMessage<MessageExceptionMessage> msg = new ResponseMessage<>(new MessageExceptionMessage("Test"), Crypto.randomString(12));

            session.send(msg, StatusMessage.class).subscribe(response -> {
                assertThat(response, anyOf(
                        equalTo(StatusMessage.FORBIDDEN)));

                lock.countDown();
            });

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        }
        catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED sendBeforeAuthTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Ignore
    public void messageWithMaxSizeShouldArrive() {
        TestHelper.println("STARTING maxSizeExceededTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(2);

        try {
            TestServerConnection session1 = TestServerConnection.buildAutoJoin(server);
            clientConnections.add(session1);
            TestServerConnection session2 = TestServerConnection.buildAutoJoin(server);
            clientConnections.add(session2);

            byte[] bigPayload = new byte[config.getMaxContentLength()];
            new Random().nextBytes(bigPayload);

            ApplicationMessage msg = new ApplicationMessage(session1.getIdentity(), session2.getIdentity(), bigPayload);

            session2.addListener(message -> {
                if (message instanceof ApplicationMessage) {
                    ApplicationMessage f = (ApplicationMessage) message;
                    lock.countDown();
                    assertEquals(msg, f);
                }
            });

            session1.send(msg, StatusMessage.class).subscribe(response -> lock.countDown());

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        }
        catch (InterruptedException | ExecutionException | CryptoException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED forwardTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Ignore
    public void maxSizeExceededTest() {
        TestHelper.println("STARTING maxSizeExceededTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(2);

        try {
            TestServerConnection session1 = TestServerConnection.buildAutoJoin(server);
            clientConnections.add(session1);
            TestServerConnection session2 = TestServerConnection.buildAutoJoin(server);
            clientConnections.add(session2);

            byte[] bigPayload = new byte[config.getMaxContentLength() + 1];
            new Random().nextBytes(bigPayload);

            ApplicationMessage msg = new ApplicationMessage(session1.getIdentity(), session2.getIdentity(), bigPayload);

            session2.addListener(message -> {
                if (message instanceof ApplicationMessage) {
                    fail("The message should not arrive!");
                }
            });

            session1.addListener(message -> {
                if (message instanceof NodeServerException) {
                    lock.countDown();
                }
            });

            session1.send(msg, StatusMessage.class).subscribe(response -> lock.countDown());

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        }
        catch (InterruptedException | ExecutionException | CryptoException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED forwardTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void shouldOpenAndCloseGracefully() throws DrasylException {
        NodeServer server = new NodeServer(identityManager, mock(Messenger.class), mock(PeersManager.class), workerGroup, bossGroup);

        server.open();
        server.close();

        assertTrue(true);
    }

    @Test
    public void openShouldFailIfInvalidPortIsGiven() throws DrasylException {
        Config config =
                ConfigFactory.parseString("drasyl.server.bind-port = 72522").withFallback(ConfigFactory.load());
        NodeServer server = new NodeServer(identityManager, mock(Messenger.class), mock(PeersManager.class), config, workerGroup, bossGroup);

        assertThrows(NodeServerException.class, server::open);
    }

    @Test
    public void serverShouldSayByeOnClose() {
        TestHelper.println("STARTING serverShouldSayByeOnClose()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        try {
            CountDownLatch lock = new CountDownLatch(2);

            TestServerConnection session = TestServerConnection.build(server);
            clientConnections.add(session);

            session.addListener(message -> {
                if (message instanceof LeaveMessage) {
                    lock.countDown();
                }
            });

            session.send(new JoinMessage(identityManager.getKeyPair().getPublicKey(), Set.of()),
                    WelcomeMessage.class).blockingSubscribe(response -> lock.countDown());

            server.close();

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        }
        catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED serverShouldSayByeOnClose()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void serverShouldNotAllowNewConnectionsOnClose() {
        TestHelper.println("STARTING serverShouldNotAllowNewConnectionsOnClose()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        try {
            CountDownLatch lock = new CountDownLatch(2);

            TestServerConnection session = TestServerConnection.build(server);
            clientConnections.add(session);
            TestServerConnection session2 = TestServerConnection.build(server);
            clientConnections.add(session2);

            session2.addListener(message -> {
                if (message instanceof RejectMessage) {
                    lock.countDown();
                }
            });

            session.send(new JoinMessage(identityManager.getKeyPair().getPublicKey(), Set.of()),
                    WelcomeMessage.class).blockingSubscribe(response -> lock.countDown());

            server.addBeforeCloseListener(() -> {
                try {
                    session2.send(new JoinMessage(CompressedPublicKey.of("0340a4f2adbddeedc8f9ace30e3f18713a3405f43f4871b4bac9624fe80d2056a7"), Set.of()));
                }
                catch (CryptoException e) {
                    e.printStackTrace();
                }
            });

            server.close();

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        }
        catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED serverShouldNotAllowNewConnectionsOnClose()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }
}
