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
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityManagerException;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeerInformation;
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

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.with;
import static org.awaitility.Durations.FIVE_MINUTES;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_HANDSHAKE_TIMEOUT;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_INITIALIZATION;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_PING_PONG;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_PROOF_OF_WORK_INVALID;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_NEW_SESSION;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_FORBIDDEN;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_SERVICE_UNAVAILABLE;
import static org.drasyl.peer.connection.server.TestNodeServerConnection.clientSession;
import static org.drasyl.peer.connection.server.TestNodeServerConnection.clientSessionAfterJoin;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static testutils.AnsiColor.COLOR_CYAN;
import static testutils.AnsiColor.STYLE_REVERSED;
import static testutils.TestHelper.colorizedPrintln;

//import net.jcip.annotations.NotThreadSafe;

//@NotThreadSafe
@Execution(ExecutionMode.SAME_THREAD)
class NodeServerIT {
    public static final long TIMEOUT = 10000L;
    private static EventLoopGroup workerGroup;
    private static EventLoopGroup bossGroup;
    DrasylConfig config;
    private IdentityManager identityManager;
    private NodeServer server;
    private Messenger messenger;
    private PeersManager peersManager;
    private Observable<Boolean> superPeerConnected;
    private Identity identitySession1;
    private Identity identitySession2;

    @BeforeEach
    void setup(TestInfo info) throws DrasylException, CryptoException {
        colorizedPrintln("STARTING " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);

        System.setProperty("io.netty.tryReflectionSetAccessible", "true");

        identitySession1 = Identity.of(169092, "030a59784f88c74dcd64258387f9126739c3aeb7965f36bb501ff01f5036b3d72b", "0f1e188d5e3b98daf2266d7916d2e1179ae6209faa7477a2a66d4bb61dab4399");
        identitySession2 = Identity.of(26778671, "0236fde6a49564a0eaa2a7d6c8f73b97062d5feb36160398c08a5b73f646aa5fe5", "093d1ee70518508cac18eaf90d312f768c14d43de9bfd2618a2794d8df392da0");

        config = DrasylConfig.newBuilder()
                //                .loglevel(Level.TRACE)
                .identityProofOfWork(ProofOfWork.of(6657650))
                .identityPublicKey(CompressedPublicKey.of("023d34f317616c3bb0fa1e4b425e9419d1704ef57f6e53afe9790e00998134f5ff"))
                .identityPrivateKey(CompressedPrivateKey.of("0c27af38c77f2cd5cc2a0ff5c461003a9c24beb955f316135d251ecaf4dda03f"))
                .serverBindHost("127.0.0.1")
                .serverBindPort(0)
                .serverHandshakeTimeout(ofSeconds(5))
                .serverSSLEnabled(true)
                .serverIdleTimeout(ofSeconds(1))
                .serverIdleRetries((short) 1)
                .superPeerEnabled(false)
                .build();
        DrasylNode.setLogLevel(config.getLoglevel());
        identityManager = new IdentityManager(config);
        identityManager.loadOrCreateIdentity();
        peersManager = new PeersManager(event -> {
        });
        messenger = new Messenger();
        superPeerConnected = Observable.just(false);

        server = new NodeServer(identityManager::getIdentity, messenger, peersManager, config, workerGroup, bossGroup, superPeerConnected);
        server.open();
    }

    @AfterEach
    void cleanUp(TestInfo info) throws IdentityManagerException {
        server.close();

        IdentityManager.deleteIdentityFile(config.getIdentityPath());

        colorizedPrintln("FINISHED " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void joinMessageShouldBeRespondedWithWelcomeMessage() throws ExecutionException, InterruptedException {
        // create connection
        TestNodeServerConnection session = clientSession(config, server, identitySession1);

        // send message
        RequestMessage request = new JoinMessage(session.getIdentity().getProofOfWork(), session.getIdentity().getPublicKey(), Set.of());
        CompletableFuture<ResponseMessage<?>> send = session.sendRequest(request);

        // verify response
        ResponseMessage<?> response = send.get();

        assertThat(response, instanceOf(WelcomeMessage.class));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void multipleJoinMessagesShouldBeRespondedWithWelcomeMessage() throws ExecutionException, InterruptedException {
        // create connections
        TestNodeServerConnection session1 = clientSession(config, server, identitySession1);
        TestNodeServerConnection session2 = clientSession(config, server, identitySession2);

        // send messages
        RequestMessage request1 = new JoinMessage(session1.getIdentity().getProofOfWork(), session1.getIdentity().getPublicKey(), Set.of());
        CompletableFuture<ResponseMessage<?>> send1 = session1.sendRequest(request1);

        RequestMessage request2 = new JoinMessage(session2.getIdentity().getProofOfWork(), session2.getIdentity().getPublicKey(), Set.of());
        CompletableFuture<ResponseMessage<?>> send2 = session2.sendRequest(request2);

        // verify responses
        ResponseMessage<?> response1 = send1.get();
        ResponseMessage<?> response2 = send2.get();

        assertThat(response1, instanceOf(WelcomeMessage.class));
        assertThat(response2, instanceOf(WelcomeMessage.class));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void applicationMessageShouldBeForwardedToRecipient() throws ExecutionException, InterruptedException {
        // create connections
        TestNodeServerConnection session1 = clientSessionAfterJoin(config, server, identitySession1);
        TestNodeServerConnection session2 = clientSessionAfterJoin(config, server, identitySession2);

        TestObserver<Message> receivedMessages2 = session2.receivedMessages().test();

        byte[] payload = new byte[]{
                0x00,
                0x01,
                0x02
        };

        // send message
        RequestMessage request = new ApplicationMessage(session1.getPublicKey(), session2.getPublicKey(), payload);
        session1.send(request);
        receivedMessages2.awaitCount(1);
        receivedMessages2.assertValue(val -> {
            if (!(val instanceof ApplicationMessage)) {
                return false;
            }
            ApplicationMessage msg = (ApplicationMessage) val;

            return Objects.equals(session1.getPublicKey(), msg.getSender()) && Objects.equals(session2.getPublicKey(), msg.getRecipient()) && Arrays.equals(payload, msg.getPayload());
        });
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void notJoiningClientsShouldBeDroppedAfterTimeout() throws ExecutionException, InterruptedException {
        // create connection
        TestNodeServerConnection session = clientSession(config, server, identitySession1);

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
    void joinedClientsShouldNoBeDroppedAfterTimeout() throws InterruptedException, ExecutionException {
        // create connection
        TestNodeServerConnection session = clientSessionAfterJoin(config, server, identitySession1);

        // wait until timeout
        Thread.sleep(config.getServerHandshakeTimeout().plusSeconds(2).toMillis());// NOSONAR

        // verify session status
        assertFalse(session.isClosed().isDone());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void invalidMessageShouldBeRespondedWithExceptionMessage() throws ExecutionException, InterruptedException {
        // create connection
        TestNodeServerConnection session = clientSession(config, server, identitySession1);

        TestObserver<Message> receivedMessages = session.receivedMessages().test();

        // send message
        session.sendRawBinary(Unpooled.buffer().writeBytes("invalid message".getBytes()));

        // verify response
        receivedMessages.awaitCount(1);
        receivedMessages.assertValue(val -> ((ConnectionExceptionMessage) val).getError() == CONNECTION_ERROR_INITIALIZATION);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void newSessionWithSameIdentityShouldReplaceAndCloseExistingSession() throws ExecutionException, InterruptedException {
        // create connections
        TestNodeServerConnection session1 = clientSession(config, server, identitySession1);
        TestNodeServerConnection session2 = clientSession(config, server, session1.getIdentity());

        TestObserver<Message> receivedMessages1 = session1.receivedMessages().test();
        TestObserver<Message> receivedMessages2 = session2.receivedMessages().test();

        // send messages
        RequestMessage request1 = new JoinMessage(session1.getIdentity().getProofOfWork(), session1.getIdentity().getPublicKey(), Set.of());
        ResponseMessage<?> response1 = session1.sendRequest(request1).get();
        session1.send(new StatusMessage(STATUS_OK, response1.getId()));
        await().until(() -> server.getChannelGroup().find(session1.getIdentity().getPublicKey()) != null);

        RequestMessage request2 = new JoinMessage(session1.getIdentity().getProofOfWork(), session1.getIdentity().getPublicKey(), Set.of());
        ResponseMessage<?> response2 = session2.sendRequest(request2).join();
        session2.send(new StatusMessage(STATUS_OK, response2.getId()));

        // verify responses
        receivedMessages1.awaitCount(2);
        receivedMessages1.assertValueAt(0, val -> {
            if (!(val instanceof WelcomeMessage)) {
                return false;
            }
            WelcomeMessage msg = (WelcomeMessage) val;

            return Objects.equals(server.getIdentity().getPublicKey(), msg.getPublicKey()) && Objects.equals(PeerInformation.of(server.getEndpoints()), msg.getPeerInformation()) && Objects.equals(msg.getCorrespondingId(), request1.getId());
        });
        receivedMessages1.assertValueAt(1, val -> ((QuitMessage) val).getReason() == REASON_NEW_SESSION);
        receivedMessages2.awaitCount(1);
        receivedMessages2.assertValue(val -> {
            if (!(val instanceof WelcomeMessage)) {
                return false;
            }
            WelcomeMessage msg = (WelcomeMessage) val;

            return Objects.equals(server.getIdentity().getPublicKey(), msg.getPublicKey()) && Objects.equals(PeerInformation.of(server.getEndpoints()), msg.getPeerInformation()) && Objects.equals(msg.getCorrespondingId(), request2.getId());
        });
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientsNotSendingPongMessageShouldBeDroppedAfterTimeout() throws ExecutionException, InterruptedException {
        // create connection
        TestNodeServerConnection session = clientSession(config, server, identitySession1, false);

        TestObserver<Message> receivedMessages = session.receivedMessages().test();

        // wait until timeout
        Thread.sleep(config.getServerIdleTimeout().toMillis() * (config.getServerIdleRetries() + 1) + 1000);// NOSONAR

        // verify responses
        receivedMessages.awaitCount(3);
        receivedMessages.assertValueAt(0, val -> val instanceof PingMessage);
        receivedMessages.assertValueAt(1, val -> val instanceof PingMessage);
        receivedMessages.assertValueAt(2, val -> ((ConnectionExceptionMessage) val).getError() == CONNECTION_ERROR_PING_PONG);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientsSendingPongMessageShouldNotBeDroppedAfterTimeout() throws ExecutionException, InterruptedException {
        // create connection
        TestNodeServerConnection session = clientSessionAfterJoin(config, server, identitySession1);

        // wait until timeout
        Thread.sleep(config.getServerIdleTimeout().toMillis() * (config.getServerIdleRetries() + 1) + 1000);// NOSONAR

        // verify session status
        assertFalse(session.isClosed().isDone());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void pingMessageShouldBeRespondedWithPongMessage() throws ExecutionException, InterruptedException {
        // create connection
        TestNodeServerConnection session = clientSession(config, server, identitySession1, false);

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
        TestNodeServerConnection session = clientSession(config, server, identitySession1);

        // send message
        CompressedPublicKey sender = CompressedPublicKey.of("023ce7bb9756b5aa68fb82914ecafb71c3bb86701d4f200ae68420d13eddda7ebf");
        CompressedPublicKey recipient = CompressedPublicKey.of("037e43ee5c82742f00355f13b9714c63e53a74a694b7de8d4715f06d9e7880bdbf");
        RequestMessage request = new ApplicationMessage(sender, recipient, new byte[]{
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
    void messageWithMaxSizeShouldArrive() throws InterruptedException, ExecutionException {
        // create connection
        TestNodeServerConnection session1 = clientSessionAfterJoin(config, server, identitySession1);
        TestNodeServerConnection session2 = clientSessionAfterJoin(config, server, identitySession1);

        TestObserver<Message> receivedMessages = session2.receivedMessages().test();

        // create message with max allowed payload size
        byte[] bigPayload = new byte[config.getMessageMaxContentLength()];
        new Random().nextBytes(bigPayload);

        // send message
        RequestMessage request = new ApplicationMessage(session1.getPublicKey(), session2.getPublicKey(), bigPayload);
        session2.send(request);

        // verify response
        receivedMessages.awaitCount(1);
        receivedMessages.assertValue(request);
    }

    @Disabled("Muss noch implementiert werden")
    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void messageExceedingMaxSizeShouldThrowExceptionOnSend() throws InterruptedException, ExecutionException {
        // create connection
        TestNodeServerConnection session1 = clientSessionAfterJoin(config, server, identitySession1);
        TestNodeServerConnection session2 = clientSessionAfterJoin(config, server, identitySession1);

        // create message with exceeded payload size
        byte[] bigPayload = new byte[config.getMessageMaxContentLength() + 1];
        new Random().nextBytes(bigPayload);

        // send message
        RequestMessage request = new ApplicationMessage(session1.getPublicKey(), session2.getPublicKey(), bigPayload);

        assertThrows(DrasylException.class, () -> session2.send(request));
    }

    @Test
    void shouldOpenAndCloseGracefully() throws DrasylException {
        NodeServer server = new NodeServer(identityManager::getIdentity, messenger, peersManager, new DrasylConfig(), workerGroup, bossGroup, superPeerConnected);

        server.open();
        server.close();

        assertTrue(true);
    }

    @Test
    void openShouldFailIfInvalidPortIsGiven() throws DrasylException {
        DrasylConfig config = DrasylConfig.newBuilder().serverBindPort(72722).build();
        NodeServer server = new NodeServer(identityManager::getIdentity, messenger, peersManager, config, workerGroup, bossGroup, superPeerConnected);

        assertThrows(NodeServerException.class, server::open);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void shuttingDownServerShouldSendLeaveMessage() throws ExecutionException, InterruptedException {
        TestNodeServerConnection session = clientSessionAfterJoin(config, server, identitySession1);

        TestObserver<Message> receivedMessages = session.receivedMessages().test();

        server.close();

        // verify responses
        receivedMessages.awaitCount(1);
        receivedMessages.assertValue(val -> ((QuitMessage) val).getReason() == REASON_SHUTTING_DOWN);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void shuttingDownServerShouldRejectNewConnections() throws ExecutionException, InterruptedException {
        TestNodeServerConnection session = clientSession(config, server, identitySession1);

        server.close();

        // send message
        RequestMessage request = new JoinMessage(session.getIdentity().getProofOfWork(), session.getIdentity().getPublicKey(), Set.of());
        CompletableFuture<ResponseMessage<?>> send = session.sendRequest(request);

        // verify response
        StatusMessage received = (StatusMessage) send.get();

        assertEquals(STATUS_SERVICE_UNAVAILABLE, received.getCode());
        assertEquals(request.getId(), received.getCorrespondingId());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void MessageWithWrongSignatureShouldProduceExceptionMessage() throws CryptoException, JsonProcessingException, ExecutionException, InterruptedException {
        // create connection
        TestNodeServerConnection session = clientSession(config, server, identitySession1, false);
        TestObserver<Message> receivedMessages = session.receivedMessages().filter(msg -> msg instanceof StatusMessage).test();

        // send message
        Message request = new PingMessage();
        SignedMessage signedMessage = new SignedMessage(request, session.getPublicKey());
        Crypto.sign(identitySession2.getPrivateKey().toUncompressedKey(), signedMessage);
        byte[] binary = JACKSON_WRITER.writeValueAsBytes(signedMessage);
        session.sendRawBinary(Unpooled.wrappedBuffer(binary));

        // verify response
        receivedMessages.awaitCount(1);
        receivedMessages.assertValue(val -> ((StatusMessage) val).getCode() == StatusMessage.Code.STATUS_INVALID_SIGNATURE);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void wrongPoWShouldResultInError() throws ExecutionException, InterruptedException {
        // create connections
        TestNodeServerConnection session = clientSession(config, server, identitySession1);
        TestObserver<Message> receivedMessages = session.receivedMessages().filter(msg -> msg instanceof ConnectionExceptionMessage).test();

        // send messages
        RequestMessage request1 = new JoinMessage(identitySession2.getProofOfWork(), session.getIdentity().getPublicKey(), Set.of());
        session.sendRequest(request1);

        // verify response
        receivedMessages.awaitCount(1);
        receivedMessages.assertValue(val -> ((ConnectionExceptionMessage) val).getError() == CONNECTION_ERROR_PROOF_OF_WORK_INVALID);
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
