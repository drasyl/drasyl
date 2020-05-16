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
package org.drasyl.peer.connection.server;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.reactivex.rxjava3.core.Single;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityManagerException;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerConnection;
import org.drasyl.peer.connection.message.*;
import org.junit.Ignore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import testutils.AnsiColor;
import testutils.TestHelper;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.awaitility.Awaitility.with;
import static org.awaitility.Durations.FIVE_MINUTES;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_FORBIDDEN;
import static org.drasyl.peer.connection.server.TestNodeServerClientConnection.clientSession;
import static org.drasyl.peer.connection.server.TestNodeServerClientConnection.clientSessionAfterJoin;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static testutils.TestHelper.colorizedPrintln;

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
    private PeersManager peersManager;

    @BeforeEach
    public void setup(TestInfo info) throws DrasylException, CryptoException {
        colorizedPrintln("STARTING " + info.getDisplayName(), AnsiColor.COLOR_CYAN, AnsiColor.STYLE_REVERSED);

        System.setProperty("io.netty.tryReflectionSetAccessible", "true");

        config = new DrasylNodeConfig(ConfigFactory.load("configs/NodeServerIT.conf"));
        identityManager = new IdentityManager(config);
        identityManager.loadOrCreateIdentity();
        peersManager = new PeersManager();
        messenger = new Messenger();

        server = new NodeServer(identityManager, messenger, peersManager, config, workerGroup, bossGroup);
        server.open();
    }

    @AfterEach
    public void cleanUp(TestInfo info) throws IdentityManagerException {
        server.close();

        IdentityManager.deleteIdentityFile(config.getIdentityPath());

        colorizedPrintln("FINISHED " + info.getDisplayName(), AnsiColor.COLOR_CYAN, AnsiColor.STYLE_REVERSED);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void joinMessageShouldBeRespondedWithWelcomeMessage() throws ExecutionException, InterruptedException {
        // create connection
        TestNodeServerClientConnection session = clientSession(server);

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
        TestNodeServerClientConnection session1 = clientSession(server);
        TestNodeServerClientConnection session2 = clientSession(server);

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
        TestNodeServerClientConnection session1 = clientSessionAfterJoin(server);
        TestNodeServerClientConnection session2 = clientSessionAfterJoin(server);

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
        TestNodeServerClientConnection session1 = clientSessionAfterJoin(server);
        TestNodeServerClientConnection session2 = clientSessionAfterJoin(server);

        CompletableFuture<Message<?>> receivedMessage2 = session2.receivedMessages().firstElement().toCompletionStage().toCompletableFuture();

        // send message
        RequestMessage<?> request = new ApplicationMessage(session1.getIdentity(), session2.getIdentity(), new byte[]{
                0x00,
                0x01,
                0x02
        });
        Single<StatusMessage> send1 = session1.send(request, StatusMessage.class);

        // verify responses
        StatusMessage response1 = send1.blockingGet();

        assertEquals(new StatusMessage(StatusMessage.Code.STATUS_OK, request.getId()), response1);
        assertEquals(request, receivedMessage2.get());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void notJoiningClientsShouldBeDroppedAfterTimeout() throws ExecutionException, InterruptedException {
        // create connection
        TestNodeServerClientConnection session = clientSession(server);

        CompletableFuture<Message<?>> receivedMessage = session.receivedMessages().firstElement().toCompletionStage().toCompletableFuture();

        // wait for timeout
        with().pollInSameThread().await().pollDelay(0, NANOSECONDS).atMost(FIVE_MINUTES)
                .until(() -> session.isClosed().isDone());

        // verify response
        assertThat(receivedMessage.get(), instanceOf(ConnectionExceptionMessage.class));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void joinedClientsShouldNoBeDroppedAfterTimeout() throws InterruptedException, CryptoException, ExecutionException {
        // create connection
        TestNodeServerClientConnection session = clientSessionAfterJoin(server);

        // wait until timeout
        Thread.sleep(server.getConfig().getServerHandshakeTimeout().plusSeconds(2).toMillis());// NOSONAR

        // verify session status
        assertFalse(session.isClosed().isDone());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void invalidMessageShouldBeRespondedWithExceptionMessage() throws ExecutionException, InterruptedException {
        // create connection
        TestNodeServerClientConnection session = clientSession(server);

        CompletableFuture<List<Message<?>>> receivedMessages = session.receivedMessages().take(2).toList().toCompletionStage().toCompletableFuture();

        // send message
        session.sendRawString("invalid message");

        // verify response
        assertThat(receivedMessages.get(), contains(instanceOf(MessageExceptionMessage.class), instanceOf(ConnectionExceptionMessage.class)));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void newSessionWithSameIdentityShouldReplaceAndCloseExistingSession() throws ExecutionException, InterruptedException, CryptoException {
        // create connections
        TestNodeServerClientConnection session1 = clientSession(server);
        TestNodeServerClientConnection session2 = clientSession(server);

        CompletableFuture<List<Message<?>>> receivedMessages1 = session1.receivedMessages().take(2).toList().toCompletionStage().toCompletableFuture();
        CompletableFuture<Message<?>> receivedMessage2 = session2.receivedMessages().firstElement().toCompletionStage().toCompletableFuture();

        // send messages
        CompressedPublicKey publicKey = CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3");
        RequestMessage<?> request1 = new JoinMessage(publicKey, Set.of());
        session1.send(request1, WelcomeMessage.class).blockingGet();

        RequestMessage<?> request2 = new JoinMessage(publicKey, Set.of());
        session2.send(request2, WelcomeMessage.class).blockingGet();

        // verify responses
        assertThat(receivedMessages1.get(), contains(instanceOf(WelcomeMessage.class), equalTo(new QuitMessage(PeerConnection.CloseReason.REASON_NEW_SESSION))));
        assertThat(receivedMessage2.get(), instanceOf(WelcomeMessage.class));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void multipleJoinMessagesWithinSameSessionShouldRespondedWithExceptionMessage() throws ExecutionException, InterruptedException, CryptoException {
        // create connection
        TestNodeServerClientConnection session = clientSessionAfterJoin(server);

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
        // create connection
        TestNodeServerClientConnection session = clientSession(server, false);

        CompletableFuture<List<Message<?>>> receivedMessages = session.receivedMessages().take(3).toList().toCompletionStage().toCompletableFuture();

        // wait until timeout
        Thread.sleep(server.getConfig().getServerIdleTimeout().toMillis() * (server.getConfig().getServerIdleRetries() + 1) + 1000);// NOSONAR

        // verify responses
        assertThat(receivedMessages.get(), contains(instanceOf(PingMessage.class), instanceOf(PingMessage.class), instanceOf(ConnectionExceptionMessage.class)));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void clientsSendingPongMessageShouldNotBeDroppedAfterTimeout() throws ExecutionException, InterruptedException, CryptoException {
        // create connection
        TestNodeServerClientConnection session = clientSessionAfterJoin(server);

        // wait until timeout
        Thread.sleep(server.getConfig().getServerIdleTimeout().toMillis() * (server.getConfig().getServerIdleRetries() + 1) + 1000);// NOSONAR

        // verify session status
        assertFalse(session.isClosed().isDone());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void pingMessageShouldBeRespondedWithPongMessage() throws ExecutionException, InterruptedException {
        // create connection
        TestNodeServerClientConnection session = clientSession(server, false);

        // send message
        RequestMessage<?> request = new PingMessage();
        Single<PongMessage> send = session.send(request, PongMessage.class);

        // verify response
        Message<?> response = send.blockingGet();

        assertEquals(new PongMessage(request.getId()), response);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void nonAuthorizedClientSendingNonJoinMessageShouldBeRespondedWithStatusForbiddenMessage() throws ExecutionException, InterruptedException {
        // create connection
        TestNodeServerClientConnection session = clientSession(server);

        // send message
        RequestMessage<?> request = new ApplicationMessage(TestHelper.random(), TestHelper.random(), new byte[]{
                0x00,
                0x01
        });
        Single<StatusMessage> send = session.send(request, StatusMessage.class);

        // verify response
        Message<?> response = send.blockingGet();

        assertEquals(new StatusMessage(STATUS_FORBIDDEN, request.getId()), response);
    }

    @Ignore
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void messageWithMaxSizeShouldArrive() throws InterruptedException, ExecutionException, CryptoException {
        // create connection
        TestNodeServerClientConnection session1 = clientSessionAfterJoin(server);
        TestNodeServerClientConnection session2 = clientSessionAfterJoin(server);

        CompletableFuture<Message<?>> receivedMessage = session2.receivedMessages().firstElement().toCompletionStage().toCompletableFuture();

        // create message with max allowed payload size
        byte[] bigPayload = new byte[config.getMaxContentLength()];
        new Random().nextBytes(bigPayload);

        // send message
        RequestMessage<?> request = new ApplicationMessage(session1.getIdentity(), session2.getIdentity(), bigPayload);
        session2.send(request);

        // verify response
        assertEquals(receivedMessage.get(), request);
    }

    @Ignore
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void messageExceedingMaxSizeShouldThrowExceptionOnSend() throws InterruptedException, ExecutionException, CryptoException {
        // create connection
        TestNodeServerClientConnection session1 = clientSessionAfterJoin(server);
        TestNodeServerClientConnection session2 = clientSessionAfterJoin(server);

        // create message with exceeded payload size
        byte[] bigPayload = new byte[config.getMaxContentLength() + 1];
        new Random().nextBytes(bigPayload);

        // send message
        RequestMessage<?> request = new ApplicationMessage(session1.getIdentity(), session2.getIdentity(), bigPayload);

        assertThrows(DrasylException.class, () -> session2.send(request));
    }

    @Test
    public void shouldOpenAndCloseGracefully() throws DrasylException {
        NodeServer server = new NodeServer(identityManager, messenger, peersManager, workerGroup, bossGroup);

        server.open();
        server.close();

        assertTrue(true);
    }

    @Test
    public void openShouldFailIfInvalidPortIsGiven() throws DrasylException {
        Config config =
                ConfigFactory.parseString("drasyl.server.bind-port = 72522").withFallback(ConfigFactory.load());
        NodeServer server = new NodeServer(identityManager, messenger, peersManager, config, workerGroup, bossGroup);

        assertThrows(NodeServerException.class, server::open);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void shuttingDownServerShouldSendLeaveMessage() throws ExecutionException, InterruptedException, CryptoException {
        TestNodeServerClientConnection session = clientSessionAfterJoin(server);

        CompletableFuture<Message<?>> receivedMessage = session.receivedMessages().firstElement().toCompletionStage().toCompletableFuture();

        server.close();

        // verify responses
        assertEquals(new QuitMessage(PeerConnection.CloseReason.REASON_SHUTTING_DOWN), receivedMessage.get());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void shuttingDownServerShouldRejectNewConnections() throws ExecutionException, InterruptedException, CryptoException {
        TestNodeServerClientConnection session = clientSession(server);

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
