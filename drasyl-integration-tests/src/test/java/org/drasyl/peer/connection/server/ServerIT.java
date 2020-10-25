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
import io.netty.util.ResourceLeakDetector;
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
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.client.TestClientChannelInitializer;
import org.drasyl.peer.connection.client.TestSuperPeerClient;
import org.drasyl.peer.connection.handler.stream.ChunkedMessageHandler;
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
import org.drasyl.peer.connection.pipeline.DirectConnectionMessageSinkHandler;
import org.drasyl.peer.connection.pipeline.LoopbackMessageSinkHandler;
import org.drasyl.peer.connection.pipeline.SuperPeerMessageSinkHandler;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.pipeline.Pipeline;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_HANDSHAKE_TIMEOUT;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_INITIALIZATION;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_PING_PONG;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_PROOF_OF_WORK_INVALID;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_NEW_SESSION;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_FORBIDDEN;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_SERVICE_UNAVAILABLE;
import static org.drasyl.peer.connection.pipeline.DirectConnectionMessageSinkHandler.DIRECT_CONNECTION_MESSAGE_SINK_HANDLER;
import static org.drasyl.peer.connection.pipeline.LoopbackMessageSinkHandler.LOOPBACK_MESSAGE_SINK_HANDLER;
import static org.drasyl.peer.connection.pipeline.SuperPeerMessageSinkHandler.SUPER_PEER_SINK_HANDLER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.drasyl.util.NetworkUtil.createInetAddress;
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
class ServerIT {
    public static final long TIMEOUT = 10000L;
    private static EventLoopGroup workerGroup;
    private static EventLoopGroup bossGroup;
    private DrasylConfig configClient1;
    private DrasylConfig configClient2;
    private DrasylConfig serverConfig;
    private IdentityManager serverIdentityManager;
    private Server server;
    private PeersManager peersManager;
    private Identity identitySession1;
    private Identity identitySession2;
    private AtomicBoolean opened;
    private Set<Endpoint> endpoints;
    private AtomicBoolean acceptNewConnections;
    private PeerChannelGroup channelGroup;
    private int networkId;
    private Pipeline pipeline;

    @BeforeEach
    void setup(final TestInfo info) throws DrasylException, CryptoException {
//        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.drasyl");
//        root.setLevel(Level.TRACE);

        colorizedPrintln("STARTING " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);

        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        System.setProperty("io.netty.leakDetection.level", "PARANOID");

        identitySession1 = Identity.of(169092, "030a59784f88c74dcd64258387f9126739c3aeb7965f36bb501ff01f5036b3d72b", "0f1e188d5e3b98daf2266d7916d2e1179ae6209faa7477a2a66d4bb61dab4399");
        identitySession2 = Identity.of(26778671, "0236fde6a49564a0eaa2a7d6c8f73b97062d5feb36160398c08a5b73f646aa5fe5", "093d1ee70518508cac18eaf90d312f768c14d43de9bfd2618a2794d8df392da0");

        serverConfig = DrasylConfig.newBuilder()
                .networkId(0)
                .identityProofOfWork(ProofOfWork.of(6657650))
                .identityPublicKey(CompressedPublicKey.of("023d34f317616c3bb0fa1e4b425e9419d1704ef57f6e53afe9790e00998134f5ff"))
                .identityPrivateKey(CompressedPrivateKey.of("0c27af38c77f2cd5cc2a0ff5c461003a9c24beb955f316135d251ecaf4dda03f"))
                .serverExposeEnabled(false)
                .serverBindHost(createInetAddress("127.0.0.1"))
                .serverBindPort(0)
                .serverEndpoints(Set.of(Endpoint.of("wss://127.0.0.1:0#023d34f317616c3bb0fa1e4b425e9419d1704ef57f6e53afe9790e00998134f5ff")))
                .serverHandshakeTimeout(ofSeconds(5))
                .serverSSLEnabled(true)
                .serverIdleTimeout(ofSeconds(1))
                .serverIdleRetries((short) 1)
                .superPeerEnabled(false)
                .messageMaxContentLength(1024 * 1024)
                .build();
        serverIdentityManager = new IdentityManager(serverConfig);
        serverIdentityManager.loadOrCreateIdentity();
        peersManager = new PeersManager(event -> {
        }, serverIdentityManager.getIdentity());
        channelGroup = new PeerChannelGroup();
        pipeline = new DrasylPipeline(event -> {
        }, serverConfig, serverIdentityManager.getIdentity());
        pipeline.addFirst(SUPER_PEER_SINK_HANDLER, new SuperPeerMessageSinkHandler(channelGroup, peersManager));
        pipeline.addAfter(SUPER_PEER_SINK_HANDLER, DIRECT_CONNECTION_MESSAGE_SINK_HANDLER, new DirectConnectionMessageSinkHandler(channelGroup));
        pipeline.addAfter(DIRECT_CONNECTION_MESSAGE_SINK_HANDLER, LOOPBACK_MESSAGE_SINK_HANDLER, new LoopbackMessageSinkHandler(new AtomicBoolean(true), serverIdentityManager.getIdentity(), peersManager, endpoints));
        opened = new AtomicBoolean(false);
        endpoints = new HashSet<>();
        acceptNewConnections = new AtomicBoolean(true);

        server = new Server(serverIdentityManager.getIdentity(), pipeline, peersManager, serverConfig, channelGroup, workerGroup, bossGroup, opened, acceptNewConnections::get, endpoints);
        server.open();

        configClient1 = DrasylConfig.newBuilder()
                .networkId(0)
                .serverEnabled(false)
                .identityProofOfWork(identitySession1.getProofOfWork())
                .identityPublicKey(identitySession1.getPublicKey())
                .identityPrivateKey(identitySession1.getPrivateKey())
                .serverExposeEnabled(false)
                .serverSSLEnabled(true)
                .superPeerEndpoints(endpoints)
                .superPeerChannelInitializer(TestClientChannelInitializer.class)
                .messageComposedMessageTransferTimeout(ofSeconds(60))
                .messageMaxContentLength(1024 * 1024)
                .superPeerRetryDelays(List.of())
                .build();

        configClient2 = DrasylConfig.newBuilder(configClient1)
                .identityProofOfWork(identitySession2.getProofOfWork())
                .identityPublicKey(identitySession2.getPublicKey())
                .identityPrivateKey(identitySession2.getPrivateKey())
                .build();

        networkId = 0;
    }

    @AfterEach
    void cleanUp(final TestInfo info) throws IdentityManagerException {
        server.close();

        IdentityManager.deleteIdentityFile(configClient1.getIdentityPath());

        colorizedPrintln("FINISHED " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void joinMessageShouldBeRespondedWithWelcomeMessage() throws ExecutionException, InterruptedException {
        // create connection
        try (final TestSuperPeerClient session = clientSession(configClient1, server, identitySession1)) {
            // send message
            final RequestMessage request = new JoinMessage(networkId, session.getIdentity().getPublicKey(), session.getIdentity().getProofOfWork(), configClient1.getSuperPeerEndpoints().iterator().next().getPublicKey(), true);
            final CompletableFuture<ResponseMessage<?>> send = session.sendRequest(request);

            // verify response
            final ResponseMessage<?> response = send.get();

            assertThat(response, instanceOf(WelcomeMessage.class));
        }
    }

    @Test
    void applicationMessageShouldBeForwardedToRecipient() {
        // create connections
        try (final TestSuperPeerClient session1 = clientSessionAfterJoin(configClient1, identitySession1)) {
            try (final TestSuperPeerClient session2 = clientSessionAfterJoin(configClient2, identitySession2)) {
                final TestObserver<Message> receivedMessages2 = session2.receivedMessages().test();

                final byte[] payload = new byte[]{
                        0x00,
                        0x01,
                        0x02
                };

                // send message
                final RequestMessage request = new ApplicationMessage(session1.getPublicKey(), session2.getPublicKey(), payload);
                session1.send(request);
                receivedMessages2.awaitCount(1);
                receivedMessages2.assertValueAt(0, val -> {
                    if (!(val instanceof ApplicationMessage)) {
                        return false;
                    }
                    final ApplicationMessage msg = (ApplicationMessage) val;

                    return Objects.equals(session1.getPublicKey(), msg.getSender()) && Objects.equals(session2.getPublicKey(), msg.getRecipient()) && Arrays.equals(payload, msg.getPayload());
                });
            }
        }
    }

    private TestSuperPeerClient clientSessionAfterJoin(final DrasylConfig config,
                                                       final Identity identity) {
        final TestSuperPeerClient client = new TestSuperPeerClient(config, identity, workerGroup, true, true, endpoints);
        client.open();
        awaitClientJoin(identity);
        return client;
    }

    private void awaitClientJoin(final Identity identity) {
        await().until(() -> channelGroup.find(identity.getPublicKey()) != null);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void multipleJoinMessagesShouldBeRespondedWithWelcomeMessage() throws ExecutionException, InterruptedException {
        // create connections
        try (final TestSuperPeerClient session1 = clientSession(configClient1, server, identitySession1)) {
            try (final TestSuperPeerClient session2 = clientSession(configClient2, server, identitySession2)) {

                // send messages
                final RequestMessage request1 = new JoinMessage(networkId, session1.getIdentity().getPublicKey(), session1.getIdentity().getProofOfWork(), configClient1.getSuperPeerEndpoints().iterator().next().getPublicKey(), true);
                final CompletableFuture<ResponseMessage<?>> send1 = session1.sendRequest(request1);

                final RequestMessage request2 = new JoinMessage(networkId, session2.getIdentity().getPublicKey(), session2.getIdentity().getProofOfWork(), configClient2.getSuperPeerEndpoints().iterator().next().getPublicKey(), true);
                final CompletableFuture<ResponseMessage<?>> send2 = session2.sendRequest(request2);

                // verify responses
                final ResponseMessage<?> response1 = send1.get();
                final ResponseMessage<?> response2 = send2.get();

                assertThat(response1, instanceOf(WelcomeMessage.class));
                assertThat(response2, instanceOf(WelcomeMessage.class));
            }
        }
    }

    private TestSuperPeerClient clientSession(final DrasylConfig config,
                                              final Server server,
                                              final Identity identity) {
        return clientSession(config, identity, true);
    }

    private TestSuperPeerClient clientSession(final DrasylConfig config,
                                              final Identity identity,
                                              final boolean doPingPong) {
        final TestSuperPeerClient client = new TestSuperPeerClient(config, identity, workerGroup, doPingPong, false, endpoints);
        client.open();
        return client;
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void joinedClientsShouldNoBeDroppedAfterTimeout() throws InterruptedException {
        // create connection
        try (final TestSuperPeerClient session = clientSessionAfterJoin(configClient1, identitySession1)) {

            // wait until timeout
            Thread.sleep(serverConfig.getServerHandshakeTimeout().plusSeconds(2).toMillis());// NOSONAR

            // verify session status
            assertFalse(session.isClosed());
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void newSessionWithSameIdentityShouldReplaceAndCloseExistingSession() throws ExecutionException, InterruptedException {
        // create connections
        try (final TestSuperPeerClient session1 = clientSession(configClient1, server, identitySession1)) {
            try (final TestSuperPeerClient session2 = clientSession(configClient1, server, session1.getIdentity())) {

                final TestObserver<Message> receivedMessages1 = session1.receivedMessages().test();
                final TestObserver<Message> receivedMessages2 = session2.receivedMessages().test();

                // send messages
                final RequestMessage request1 = new JoinMessage(networkId, session1.getIdentity().getPublicKey(), session1.getIdentity().getProofOfWork(), configClient1.getSuperPeerEndpoints().iterator().next().getPublicKey(), true);
                final ResponseMessage<?> response1 = session1.sendRequest(request1).get();
                session1.send(new StatusMessage(STATUS_OK, response1.getId()));
                await().until(() -> channelGroup.find(session1.getIdentity().getPublicKey()) != null);

                final RequestMessage request2 = new JoinMessage(networkId, session1.getIdentity().getPublicKey(), session1.getIdentity().getProofOfWork(), configClient2.getSuperPeerEndpoints().iterator().next().getPublicKey(), true);
                final ResponseMessage<?> response2 = session2.sendRequest(request2).join();
                session2.send(new StatusMessage(STATUS_OK, response2.getId()));

                // verify responses
                receivedMessages1.awaitCount(2);
                receivedMessages1.assertValueAt(0, val -> {
                    if (!(val instanceof WelcomeMessage)) {
                        return false;
                    }
                    final WelcomeMessage msg = (WelcomeMessage) val;

                    return Objects.equals(PeerInformation.of(endpoints), msg.getPeerInformation()) && Objects.equals(msg.getCorrespondingId(), request1.getId());
                });
                receivedMessages1.assertValueAt(1, val -> ((QuitMessage) val).getReason() == REASON_NEW_SESSION);
                receivedMessages2.awaitCount(1);
                receivedMessages2.assertValueAt(0, val -> {
                    if (!(val instanceof WelcomeMessage)) {
                        return false;
                    }
                    final WelcomeMessage msg = (WelcomeMessage) val;

                    return Objects.equals(PeerInformation.of(endpoints), msg.getPeerInformation()) && Objects.equals(msg.getCorrespondingId(), request2.getId());
                });
            }
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void notJoiningClientsShouldBeDroppedAfterTimeout() throws InterruptedException {
        // create connection
        try (final TestSuperPeerClient session = clientSession(configClient1, server, identitySession1)) {

            final TestObserver<Message> receivedMessages = session.receivedMessages().filter(m -> m instanceof ConnectionExceptionMessage).test();

            // wait for timeout
            Thread.sleep(serverConfig.getServerHandshakeTimeout().plusSeconds(2).toMillis());// NOSONAR

            // verify response
            receivedMessages.awaitCount(1);
            receivedMessages.assertValueAt(0, new ConnectionExceptionMessage(CONNECTION_ERROR_HANDSHAKE_TIMEOUT));
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void invalidMessageShouldBeRespondedWithExceptionMessage() {
        // create connection
        try (final TestSuperPeerClient session = clientSession(configClient1, server, identitySession1)) {
            final TestObserver<Message> receivedMessages = session.receivedMessages().test();

            // send message
            session.sendRawBinary(Unpooled.buffer().writeBytes("invalid message".getBytes()));

            // verify response
            receivedMessages.awaitCount(1);
            receivedMessages.assertValueAt(0, val -> ((ConnectionExceptionMessage) val).getError() == CONNECTION_ERROR_INITIALIZATION);
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientsNotSendingPongMessageShouldBeDroppedAfterTimeout() throws InterruptedException {
        // create connection
        try (final TestSuperPeerClient session = clientSession(configClient1, identitySession1, false)) {

            final TestObserver<Message> receivedMessages = session.receivedMessages().filter(m -> m instanceof ConnectionExceptionMessage).test();

            // wait until timeout
            Thread.sleep(serverConfig.getServerIdleTimeout().toMillis() * (serverConfig.getServerIdleRetries() + 1) + 1000);// NOSONAR

            // verify responses
            receivedMessages.awaitCount(1);
            receivedMessages.assertValueAt(0, new ConnectionExceptionMessage(CONNECTION_ERROR_PING_PONG));
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientsSendingPongMessageShouldNotBeDroppedAfterTimeout() throws InterruptedException {
        // create connection
        try (final TestSuperPeerClient session = clientSessionAfterJoin(configClient1, identitySession1)) {

            // wait until timeout
            Thread.sleep(serverConfig.getServerIdleTimeout().toMillis() * (serverConfig.getServerIdleRetries() + 1) + 1000);// NOSONAR

            // verify session status
            assertFalse(session.isClosed());
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void pingMessageShouldBeRespondedWithPongMessage() throws ExecutionException, InterruptedException {
        // create connection
        try (final TestSuperPeerClient session = clientSession(configClient1, identitySession1, false)) {

            // send message
            final RequestMessage request = new PingMessage();
            final CompletableFuture<ResponseMessage<?>> send = session.sendRequest(request);

            // verify response
            final ResponseMessage<?> response = send.get();

            assertEquals(request.getId(), response.getCorrespondingId());
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void nonAuthorizedClientSendingNonJoinMessageShouldBeRespondedWithStatusForbiddenMessage() throws ExecutionException, InterruptedException, CryptoException {
        // create connection
        try (final TestSuperPeerClient session = clientSession(configClient1, server, identitySession1)) {

            // send message
            final CompressedPublicKey sender = CompressedPublicKey.of("023ce7bb9756b5aa68fb82914ecafb71c3bb86701d4f200ae68420d13eddda7ebf");
            final CompressedPublicKey recipient = CompressedPublicKey.of("037e43ee5c82742f00355f13b9714c63e53a74a694b7de8d4715f06d9e7880bdbf");
            final RequestMessage request = new ApplicationMessage(sender, recipient, new byte[]{
                    0x00,
                    0x01
            });
            final CompletableFuture<ResponseMessage<?>> send = session.sendRequest(request);

            // verify response
            final StatusMessage response = (StatusMessage) send.get();

            assertEquals(STATUS_FORBIDDEN, response.getCode());
            assertEquals(request.getId(), response.getCorrespondingId());
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void messageWithMaxSizeShouldArrive() {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        // create connection
        try (final TestSuperPeerClient session1 = clientSessionAfterJoin(configClient1, identitySession1)) {
            try (final TestSuperPeerClient session2 = clientSessionAfterJoin(configClient2, identitySession2)) {

                final TestObserver<Message> receivedMessages = session2.receivedMessages().test();

                // create message with max allowed payload size
                final byte[] bigPayload = new byte[configClient1.getMessageMaxContentLength()];
                new Random().nextBytes(bigPayload);

                // send message
                final RequestMessage request = new ApplicationMessage(session1.getPublicKey(), session2.getPublicKey(), bigPayload);
                session2.send(request);

                // verify response
                receivedMessages.awaitCount(1);
                receivedMessages.assertValueAt(0, request);
            }
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void messageExceedingChunkSizeShouldBeSend() {
        // create connections
        try (final TestSuperPeerClient session1 = clientSessionAfterJoin(configClient1, identitySession1)) {
            try (final TestSuperPeerClient session2 = clientSessionAfterJoin(configClient2, identitySession2)) {
                final TestObserver<Message> receivedMessages2 = session2.receivedMessages().test();

                // create message with exceeded chunk size
                final byte[] bigPayload = new byte[Math.min(ChunkedMessageHandler.CHUNK_SIZE * 2 + ChunkedMessageHandler.CHUNK_SIZE / 2, configClient1.getMessageMaxContentLength())];
                new Random().nextBytes(bigPayload);

                // send message
                final RequestMessage request = new ApplicationMessage(session1.getPublicKey(), session2.getPublicKey(), bigPayload);

                session1.send(request);
                receivedMessages2.awaitCount(1);
                receivedMessages2.assertValueAt(0, val -> {
                    if (!(val instanceof ApplicationMessage)) {
                        return false;
                    }
                    final ApplicationMessage msg = (ApplicationMessage) val;

                    return Objects.equals(session1.getPublicKey(), msg.getSender()) && Objects.equals(session2.getPublicKey(), msg.getRecipient()) && Arrays.equals(bigPayload, msg.getPayload());
                });
            }
        }
    }

    @Test
    void shouldOpenAndCloseGracefully() throws DrasylException {
        try (final Server myServer = new Server(serverIdentityManager.getIdentity(), pipeline, peersManager, serverConfig, channelGroup, workerGroup, bossGroup, endpoints, acceptNewConnections::get)) {
            myServer.open();
        }

        assertTrue(true);
    }

    @Test
    void constructorShouldFailIfInvalidPortIsGiven() {
        final DrasylConfig config = DrasylConfig.newBuilder()
                .networkId(0)
                .serverBindPort(72722)
                .build();
        final Identity identity = serverIdentityManager.getIdentity();
        assertThrows(IllegalArgumentException.class, () -> new Server(identity, pipeline, peersManager, config, channelGroup, workerGroup, bossGroup, endpoints, acceptNewConnections::get));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void shuttingDownServerShouldRejectNewConnections() throws ExecutionException, InterruptedException {
        try (final TestSuperPeerClient session = clientSession(configClient1, server, identitySession1)) {
            acceptNewConnections.set(false);

            // send message
            final RequestMessage request = new JoinMessage(networkId, session.getIdentity().getPublicKey(), session.getIdentity().getProofOfWork(), configClient1.getSuperPeerEndpoints().iterator().next().getPublicKey(), true);
            final CompletableFuture<ResponseMessage<?>> send = session.sendRequest(request);

            // verify response
            final StatusMessage received = (StatusMessage) send.get();

            assertEquals(STATUS_SERVICE_UNAVAILABLE, received.getCode());
            assertEquals(request.getId(), received.getCorrespondingId());
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void messageWithWrongSignatureShouldProduceExceptionMessage() throws CryptoException, JsonProcessingException {
        // create connection
        try (final TestSuperPeerClient session = clientSession(configClient1, identitySession1, false)) {
            final TestObserver<Message> receivedMessages = session.receivedMessages().filter(msg -> msg instanceof StatusMessage).test();

            // send message
            final Message request = new PingMessage();
            final SignedMessage signedMessage = new SignedMessage(request, session.getPublicKey());
            Crypto.sign(identitySession2.getPrivateKey().toUncompressedKey(), signedMessage);
            final byte[] binary = JACKSON_WRITER.writeValueAsBytes(signedMessage);
            session.sendRawBinary(Unpooled.wrappedBuffer(binary));

            // verify response
            receivedMessages.awaitCount(1);
            receivedMessages.assertValueAt(0, val -> ((StatusMessage) val).getCode() == StatusMessage.Code.STATUS_INVALID_SIGNATURE);
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void wrongPoWShouldResultInError() {
        // create connections
        try (final TestSuperPeerClient session = clientSession(configClient1, server, identitySession1)) {
            final TestObserver<Message> receivedMessages = session.receivedMessages().filter(msg -> msg instanceof ConnectionExceptionMessage).test();

            // send messages
            final RequestMessage request1 = new JoinMessage(networkId, session.getIdentity().getPublicKey(), identitySession2.getProofOfWork(), configClient1.getSuperPeerEndpoints().iterator().next().getPublicKey(), true);
            session.sendRequest(request1);

            // verify response
            receivedMessages.awaitCount(1);
            receivedMessages.assertValueAt(0, val -> ((ConnectionExceptionMessage) val).getError() == CONNECTION_ERROR_PROOF_OF_WORK_INVALID);
        }
    }

    @BeforeAll
    static void beforeAll() {
        workerGroup = DrasylNode.getBestEventLoop();
        bossGroup = DrasylNode.getBestEventLoop(1);
    }

    @AfterAll
    static void afterAll() {
        workerGroup.shutdownGracefully().syncUninterruptibly();
        bossGroup.shutdownGracefully().syncUninterruptibly();
    }
}