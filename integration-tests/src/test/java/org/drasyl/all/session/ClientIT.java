/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.session;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.ConfigFactory;
import org.awaitility.Durations;
import org.drasyl.all.Drasyl;
import org.drasyl.all.DrasylConfig;
import org.drasyl.all.DrasylException;
import org.drasyl.all.messages.*;
import org.drasyl.all.models.SessionChannel;
import org.drasyl.all.models.SessionUID;
import org.drasyl.all.testutils.ANSI_COLOR;
import org.drasyl.all.testutils.BetterArrayList;
import org.drasyl.all.testutils.TestHelper;
import org.drasyl.all.testutils.TestSession;
import org.drasyl.all.util.random.RandomUtil;
import org.junit.Assert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.with;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.*;

//import net.jcip.annotations.NotThreadSafe;
//import org.junit.jupiter.api.parallel.Execution;
//import org.junit.jupiter.api.parallel.ExecutionMode;

//@NotThreadSafe
//@Execution(ExecutionMode.SAME_THREAD)
public class ClientIT {
    public static final long TIMEOUT = 10000L;
    private static final DrasylConfig config = new DrasylConfig(
            ConfigFactory.load("configs/ClientTest.conf"));
    private static Drasyl drasyl;
    private BetterArrayList<Session> sessions = new BetterArrayList<>();

    @BeforeAll
    public static void setup() throws DrasylException {
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");

        drasyl = new Drasyl(config);

        TestHelper.waitUntilNetworkAvailable(config.getRelayPort());
        drasyl.open();
        drasyl.awaitOpen();
    }

    @AfterAll
    public static void tearDown() throws DrasylException {
        drasyl.close();
        drasyl.awaitClose();
    }

    @AfterEach
    public void cleanUp() {
        sessions.forEach(s -> s.sendMessage(new Leave()));
        sessions.clear();
    }

    @Test
    public void handshakeTest() {
        TestHelper.println("STARTING handshakeTest2()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        try {
            CountDownLatch lock = new CountDownLatch(1);

            TestSession session = TestSession.build(drasyl);
            sessions.add(session);
            session.sendMessageWithResponse(new Join(session.getUID(), Set.of()),
                    Welcome.class).thenAccept(response -> {
                lock.countDown();
                session.sendMessage(new Leave());
            });


            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED handshakeTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void multipleHandshakeTest() {
        TestHelper.println("STARTING multipleHandshakeTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(2);
        Set<SessionChannel> sessionChannels = Set.of(SessionChannel.of("testChannel"));

        try {
            TestSession session1 = TestSession.build(drasyl);
            sessions.add(session1);
            TestSession session2 = TestSession.build(drasyl);
            sessions.add(session2);

            session1.sendMessageWithResponse(new Join(session1.getUID(), sessionChannels),
                    Welcome.class).thenAccept(response -> lock.countDown());
            session2.sendMessageWithResponse(new Join(session2.getUID(), sessionChannels),
                    Welcome.class).thenAccept(response -> lock.countDown());

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED multipleHandshakeTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void getClientsStockingTest() {
        TestHelper.println("STARTING getClientsStockingTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(1);

        try {
            TestSession session1 = TestSession.build(drasyl, Set.of());
            sessions.add(session1);
            TestSession session2 = TestSession.build(drasyl, Set.of());
            sessions.add(session2);

            with().pollInSameThread().await().pollDelay(0, TimeUnit.NANOSECONDS).atMost(Durations.FIVE_MINUTES)
                    .until(() -> drasyl.getClientBucket().getClientUIDs().size() >= 2);

            session1.sendMessageWithResponse(new RequestClientsStocktaking(), ClientsStocktaking.class).thenAccept(response -> {
                assertTrue(response.getClientUIDs().contains(session1.getUID()));
                assertTrue(response.getClientUIDs().contains(session2.getUID()));
                lock.countDown();
            });

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED getClientsStockingTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void forwardTest() {
        TestHelper.println("STARTING forwardTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(2);
        Set<SessionChannel> sessionChannels = Set.of(SessionChannel.of("testChannel3"));

        try {
            TestSession session1 = TestSession.build(drasyl, sessionChannels);
            sessions.add(session1);
            TestSession session2 = TestSession.build(drasyl, sessionChannels);
            sessions.add(session2);

            ForwardableMessage msg = new ForwardableMessage(session1.getUID(), session2.getUID(), new byte[]{0x00, 0x01,
                    0x02});

            session2.addListener(message -> {
                if (message instanceof ForwardableMessage) {
                    ForwardableMessage f = (ForwardableMessage) message;
                    lock.countDown();
                    Assert.assertEquals(msg, f);
                }
            });

            session1.sendMessageWithResponse(msg, Status.class).thenAccept(response -> lock.countDown());


            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED forwardTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void forwardMulticastTest() {
        TestHelper.println("STARTING forwardMulticastTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(3);
        Set<SessionChannel> sessionChannels = Set.of(SessionChannel.of("testChannel3"));

        try {
            TestSession session1 = TestSession.build(drasyl, sessionChannels);
            sessions.add(session1);
            TestSession session2 = TestSession.build(drasyl, sessionChannels);
            sessions.add(session2);
            TestSession session3 = TestSession.build(drasyl, sessionChannels);
            sessions.add(session3);

            ForwardableMessage msg = new ForwardableMessage(session1.getUID(), SessionUID.of(session2.getUID(),
                    session3.getUID()), new byte[]{0x00, 0x01, 0x02});

            session2.addListener(message -> {
                if (message instanceof ForwardableMessage) {
                    ForwardableMessage f = (ForwardableMessage) message;
                    lock.countDown();
                }
            });

            session3.addListener(message -> {
                if (message instanceof ForwardableMessage) {
                    ForwardableMessage f = (ForwardableMessage) message;
                    lock.countDown();
                }
            });

            session1.sendMessageWithResponse(msg, Status.class).thenAccept(response -> lock.countDown());


            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED forwardMulticastTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void emptyBlobTest() {
        TestHelper.println("STARTING emptyBlobTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(2);
        Set<SessionChannel> sessionChannels = Set.of(SessionChannel.of("testChannel3"));

        try {
            TestSession session1 = TestSession.build(drasyl, sessionChannels);
            sessions.add(session1);
            TestSession session2 = TestSession.build(drasyl, sessionChannels);
            sessions.add(session1);

            ForwardableMessage msg = new ForwardableMessage(session1.getUID(), session2.getUID(), new byte[]{});

            session2.addListener(message -> {
                if (message instanceof ForwardableMessage) {
                    ForwardableMessage f = (ForwardableMessage) message;
                    lock.countDown();
                    Assert.assertEquals(msg, f);
                }
            });

            session1.sendMessageWithResponse(msg, Status.class).thenAccept(response -> lock.countDown());


            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED emptyBlobTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void timeoutTest() {
        TestHelper.println("STARTING timeoutTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
        CountDownLatch lock = new CountDownLatch(1);

        try {
            TestSession session = TestSession.build(drasyl);
            sessions.add(session);

            session.addListener(message -> {
                if (message instanceof RelayException) {
                    RelayException e = (RelayException) message;

                    Assert.assertEquals(
                            "Handshake did not take place successfully in "
                                    + drasyl.getConfig().getRelayMaxHandshakeTimeout().toMillis() + " ms. Connection is closed.",
                            e.getException());
                    lock.countDown();
                }
            });

            lock.await(drasyl.getConfig().getRelayMaxHandshakeTimeout().toMillis() + 2000, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());

            with().pollInSameThread().await().pollDelay(0, TimeUnit.NANOSECONDS).atMost(Durations.TEN_SECONDS)
                    .until(() -> {
                        return session.isTerminated;
                    });
        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED timeoutTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void timeoutNegativeTest() {
        TestHelper.println("STARTING timeoutNegativeTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
        Logger clientLogger = (Logger) LoggerFactory.getLogger(Session.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        clientLogger.addAppender(listAppender);

        try {
            TestSession session = TestSession.build(drasyl, Set.of());
            sessions.add(session);

            session.addListener(message -> {
                assertThat(message, is(not(instanceOf(RelayException.class))));
            });

            // Wait until timeout
            Thread.sleep(drasyl.getConfig().getRelayMaxHandshakeTimeout().toMillis() + 2000);// NOSONAR
            with().pollInSameThread().await().pollDelay(0, TimeUnit.NANOSECONDS).atMost(Durations.TEN_SECONDS)
                    .until(() -> !session.isTerminated());

            for (ILoggingEvent event : ImmutableList.copyOf(listAppender.list)) {
                if (event.getLevel().equals(Level.INFO)) {
                    assertNotEquals(event.getMessage(), "{} Handshake did not take place successfully in {} ms. " + "Connection is closed.");
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED timeoutNegativeTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void invalidMessageTest() {
        TestHelper.println("STARTING invalidMessageTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
        CountDownLatch lock = new CountDownLatch(2);

        try {
            TestSession session = TestSession.build(drasyl);
            sessions.add(session);

            session.addListener(message -> {
                if (message instanceof RelayException) {
                    RelayException e = (RelayException) message;

                    Assert.assertThat(e.getException(), anyOf(
                            equalTo("java.lang.IllegalArgumentException: Your request was not a valid Message Object: 'invalid message'"),
                            equalTo("Exception occurred during initialization stage. The connection will shut down.")
                    ));

                    lock.countDown();
                }
            });

            session.sendRawString("invalid message");

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());

        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED invalidMessageTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void multipleHandshakeWithSameIDTest() {
        TestHelper.println("STARTING multipleHandshakeWithSameIDTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(1);
        Set<SessionChannel> sessionChannels = Set.of(SessionChannel.of("testChannel"));

        try {
            TestSession session1 = TestSession.build(drasyl, sessionChannels);
            sessions.add(session1);
            TestSession session2 = TestSession.build(drasyl, session1.getUID());
            sessions.add(session2);

            session2.sendMessageWithResponse(new Join(session2.getUID(), sessionChannels), RelayException.class).thenAccept(response -> {
                assertEquals("This client has already an open "
                        + "session with this relay server. Can't open more sockets.", response.getException());
                lock.countDown();
            });

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED multipleHandshakeWithSameIDTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void multipleHandshakeWithObjectTest() {
        TestHelper.println("STARTING multipleHandshakeWithObjectTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(1);
        Set<SessionChannel> sessionChannels = Set.of(SessionChannel.of("testChannel"));

        try {
            TestSession session = TestSession.build(drasyl, sessionChannels);
            sessions.add(session);

            session.sendMessageWithResponse(new Join(session.getUID(), sessionChannels), RelayException.class).thenAccept(response -> {
                assertEquals("This client has already an open "
                        + "session with this relay server. No need to authenticate twice.", response.getException());
                lock.countDown();
            });

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED multipleHandshakeWithObjectTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void idleTimoutTest() {
        TestHelper.println("STARTING idleTimoutTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(2);
        try {
            TestSession session = TestSession.build(drasyl, false);
            sessions.add(session);

            session.addListener(message -> {
                if (message instanceof RelayException) {
                    RelayException m = (RelayException) message;
                    assertThat(m.getException(),
                            is(equalTo("Max retries for ping/pong requests reached. Connection will be closed.")));
                    lock.countDown();
                }

                if (message instanceof Ping) {
                    lock.countDown();
                }
            });

            // Wait until timeout
            Thread.sleep(drasyl.getConfig().getIdleTimeout().toMillis() * (drasyl.getConfig().getIdleRetries() + 1) + 1000);// NOSONAR

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED idleTimoutTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void idleTimoutNegativeTest() {
        TestHelper.println("STARTING idleTimoutNegativeTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        try {
            TestSession session = TestSession.build(drasyl, Set.of());
            sessions.add(session);

            session.addListener(message -> {
                assertThat(message, is(not(instanceOf(RelayException.class))));
            });

            // Wait until timeout
            Thread.sleep(drasyl.getConfig().getIdleTimeout().toMillis() * (drasyl.getConfig().getIdleRetries() + 1) + 1000);// NOSONAR
        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED idleTimoutNegativeTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void serverPingResponseTest() {
        TestHelper.println("STARTING serverPingResponseTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        CountDownLatch lock = new CountDownLatch(1);
        try {
            TestSession session = TestSession.build(drasyl, false);
            sessions.add(session);

            session.addListener(message -> {
                if (message instanceof Pong)
                    lock.countDown();
            });

            session.sendMessage(new Ping());

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());
        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED serverPingResponseTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void messageDuplicateFilterTest() {
        TestHelper.println("STARTING messageDuplicateFilterTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
        CountDownLatch lock = new CountDownLatch(1);

        try {
            TestSession session = TestSession.build(drasyl, Set.of());
            sessions.add(session);

            RelayException msg = new RelayException("Test");

            session.sendMessage(msg);
            session.sendMessageWithResponse(msg, RelayException.class).thenAccept(response -> {
                Assert.assertThat(response.getException(), anyOf(
                        equalTo("This message was already send.")));

                lock.countDown();
            });

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());

        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED messageDuplicateFilterTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void responseFilterTest() {
        TestHelper.println("STARTING responseFilterTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
        CountDownLatch lock = new CountDownLatch(1);

        try {
            TestSession session = TestSession.build(drasyl, Set.of());
            sessions.add(session);

            Response<RelayException> msg = new Response<>(new RelayException("Test"), RandomUtil.randomString(12));

            session.sendMessageWithResponse(msg, RelayException.class).thenAccept(response -> {
                Assert.assertThat(response.getException(), anyOf(
                        equalTo("This response was not expected from us, as it does not refer to any valid request.")));

                lock.countDown();
            });

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());

        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED responseFilterTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    public void sendBeforeAuthTest() {
        TestHelper.println("STARTING sendBeforeAuthTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
        CountDownLatch lock = new CountDownLatch(1);

        try {
            TestSession session = TestSession.build(drasyl);
            sessions.add(session);

            Response<RelayException> msg = new Response<>(new RelayException("Test"), RandomUtil.randomString(12));

            session.sendMessageWithResponse(msg, Status.class).thenAccept(response -> {
                Assert.assertThat(response, anyOf(
                        equalTo(Status.FORBIDDEN)));

                lock.countDown();
            });

            lock.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertEquals(0, lock.getCount());

        } catch (InterruptedException | ExecutionException e) {
            fail("Exception occurred during the test.");
        }

        TestHelper.println("FINISHED sendBeforeAuthTest()", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }
}
