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
package org.drasyl.core.server.session;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.ConfigFactory;
import org.awaitility.Durations;
import org.drasyl.core.common.messages.*;
import org.drasyl.core.models.CompressedKeyPair;
import org.drasyl.core.models.CompressedPrivateKey;
import org.drasyl.core.models.CompressedPublicKey;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.DrasylNode;
import org.drasyl.core.node.DrasylNodeConfig;
import org.drasyl.core.node.Messenger;
import org.drasyl.core.node.PeersManager;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityManager;
import org.drasyl.core.server.NodeServer;
import org.drasyl.core.server.testutils.ANSI_COLOR;
import org.drasyl.core.server.testutils.BetterArrayList;
import org.drasyl.core.server.testutils.TestHelper;
import org.drasyl.core.server.testutils.TestSession;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.with;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//import net.jcip.annotations.NotThreadSafe;

//@NotThreadSafe
@Execution(ExecutionMode.SAME_THREAD)
public class ClientIT {
    private IdentityManager identityManager;
    public static final long TIMEOUT = 10000L;
    private NodeServer server;
    private final BetterArrayList<ServerSession> serverSessions = new BetterArrayList<>();

    @BeforeEach
    public void setup() throws DrasylException, CryptoException {
        DrasylNode node = mock(DrasylNode.class);
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        identityManager = mock(IdentityManager.class);
        PeersManager peersManager = new PeersManager();
        Messenger messenger = new Messenger(identityManager, peersManager);

        DrasylNodeConfig config = new DrasylNodeConfig(
                ConfigFactory.load("configs/ClientTest.conf"));

        server = new NodeServer(identityManager, messenger, peersManager, config);

        TestHelper.waitUntilNetworkAvailable(config.getServerEntryPoint().getPort());
        server.open();
        server.awaitOpen();

        CompressedKeyPair keyPair = mock(CompressedKeyPair.class);
        CompressedPublicKey publicKey = CompressedPublicKey.of("0343bc674c4e58a289d3904a16f83177581770d32e3ee0d63b7c75ee2b32c733b1");
        CompressedPrivateKey privateKey = CompressedPrivateKey.of("0c5d76039113707512c15d23f27c963fa2b636672ae86c66f68e588203556775");

        when(identityManager.getKeyPair()).thenReturn(keyPair);
        when(keyPair.getPublicKey()).thenReturn(publicKey);
        when(keyPair.getPrivateKey()).thenReturn(privateKey);
        when(identityManager.getIdentity()).thenReturn(Identity.of(publicKey));
    }

    @AfterEach
    public void cleanUp() throws org.drasyl.core.server.NodeServerException {
        serverSessions.forEach(s -> s.send(new Leave()));
        serverSessions.clear();

        server.close();
        server.awaitClose();
    }

    @Test
    public void handshakeTest() {
        TestHelper.println("STARTING handshakeTest2()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        try {
            CountDownLatch lock = new CountDownLatch(1);

            TestSession session = TestSession.build(server);
            serverSessions.add(session);

            session.send(new Join(identityManager.getKeyPair().getPublicKey(), Set.of()),
                    Welcome.class).blockingSubscribe(response -> {
                lock.countDown();
                session.send(new Leave());
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
            TestSession session1 = TestSession.build(server);
            serverSessions.add(session1);
            TestSession session2 = TestSession.build(server);
            serverSessions.add(session2);

            session1.send(new Join(CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3"), Set.of()),
                    Welcome.class).subscribe(response -> lock.countDown());
            session2.send(new Join(CompressedPublicKey.of("0340a4f2adbddeedc8f9ace30e3f18713a3405f43f4871b4bac9624fe80d2056a7"), Set.of()),
                    Welcome.class).subscribe(response -> lock.countDown());

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
            TestSession session1 = TestSession.buildAutoJoin(server);
            serverSessions.add(session1);
            TestSession session2 = TestSession.buildAutoJoin(server);
            serverSessions.add(session2);

            with().pollInSameThread().await().pollDelay(0, TimeUnit.NANOSECONDS).atMost(Durations.FIVE_MINUTES)
                    .until(() -> server.getPeersManager().getChildren().size() >= 2);

            session1.send(new RequestClientsStocktaking(), ClientsStocktaking.class).subscribe(response -> {
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
            TestSession session1 = TestSession.buildAutoJoin(server);
            serverSessions.add(session1);
            TestSession session2 = TestSession.buildAutoJoin(server);
            serverSessions.add(session2);

            Message msg = new Message(session1.getIdentity(), session2.getIdentity(), new byte[]{
                    0x00,
                    0x01,
                    0x02
            });

            session2.addListener(message -> {
                if (message instanceof Message) {
                    Message f = (Message) message;
                    lock.countDown();
                    assertEquals(msg, f);
                }
            });

            session1.send(msg, Status.class).subscribe(response -> lock.countDown());

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
            TestSession session1 = TestSession.buildAutoJoin(server);
            serverSessions.add(session1);
            TestSession session2 = TestSession.buildAutoJoin(server);
            serverSessions.add(session1);

            Message msg = new Message(session1.getIdentity(), session2.getIdentity(), new byte[]{});

            session2.addListener(message -> {
                if (message instanceof Message) {
                    Message f = (Message) message;
                    lock.countDown();
                    assertEquals(msg, f);
                }
            });

            session1.send(msg, Status.class).subscribe(response -> lock.countDown());

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
            TestSession session = TestSession.build(server);
            serverSessions.add(session);

            session.addListener(message -> {
                if (message instanceof org.drasyl.core.common.messages.NodeServerException) {
                    org.drasyl.core.common.messages.NodeServerException e = (org.drasyl.core.common.messages.NodeServerException) message;

                    assertEquals(
                            "Handshake did not take place successfully in "
                                    + server.getConfig().getServerHandshakeTimeout().toMillis() + " ms. Connection is closed.",
                            e.getException());
                    lock.countDown();
                }
            });

            lock.await(server.getConfig().getServerHandshakeTimeout().toMillis() + 2000, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());

            with().pollInSameThread().await().pollDelay(0, TimeUnit.NANOSECONDS).atMost(Durations.TEN_SECONDS)
                    .until(() -> {
                        return session.isClosed;
                    });
        }
        catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED timeoutTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void timeoutNegativeTest() {
        TestHelper.println("STARTING timeoutNegativeTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
        Logger clientLogger = (Logger) LoggerFactory.getLogger(ServerSession.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        clientLogger.addAppender(listAppender);

        try {
            TestSession session = TestSession.buildAutoJoin(server);
            serverSessions.add(session);

            session.addListener(message -> {
                assertThat(message, is(not(instanceOf(org.drasyl.core.common.messages.NodeServerException.class))));
            });

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
            TestSession session = TestSession.build(server);
            serverSessions.add(session);

            session.addListener(message -> {
                if (message instanceof org.drasyl.core.common.messages.NodeServerException) {
                    org.drasyl.core.common.messages.NodeServerException e = (org.drasyl.core.common.messages.NodeServerException) message;

                    assertThat(e.getException(), anyOf(
                            equalTo("java.lang.IllegalArgumentException: Your request was not a valid Message Object: 'invalid message'"),
                            equalTo("Exception occurred during initialization stage. The connection will shut down.")
                    ));

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
    public void multipleHandshakeWithSameIDTest() {
        TestHelper.println("STARTING multipleHandshakeWithSameIDTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(1);

        try {
            TestSession session1 = TestSession.build(server);
            serverSessions.add(session1);
            TestSession session2 = TestSession.build(server);
            serverSessions.add(session2);

            session1.send(new Join(CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3"), Set.of()),
                    Welcome.class).blockingGet();
            session2.send(new Join(CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3"), Set.of()),
                    org.drasyl.core.common.messages.NodeServerException.class).subscribe(response -> {
                Assert.assertEquals("This client has already an open "
                        + "session with this node server. Can't open more sockets.", response.getException());
                lock.countDown();
            });

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        }
        catch (InterruptedException | ExecutionException | CryptoException e) {
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
            TestSession session = TestSession.buildAutoJoin(server);
            serverSessions.add(session);

            session.send(new Join(CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3"), Set.of()), org.drasyl.core.common.messages.NodeServerException.class)
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
            TestSession session = TestSession.build(server, false);
            serverSessions.add(session);

            session.addListener(message -> {
                if (message instanceof org.drasyl.core.common.messages.NodeServerException) {
                    org.drasyl.core.common.messages.NodeServerException m = (org.drasyl.core.common.messages.NodeServerException) message;
                    assertThat(m.getException(),
                            is(equalTo("Max retries for ping/pong requests reached. Connection will be closed.")));
                    lock.countDown();
                }

                if (message instanceof Ping) {
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
            TestSession session = TestSession.buildAutoJoin(server);
            serverSessions.add(session);

            session.addListener(message -> {
                assertThat(message, is(not(instanceOf(org.drasyl.core.common.messages.NodeServerException.class))));
            });

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
            TestSession session = TestSession.build(server, false);
            serverSessions.add(session);

            session.addListener(message -> {
                if (message instanceof Pong) {
                    lock.countDown();
                }
            });

            session.send(new Ping());

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
            TestSession session = TestSession.build(server);
            serverSessions.add(session);

            Response<org.drasyl.core.common.messages.NodeServerException> msg = new Response<>(new org.drasyl.core.common.messages.NodeServerException("Test"), Crypto.randomString(12));

            session.send(msg, Status.class).subscribe(response -> {
                assertThat(response, anyOf(
                        equalTo(Status.FORBIDDEN)));

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
}
