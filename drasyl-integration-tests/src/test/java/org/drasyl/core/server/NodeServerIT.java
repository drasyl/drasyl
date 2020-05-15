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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.core.common.message.*;
import org.drasyl.core.models.CompressedKeyPair;
import org.drasyl.core.models.CompressedPrivateKey;
import org.drasyl.core.models.CompressedPublicKey;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.ConnectionsManager;
import org.drasyl.core.node.DrasylNodeConfig;
import org.drasyl.core.node.Messenger;
import org.drasyl.core.node.PeersManager;
import org.drasyl.core.node.connections.PeerConnection;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityManager;
import org.drasyl.core.server.testutils.ANSI_COLOR;
import org.drasyl.core.server.testutils.TestHelper;
import org.drasyl.core.server.testutils.TestServerConnection;
import org.drasyl.crypto.CryptoException;
import org.junit.Ignore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.awaitility.Awaitility.with;
import static org.awaitility.Durations.FIVE_MINUTES;
import static org.drasyl.core.common.message.StatusMessage.Code.STATUS_FORBIDDEN;
import static org.drasyl.core.node.connections.PeerConnection.CloseReason.REASON_SHUTTING_DOWN;
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
    public static final long TIMEOUT = 10000L;
    private static EventLoopGroup workerGroup;
    private static EventLoopGroup bossGroup;
    DrasylNodeConfig config;
    private IdentityManager identityManager;
    private NodeServer server;
    private Messenger messenger;

    @BeforeEach
    public void setup(TestInfo info) throws DrasylException, CryptoException {
        TestHelper.println("STARTING " + info.getDisplayName(), ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        identityManager = mock(IdentityManager.class);
        PeersManager peersManager = new PeersManager();
        ConnectionsManager connectionsManager = new ConnectionsManager();
        messenger = new Messenger(connectionsManager);

        config = new DrasylNodeConfig(
                ConfigFactory.load("configs/NodeServerIT.conf"));

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
    public void cleanUp(TestInfo info) {
        server.close();

        TestHelper.println("FINISHED " + info.getDisplayName(), ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void joinMessageShouldBeRespondedWithWelcomeMessage() throws ExecutionException, InterruptedException {
        // create connection
        TestServerConnection session = TestServerConnection.build(server);

        // send message
        RequestMessage<?> request = new JoinMessage(identityManager.getKeyPair().getPublicKey(), Set.of());
        Single<WelcomeMessage> send = session.send(request, WelcomeMessage.class);

        // verify response
        WelcomeMessage response = send.blockingGet();

        assertThat(response, instanceOf(WelcomeMessage.class));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void multipleJoinMessagesShouldBeRespondedWithWelcomeMessage() throws ExecutionException, InterruptedException, CryptoException {
        // create connections
        TestServerConnection session1 = TestServerConnection.build(server);
        TestServerConnection session2 = TestServerConnection.build(server);

        // send messages
        RequestMessage<?> request1 = new JoinMessage(CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3"), Set.of());
        Single<WelcomeMessage> send1 = session1.send(request1, WelcomeMessage.class);

        RequestMessage<?> request2 = new JoinMessage(CompressedPublicKey.of("0340a4f2adbddeedc8f9ace30e3f18713a3405f43f4871b4bac9624fe80d2056a7"), Set.of());
        Single<WelcomeMessage> send2 = session2.send(request2, WelcomeMessage.class);

        // verify responses
        WelcomeMessage response1 = send1.blockingGet();
        WelcomeMessage response2 = send2.blockingGet();

        assertThat(response1, instanceOf(WelcomeMessage.class));
        assertThat(response2, instanceOf(WelcomeMessage.class));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void requestStocktakingMessageShouldBeRespondedWithStocktakingMessage() throws ExecutionException, InterruptedException, CryptoException {
        // create connections
        TestServerConnection session1 = TestServerConnection.buildAutoJoin(server);
        TestServerConnection session2 = TestServerConnection.buildAutoJoin(server);

        with().pollInSameThread().await().pollDelay(0, NANOSECONDS).atMost(FIVE_MINUTES)
                .until(() -> server.getPeersManager().getChildren().size() >= 2);

        // send message
        RequestMessage<?> request = new RequestClientsStocktakingMessage();
        Single<ClientsStocktakingMessage> send = session1.send(request, ClientsStocktakingMessage.class);

        // verify response
        ClientsStocktakingMessage response = send.blockingGet();

        assertThat(response, instanceOf(ClientsStocktakingMessage.class));
        assertThat(response.getIdentities(), containsInAnyOrder(session1.getIdentity(), session2.getIdentity()));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void applicationMessageShouldBeForwardedToRecipient() throws ExecutionException, InterruptedException, CryptoException {
        // create connections
        TestServerConnection session1 = TestServerConnection.buildAutoJoin(server);
        TestServerConnection session2 = TestServerConnection.buildAutoJoin(server);

        Subject<Message<?>> receiver2 = session2.receivedMessages();

        // send message
        RequestMessage<?> request = new ApplicationMessage(session1.getIdentity(), session2.getIdentity(), new byte[]{
                0x00,
                0x01,
                0x02
        });
        Single<StatusMessage> send1 = session1.send(request, StatusMessage.class);

        // verify responses
        StatusMessage response1 = send1.blockingGet();
        Message<?> received2 = receiver2.firstElement().blockingGet();

        assertEquals(new StatusMessage(StatusMessage.Code.STATUS_OK, request.getId()), response1);
        assertEquals(request, received2);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void notJoiningClientsShouldBeDroppedAfterTimeout() throws ExecutionException, InterruptedException, CryptoException {
        // create connection
        TestServerConnection session = TestServerConnection.build(server);

        Subject<Message<?>> receiver = session.receivedMessages();

        // wait for timeout
        with().pollInSameThread().await().pollDelay(0, NANOSECONDS).atMost(FIVE_MINUTES)
                .until(() -> session.isClosed().isDone());

        // verify response
        Message<?> received = receiver.firstElement().blockingGet();

        assertThat(received, instanceOf(ConnectionExceptionMessage.class));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void joinedClientsShouldNoBeDroppedAfterTimeout() throws InterruptedException, CryptoException, ExecutionException {
        // create connection
        TestServerConnection session = TestServerConnection.buildAutoJoin(server);

        ReplaySubject<Message<?>> receiver = session.receivedMessages();

        // wait until timeout
        Thread.sleep(server.getConfig().getServerHandshakeTimeout().plusSeconds(2).toMillis());// NOSONAR

        // verify session status
        assertFalse(session.isClosed().isDone());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void invalidMessageShouldBeRespondedWithExceptionMessage() throws ExecutionException, InterruptedException {
        // create connection
        TestServerConnection session = TestServerConnection.build(server);

        Subject<Message<?>> receiver = session.receivedMessages();

        // send message
        session.sendRawString("invalid message");

        // verify response
        List<Message<?>> received = receiver.take(2).toList().blockingGet();

        assertThat(received, contains(instanceOf(MessageExceptionMessage.class), instanceOf(ConnectionExceptionMessage.class)));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void newSessionWithSameIdentityShouldReplaceAndCloseExistingSession() throws ExecutionException, InterruptedException, CryptoException {
        // create connections
        TestServerConnection session1 = TestServerConnection.build(server);
        TestServerConnection session2 = TestServerConnection.build(server);

        Subject<Message<?>> receiver1 = session1.receivedMessages();

        Subject<Message<?>> receiver2 = session2.receivedMessages();

        // send messages
        CompressedPublicKey publicKey = CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3");
        RequestMessage<?> request1 = new JoinMessage(publicKey, Set.of());
        session1.send(request1, WelcomeMessage.class).blockingGet();

        RequestMessage<?> request2 = new JoinMessage(publicKey, Set.of());
        session2.send(request2, WelcomeMessage.class).blockingGet();

        // verify responses
        List<Message<?>> received1 = receiver1.take(2).toList().blockingGet();
        @NonNull List<Message<?>> received2 = receiver2.take(1).toList().blockingGet();

        assertThat(received1, contains(instanceOf(WelcomeMessage.class), equalTo(new LeaveMessage(PeerConnection.CloseReason.REASON_NEW_SESSION))));
        assertThat(received2, contains(instanceOf(WelcomeMessage.class)));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void multipleJoinMessagesWithinSameSessionShouldRespondedWithExceptionMessage() throws ExecutionException, InterruptedException, CryptoException {
        // create connection
        TestServerConnection session = TestServerConnection.buildAutoJoin(server);

        // send message
        RequestMessage<?> request = new JoinMessage(CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3"), Set.of());
        Single<MessageExceptionMessage> send = session.send(request, MessageExceptionMessage.class);

        // verify response
        Message<?> response = send.blockingGet();

        assertThat(response, instanceOf(MessageExceptionMessage.class));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void clientsNotSendingPongMessageShouldBeDroppedAfterTimeout() throws ExecutionException, InterruptedException {
        TestServerConnection session = TestServerConnection.build(server, false);

        Subject<Message<?>> receiver = session.receivedMessages();

        // wait until timeout
        Thread.sleep(server.getConfig().getServerIdleTimeout().toMillis() * (server.getConfig().getServerIdleRetries() + 1) + 1000);// NOSONAR

        // verify responses
        List<Message<?>> received = receiver.take(3).toList().blockingGet();

        assertThat(received, contains(instanceOf(PingMessage.class), instanceOf(PingMessage.class), instanceOf(ConnectionExceptionMessage.class)));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void clientsSendingPongMessageShouldNotBeDroppedAfterTimeout() throws ExecutionException, InterruptedException, CryptoException {
        TestServerConnection session = TestServerConnection.buildAutoJoin(server);

        // wait until timeout
        Thread.sleep(server.getConfig().getServerIdleTimeout().toMillis() * (server.getConfig().getServerIdleRetries() + 1) + 1000);// NOSONAR

        // verify session status
        assertFalse(session.isClosed().isDone());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void pingMessageShouldBeRespondedWithPongMessage() throws ExecutionException, InterruptedException {
        TestServerConnection session = TestServerConnection.build(server, false);

        // send messages
        RequestMessage<?> request = new PingMessage();
        Single<PongMessage> send = session.send(request, PongMessage.class);

        // verify responses
        Message<?> response = send.blockingGet();

        assertEquals(new PongMessage(request.getId()), response);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void nonAuthorizedClientSendingNonJoinMessageShouldBeRespondedWithStatusForbiddenMessage() throws ExecutionException, InterruptedException {
        TestServerConnection session = TestServerConnection.build(server);

        // send messages
        RequestMessage<?> request = new ApplicationMessage(TestHelper.random(), TestHelper.random(), new byte[]{
                0x00,
                0x01
        });
        Single<StatusMessage> send = session.send(request, StatusMessage.class);

        // verify responses
        Message<?> response = send.blockingGet();

        assertEquals(new StatusMessage(STATUS_FORBIDDEN, request.getId()), response);
    }

    @Ignore
    public void messageWithMaxSizeShouldArrive() {
        CountDownLatch lock = new CountDownLatch(2);

        try {
            TestServerConnection session1 = TestServerConnection.buildAutoJoin(server);
            TestServerConnection session2 = TestServerConnection.buildAutoJoin(server);

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
    }

    @Ignore
    public void maxSizeExceededTest() {
        CountDownLatch lock = new CountDownLatch(2);

        try {
            TestServerConnection session1 = TestServerConnection.buildAutoJoin(server);
            TestServerConnection session2 = TestServerConnection.buildAutoJoin(server);

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
    }

    @Test
    public void shouldOpenAndCloseGracefully() throws DrasylException {
        NodeServer server = new NodeServer(identityManager, messenger, mock(PeersManager.class), workerGroup, bossGroup);

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
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void shuttingDownServerShouldSendLeaveMessage() throws ExecutionException, InterruptedException, CryptoException {
        TestServerConnection session = TestServerConnection.buildAutoJoin(server);

        Subject<Message<?>> receiver = session.receivedMessages();

        server.close();

        // verify responses
        Message<?> received = receiver.firstElement().blockingGet();

        assertEquals(new LeaveMessage(REASON_SHUTTING_DOWN), received);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void shuttingDownServerShouldRejectNewConnections() throws ExecutionException, InterruptedException, CryptoException {
        TestServerConnection session = TestServerConnection.build(server);

        server.close();

        // send message
        RequestMessage<?> request = new JoinMessage(CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3"), Set.of());
        Single<RejectMessage> send = session.send(request, RejectMessage.class);

        // verify response
        Message<?> received = send.blockingGet();

        assertEquals(new RejectMessage(request.getId()), received);
    }

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
}
