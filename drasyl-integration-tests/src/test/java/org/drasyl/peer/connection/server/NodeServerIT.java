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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityManagerException;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.PingMessage;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.message.SignedMessage;
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

import java.security.KeyPair;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
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
    private Observable<Boolean> superPeerConnected;

    @BeforeEach
    void setup(TestInfo info) throws DrasylException {
        colorizedPrintln("STARTING " + info.getDisplayName(), AnsiColor.COLOR_CYAN, AnsiColor.STYLE_REVERSED);

        System.setProperty("io.netty.tryReflectionSetAccessible", "true");

        config = new DrasylNodeConfig(ConfigFactory.load("configs/NodeServerIT.conf"));
        DrasylNode.setLogLevel(config.getLoglevel());
        identityManager = new IdentityManager(config);
        identityManager.loadOrCreateIdentity();
        peersManager = new PeersManager(event -> {
        });
        messenger = new Messenger();
        superPeerConnected = Observable.just(false);

        server = new NodeServer(identityManager, messenger, peersManager, superPeerConnected, config, workerGroup, bossGroup);
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
        RequestMessage request1 = new JoinMessage(session1.getPublicKey(), Set.of(), Map.of());
        CompletableFuture<ResponseMessage<?>> send1 = session1.sendRequest(request1);

        RequestMessage request2 = new JoinMessage(session2.getPublicKey(), Set.of(), Map.of());
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

        byte[] payload = new byte[]{
                0x00,
                0x01,
                0x02
        };

        // send message
        RequestMessage request = new ApplicationMessage(session1.getAddress(), session2.getAddress(), payload);
        session1.send(request);
        receivedMessages2.awaitCount(1);
        receivedMessages2.assertValue(val -> {
            if (!(val instanceof ApplicationMessage)) {
                return false;
            }
            ApplicationMessage msg = (ApplicationMessage) val;

            return Objects.equals(session1.getAddress(), msg.getSender()) && Objects.equals(session2.getAddress(), msg.getRecipient()) && Arrays.equals(payload, msg.getPayload());
        });
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
        receivedMessages.assertValue(val -> {
            if (!(val instanceof ConnectionExceptionMessage)) {
                return false;
            }

            return ((ConnectionExceptionMessage) val).getError() == CONNECTION_ERROR_HANDSHAKE_TIMEOUT;
        });
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
        receivedMessages.assertValue(val -> ((ConnectionExceptionMessage) val).getError() == CONNECTION_ERROR_INITIALIZATION);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void newSessionWithSameIdentityShouldReplaceAndCloseExistingSession() throws ExecutionException, InterruptedException, CryptoException {
        // create connections
        TestNodeServerConnection session1 = clientSession(server);
        TestNodeServerConnection session2 = clientSession(server, session1.getIdentity());

        TestObserver<Message> receivedMessages1 = session1.receivedMessages().test();
        TestObserver<Message> receivedMessages2 = session2.receivedMessages().test();

        // send messages
        RequestMessage request1 = new JoinMessage(session1.getPublicKey(), Set.of(), Map.of());
        ResponseMessage<?> response1 = session1.sendRequest(request1).get();
        session1.send(new StatusMessage(STATUS_OK, response1.getId()));
        await().until(() -> server.getChannelGroup().find(session1.getIdentity().toNonPrivate()) != null);

        RequestMessage request2 = new JoinMessage(session1.getPublicKey(), Set.of(), Map.of());
        ResponseMessage<?> response2 = session2.sendRequest(request2).join();
        session2.send(new StatusMessage(STATUS_OK, response2.getId()));

        // verify responses
        receivedMessages1.awaitCount(2);
        receivedMessages1.assertValueAt(0, val -> {
            if (!(val instanceof WelcomeMessage)) {
                return false;
            }
            WelcomeMessage msg = (WelcomeMessage) val;

            return Objects.equals(server.getIdentityManager().getIdentity().getPublicKey(), msg.getPublicKey()) && Objects.equals(server.getEndpoints(), msg.getEndpoints()) && Objects.equals(msg.getCorrespondingId(), request1.getId());
        });
        receivedMessages1.assertValueAt(1, val -> ((QuitMessage) val).getReason() == REASON_NEW_SESSION);
        receivedMessages2.awaitCount(1);
        receivedMessages2.assertValue(val -> {
            if (!(val instanceof WelcomeMessage)) {
                return false;
            }
            WelcomeMessage msg = (WelcomeMessage) val;

            return Objects.equals(server.getIdentityManager().getIdentity().getPublicKey(), msg.getPublicKey()) && Objects.equals(server.getEndpoints(), msg.getEndpoints()) && Objects.equals(msg.getCorrespondingId(), request2.getId());
        });
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
        receivedMessages.assertValueAt(0, val -> val instanceof PingMessage);
        receivedMessages.assertValueAt(1, val -> val instanceof PingMessage);
        receivedMessages.assertValueAt(2, val -> ((ConnectionExceptionMessage) val).getError() == CONNECTION_ERROR_PING_PONG);
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

        assertEquals(request.getId(), response.getCorrespondingId());
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
        StatusMessage response = (StatusMessage) send.get();

        assertEquals(STATUS_FORBIDDEN, response.getCode());
        assertEquals(request.getId(), response.getCorrespondingId());
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
        byte[] bigPayload = new byte[config.getMessageMaxContentLength()];
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
        byte[] bigPayload = new byte[config.getMessageMaxContentLength() + 1];
        new Random().nextBytes(bigPayload);

        // send message
        RequestMessage request = new ApplicationMessage(session1.getAddress(), session2.getAddress(), bigPayload);

        assertThrows(DrasylException.class, () -> session2.send(request));
    }

    @Test
    void shouldOpenAndCloseGracefully() throws DrasylException {
        NodeServer server = new NodeServer(identityManager, messenger, peersManager, workerGroup, bossGroup, superPeerConnected);

        server.open();
        server.close();

        assertTrue(true);
    }

    @Test
    void openShouldFailIfInvalidPortIsGiven() throws DrasylException {
        Config config =
                ConfigFactory.parseString("drasyl.server.bind-port = 72522").withFallback(ConfigFactory.load());
        NodeServer server = new NodeServer(identityManager, messenger, peersManager, superPeerConnected, config, workerGroup, bossGroup);

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
        receivedMessages.assertValue(val -> ((QuitMessage) val).getReason() == REASON_SHUTTING_DOWN);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void shuttingDownServerShouldRejectNewConnections() throws ExecutionException, InterruptedException, CryptoException {
        TestNodeServerConnection session = clientSession(server);

        server.close();

        // send message
        RequestMessage request = new JoinMessage(session.getPublicKey(), Set.of(), Map.of());
        CompletableFuture<ResponseMessage<?>> send = session.sendRequest(request);

        // verify response
        StatusMessage received = (StatusMessage) send.get();

        assertEquals(STATUS_SERVICE_UNAVAILABLE, received.getCode());
        assertEquals(request.getId(), received.getCorrespondingId());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void MessageWithWrongSignatureShouldProduceExceptionMessage() throws ExecutionException, InterruptedException, CryptoException, JsonProcessingException {

        // create connection
        TestNodeServerConnection session = clientSession(server, false);
        TestObserver<Message> receivedMessages = session.receivedMessages().filter(msg -> msg instanceof StatusMessage).test();

        // send message
        Message request = new PingMessage();
        SignedMessage signedMessage = new SignedMessage(request, session.getPublicKey());
        KeyPair keyPair = Crypto.generateKeys();
        Crypto.sign(keyPair.getPrivate(), signedMessage);
        session.sendRawString(new ObjectMapper().writeValueAsString(signedMessage));

        // verify response
        receivedMessages.awaitCount(1);
        receivedMessages.assertValue(val -> ((StatusMessage) val).getCode() == StatusMessage.Code.STATUS_INVALID_SIGNATURE);
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
