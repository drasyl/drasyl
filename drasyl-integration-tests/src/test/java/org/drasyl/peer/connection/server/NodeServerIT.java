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
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityManagerException;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.PingMessage;
import org.drasyl.peer.connection.message.PongMessage;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import testutils.AnsiColor;
import testutils.TestHelper;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.with;
import static org.awaitility.Durations.FIVE_MINUTES;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_HANDSHAKE_TIMEOUT;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_INITIALIZATION;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_PING_PONG;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_NEW_SESSION;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_FORBIDDEN;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_SERVICE_UNAVAILABLE;
import static org.drasyl.peer.connection.server.TestNodeServerConnection.clientSession;
import static org.drasyl.peer.connection.server.TestNodeServerConnection.clientSessionAfterJoin;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static testutils.TestHelper.colorizedPrintln;

//import net.jcip.annotations.NotThreadSafe;

//@NotThreadSafe
@Execution(ExecutionMode.SAME_THREAD)
class NodeServerIT {
    public static final long TIMEOUT = 10000L;
    private static EventLoopGroup workerGroup;
    private static EventLoopGroup bossGroup;
    DrasylNodeConfig config;
    private IdentityManager identityManager;
    private NodeServer server;
    private Messenger messenger;
    private PeersManager peersManager;

    @BeforeEach
    void setup(TestInfo info) throws DrasylException {
        colorizedPrintln("STARTING " + info.getDisplayName(), AnsiColor.COLOR_CYAN, AnsiColor.STYLE_REVERSED);

        System.setProperty("io.netty.tryReflectionSetAccessible", "true");

        config = new DrasylNodeConfig(ConfigFactory.load("configs/NodeServerIT.conf"));
        identityManager = new IdentityManager(config);
        identityManager.loadOrCreateIdentity();
        peersManager = new PeersManager(event -> {
        });
        messenger = new Messenger();

        server = new NodeServer(identityManager, messenger, peersManager, config, workerGroup, bossGroup);
        server.open();
    }

    @AfterEach
    void cleanUp(TestInfo info) throws IdentityManagerException {
        server.close();

        IdentityManager.deleteIdentityFile(config.getIdentityPath());

        colorizedPrintln("FINISHED " + info.getDisplayName(), AnsiColor.COLOR_CYAN, AnsiColor.STYLE_REVERSED);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void joinMessageShouldBeRespondedWithWelcomeMessage() throws ExecutionException, InterruptedException, CryptoException {
        // create connection
        TestNodeServerConnection session = clientSession(server);

        // send message
        RequestMessage request = new JoinMessage(session.getPublicKey(), Set.of(), Map.of());
        CompletableFuture<ResponseMessage<?>> send = session.sendRequest(request);

        // verify response
        ResponseMessage<?> response = send.get();

        assertThat(response, instanceOf(WelcomeMessage.class));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void multipleJoinMessagesShouldBeRespondedWithWelcomeMessage() throws ExecutionException, InterruptedException, CryptoException {
        // create connections
        TestNodeServerConnection session1 = clientSession(server);
        TestNodeServerConnection session2 = clientSession(server);

        // send messages
        RequestMessage request1 = new JoinMessage(CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3"), Set.of(), Map.of());
        CompletableFuture<ResponseMessage<?>> send1 = session1.sendRequest(request1);

        RequestMessage request2 = new JoinMessage(CompressedPublicKey.of("0340a4f2adbddeedc8f9ace30e3f18713a3405f43f4871b4bac9624fe80d2056a7"), Set.of(), Map.of());
        CompletableFuture<ResponseMessage<?>> send2 = session2.sendRequest(request2);

        // verify responses
        ResponseMessage<?> response1 = send1.get();
        ResponseMessage<?> response2 = send2.get();

        assertThat(response1, instanceOf(WelcomeMessage.class));
        assertThat(response2, instanceOf(WelcomeMessage.class));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void applicationMessageShouldBeForwardedToRecipient() throws ExecutionException, InterruptedException, CryptoException {
        // create connections
        TestNodeServerConnection session1 = clientSessionAfterJoin(server);
        TestNodeServerConnection session2 = clientSessionAfterJoin(server);

        TestObserver<Message> receivedMessages2 = session2.receivedMessages().test();

        // send message
        RequestMessage request = new ApplicationMessage(session1.getAddress(), session2.getAddress(), new byte[]{
                0x00,
                0x01,
                0x02
        });
        CompletableFuture<ResponseMessage<?>> send1 = session1.sendRequest(request);

        // verify responses
        ResponseMessage<?> response1 = send1.get();

        assertEquals(new StatusMessage(STATUS_OK, request.getId()), response1);
        receivedMessages2.awaitCount(1);
        receivedMessages2.assertValue(request);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void notJoiningClientsShouldBeDroppedAfterTimeout() throws ExecutionException, InterruptedException, CryptoException {
        // create connection
        TestNodeServerConnection session = clientSession(server);

        TestObserver<Message> receivedMessages = session.receivedMessages().test();

        // wait for timeout
        with().pollInSameThread().await().pollDelay(0, NANOSECONDS).atMost(FIVE_MINUTES)
                .until(() -> session.isClosed().isDone());

        // verify response
        receivedMessages.awaitCount(1);
        receivedMessages.assertValue(new ConnectionExceptionMessage(CONNECTION_ERROR_HANDSHAKE_TIMEOUT));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void joinedClientsShouldNoBeDroppedAfterTimeout() throws InterruptedException, CryptoException, ExecutionException {
        // create connection
        TestNodeServerConnection session = clientSessionAfterJoin(server);

        // wait until timeout
        Thread.sleep(server.getConfig().getServerHandshakeTimeout().plusSeconds(2).toMillis());// NOSONAR

        // verify session status
        assertFalse(session.isClosed().isDone());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void invalidMessageShouldBeRespondedWithExceptionMessage() throws ExecutionException, InterruptedException, CryptoException {
        // create connection
        TestNodeServerConnection session = clientSession(server);

        TestObserver<Message> receivedMessages = session.receivedMessages().test();

        // send message
        session.sendRawString("invalid message");

        // verify response
        receivedMessages.awaitCount(1);
        receivedMessages.assertValue(new ConnectionExceptionMessage(CONNECTION_ERROR_INITIALIZATION));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void newSessionWithSameIdentityShouldReplaceAndCloseExistingSession() throws ExecutionException, InterruptedException, CryptoException {
        // create connections
        TestNodeServerConnection session1 = clientSession(server);
        TestNodeServerConnection session2 = clientSession(server);

        TestObserver<Message> receivedMessages1 = session1.receivedMessages().test();
        TestObserver<Message> receivedMessages2 = session2.receivedMessages().test();

        // send messages
        CompressedPublicKey publicKey = CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3");
        Identity identity = Identity.of(publicKey);
        RequestMessage request1 = new JoinMessage(publicKey, Set.of(), Map.of());
        ResponseMessage<?> response1 = session1.sendRequest(request1).get();
        session1.send(new StatusMessage(STATUS_OK, response1.getId()));
        await().until(() -> server.getChannelGroup().find(identity) != null);

        RequestMessage request2 = new JoinMessage(publicKey, Set.of(), Map.of());
        ResponseMessage<?> response2 = session2.sendRequest(request2).join();
        session2.send(new StatusMessage(STATUS_OK, response2.getId()));

        // verify responses
        receivedMessages1.awaitCount(2);
        receivedMessages1.assertValues(new WelcomeMessage(server.getIdentityManager().getIdentity().getPublicKey(), server.getEntryPoints(), request1.getId()), new QuitMessage(REASON_NEW_SESSION));
        receivedMessages2.awaitCount(1);
        receivedMessages2.assertValue(new WelcomeMessage(server.getIdentityManager().getIdentity().getPublicKey(), server.getEntryPoints(), request2.getId()));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientsNotSendingPongMessageShouldBeDroppedAfterTimeout() throws ExecutionException, InterruptedException, CryptoException {
        // create connection
        TestNodeServerConnection session = clientSession(server, false);

        TestObserver<Message> receivedMessages = session.receivedMessages().test();

        // wait until timeout
        Thread.sleep(server.getConfig().getServerIdleTimeout().toMillis() * (server.getConfig().getServerIdleRetries() + 1) + 1000);// NOSONAR

        // verify responses
        receivedMessages.awaitCount(3);
        receivedMessages.assertValues(new PingMessage(), new PingMessage(), new ConnectionExceptionMessage(CONNECTION_ERROR_PING_PONG));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientsSendingPongMessageShouldNotBeDroppedAfterTimeout() throws ExecutionException, InterruptedException, CryptoException {
        // create connection
        TestNodeServerConnection session = clientSessionAfterJoin(server);

        // wait until timeout
        Thread.sleep(server.getConfig().getServerIdleTimeout().toMillis() * (server.getConfig().getServerIdleRetries() + 1) + 1000);// NOSONAR

        // verify session status
        assertFalse(session.isClosed().isDone());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void pingMessageShouldBeRespondedWithPongMessage() throws ExecutionException, InterruptedException, CryptoException {
        // create connection
        TestNodeServerConnection session = clientSession(server, false);

        // send message
        RequestMessage request = new PingMessage();
        CompletableFuture<ResponseMessage<?>> send = session.sendRequest(request);

        // verify response
        ResponseMessage<?> response = send.get();

        assertEquals(new PongMessage(request.getId()), response);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void nonAuthorizedClientSendingNonJoinMessageShouldBeRespondedWithStatusForbiddenMessage() throws ExecutionException, InterruptedException, CryptoException {
        // create connection
        TestNodeServerConnection session = clientSession(server);

        // send message
        RequestMessage request = new ApplicationMessage(TestHelper.random(), TestHelper.random(), new byte[]{
                0x00,
                0x01
        });
        CompletableFuture<ResponseMessage<?>> send = session.sendRequest(request);

        // verify response
        ResponseMessage<?> response = send.get();

        assertEquals(new StatusMessage(STATUS_FORBIDDEN, request.getId()), response);
    }

    @Disabled("Muss noch implementiert werden")
    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void messageWithMaxSizeShouldArrive() throws InterruptedException, ExecutionException, CryptoException {
        // create connection
        TestNodeServerConnection session1 = clientSessionAfterJoin(server);
        TestNodeServerConnection session2 = clientSessionAfterJoin(server);

        TestObserver<Message> receivedMessages = session2.receivedMessages().test();

        // create message with max allowed payload size
        byte[] bigPayload = new byte[config.getMaxContentLength()];
        new Random().nextBytes(bigPayload);

        // send message
        RequestMessage request = new ApplicationMessage(session1.getAddress(), session2.getAddress(), bigPayload);
        session2.send(request);

        // verify response
        receivedMessages.awaitCount(1);
        receivedMessages.assertValue(request);
    }

    @Disabled("Muss noch implementiert werden")
    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void messageExceedingMaxSizeShouldThrowExceptionOnSend() throws InterruptedException, ExecutionException, CryptoException {
        // create connection
        TestNodeServerConnection session1 = clientSessionAfterJoin(server);
        TestNodeServerConnection session2 = clientSessionAfterJoin(server);

        // create message with exceeded payload size
        byte[] bigPayload = new byte[config.getMaxContentLength() + 1];
        new Random().nextBytes(bigPayload);

        // send message
        RequestMessage request = new ApplicationMessage(session1.getAddress(), session2.getAddress(), bigPayload);

        assertThrows(DrasylException.class, () -> session2.send(request));
    }

    @Test
    void shouldOpenAndCloseGracefully() throws DrasylException {
        NodeServer server = new NodeServer(identityManager, messenger, peersManager, workerGroup, bossGroup);

        server.open();
        server.close();

        assertTrue(true);
    }

    @Test
    void openShouldFailIfInvalidPortIsGiven() throws DrasylException {
        Config config =
                ConfigFactory.parseString("drasyl.server.bind-port = 72522").withFallback(ConfigFactory.load());
        NodeServer server = new NodeServer(identityManager, messenger, peersManager, config, workerGroup, bossGroup);

        assertThrows(NodeServerException.class, server::open);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void shuttingDownServerShouldSendLeaveMessage() throws ExecutionException, InterruptedException, CryptoException {
        TestNodeServerConnection session = clientSessionAfterJoin(server);

        TestObserver<Message> receivedMessages = session.receivedMessages().test();

        server.close();

        // verify responses
        receivedMessages.awaitCount(1);
        receivedMessages.assertValue(new QuitMessage(REASON_SHUTTING_DOWN));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void shuttingDownServerShouldRejectNewConnections() throws ExecutionException, InterruptedException, CryptoException {
        TestNodeServerConnection session = clientSession(server);

        server.close();

        // send message
        RequestMessage request = new JoinMessage(CompressedPublicKey.of("023e0a51f1830f5ec7decdb428a63992fadd682513e82dc9594e259edd9398edf3"), Set.of(), Map.of());
        CompletableFuture<ResponseMessage<?>> send = session.sendRequest(request);

        // verify response
        ResponseMessage<?> received = send.get();

        assertEquals(new StatusMessage(STATUS_SERVICE_UNAVAILABLE, request.getId()), received);
    }

    @BeforeAll
    static void beforeAll() {
        workerGroup = new NioEventLoopGroup();
        bossGroup = new NioEventLoopGroup(1);
    }

    @AfterAll
    static void afterAll() {
        workerGroup.shutdownGracefully().syncUninterruptibly();
        bossGroup.shutdownGracefully().syncUninterruptibly();
    }
}
