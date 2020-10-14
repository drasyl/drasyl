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
package org.drasyl.peer.connection.streaming;

import io.netty.channel.EventLoopGroup;
import io.reactivex.rxjava3.core.Observable;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNode;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityManagerException;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.client.TestClientChannelInitializer;
import org.drasyl.peer.connection.client.TestSuperPeerClient;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.pipeline.DirectConnectionMessageSinkHandler;
import org.drasyl.peer.connection.pipeline.LoopbackMessageSinkHandler;
import org.drasyl.peer.connection.pipeline.SuperPeerMessageSinkHandler;
import org.drasyl.peer.connection.server.ServerException;
import org.drasyl.peer.connection.server.TestServer;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.pipeline.Pipeline;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestInfo;
import testutils.AnsiColor;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.drasyl.peer.connection.pipeline.DirectConnectionMessageSinkHandler.DIRECT_CONNECTION_MESSAGE_SINK_HANDLER;
import static org.drasyl.peer.connection.pipeline.LoopbackMessageSinkHandler.LOOPBACK_MESSAGE_SINK_HANDLER;
import static org.drasyl.peer.connection.pipeline.SuperPeerMessageSinkHandler.SUPER_PEER_SINK_HANDLER;
import static org.drasyl.util.NetworkUtil.createInetAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static testutils.AnsiColor.COLOR_CYAN;
import static testutils.AnsiColor.STYLE_REVERSED;
import static testutils.TestHelper.colorizedPrintln;

@Disabled("Only for benchmark purposes")
class ChunkedMessageIT {
    private static EventLoopGroup workerGroup;
    private static EventLoopGroup bossGroup;
    private static DrasylConfig config;
    private static TestServer server;
    private static TestSuperPeerClient session1;
    private static TestSuperPeerClient session2;
    private static byte[] bigPayload;
    private static PeerChannelGroup channelGroup;
    private static Set<Endpoint> endpoints;
    private static Pipeline pipeline;

    @BeforeEach
    void setup(final TestInfo info) {
        colorizedPrintln("STARTING " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
    }

    @AfterEach
    void cleanUp(final TestInfo info) {
        colorizedPrintln("FINISHED " + info.getDisplayName(), AnsiColor.COLOR_CYAN, AnsiColor.STYLE_REVERSED);
    }

    @RepeatedTest(5)
    void messageWithMaxSizeShouldArrive() {
        final Observable<Message> receivedMessages = session2.receivedMessages().filter(msg -> msg instanceof ApplicationMessage);

        // send message
        final RequestMessage request = new ApplicationMessage(session1.getPublicKey(), session2.getPublicKey(), bigPayload);
        session2.send(request);

        // verify response
        assertEquals(request, receivedMessages.blockingFirst());
    }

    @BeforeAll
    static void beforeAll() throws IdentityManagerException, ServerException, CryptoException {
//        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.drasyl");
//        root.setLevel(Level.TRACE);

        workerGroup = DrasylNode.getBestEventLoop();
        bossGroup = DrasylNode.getBestEventLoop(1);

        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        System.setProperty("io.netty.leakDetection.level", "DISABLED");

        final Identity identitySession1 = Identity.of(169092, "030a59784f88c74dcd64258387f9126739c3aeb7965f36bb501ff01f5036b3d72b", "0f1e188d5e3b98daf2266d7916d2e1179ae6209faa7477a2a66d4bb61dab4399");
        final Identity identitySession2 = Identity.of(26778671, "0236fde6a49564a0eaa2a7d6c8f73b97062d5feb36160398c08a5b73f646aa5fe5", "093d1ee70518508cac18eaf90d312f768c14d43de9bfd2618a2794d8df392da0");

        final DrasylConfig serverConfig = DrasylConfig.newBuilder()
                .networkId(0)
                .messageMaxContentLength(1024 * 1024 * 100)
                .identityProofOfWork(ProofOfWork.of(6657650))
                .identityPublicKey(CompressedPublicKey.of("023d34f317616c3bb0fa1e4b425e9419d1704ef57f6e53afe9790e00998134f5ff"))
                .identityPrivateKey(CompressedPrivateKey.of("0c27af38c77f2cd5cc2a0ff5c461003a9c24beb955f316135d251ecaf4dda03f"))
                .serverBindHost(createInetAddress("127.0.0.1"))
                .serverBindPort(0)
                .serverEndpoints(Set.of(Endpoint.of("wss://127.0.0.1:0")))
                .serverHandshakeTimeout(ofSeconds(5))
                .serverSSLEnabled(true)
                .serverIdleTimeout(ofSeconds(1))
                .serverIdleRetries((short) 1)
                .superPeerEnabled(false)
                .build();
        final IdentityManager serverIdentityManager = new IdentityManager(serverConfig);
        serverIdentityManager.loadOrCreateIdentity();
        final PeersManager peersManager = new PeersManager(event -> {
        }, serverIdentityManager.getIdentity());
        pipeline = new DrasylPipeline(event -> {
        }, serverConfig, serverIdentityManager.getIdentity());
        pipeline.addFirst(SUPER_PEER_SINK_HANDLER, new SuperPeerMessageSinkHandler(channelGroup, peersManager));
        pipeline.addAfter(SUPER_PEER_SINK_HANDLER, DIRECT_CONNECTION_MESSAGE_SINK_HANDLER, new DirectConnectionMessageSinkHandler(channelGroup));
        pipeline.addAfter(DIRECT_CONNECTION_MESSAGE_SINK_HANDLER, LOOPBACK_MESSAGE_SINK_HANDLER, new LoopbackMessageSinkHandler(new AtomicBoolean(true), serverIdentityManager.getIdentity(), peersManager, endpoints));
        channelGroup = new PeerChannelGroup();
        endpoints = new HashSet<>();

        server = new TestServer(serverIdentityManager.getIdentity(), pipeline, peersManager, serverConfig, channelGroup, workerGroup, bossGroup, endpoints);
        server.open();

        config = DrasylConfig.newBuilder()
                .networkId(0)
                .messageMaxContentLength(1024 * 1024 * 100)
                .serverEnabled(false)
                .serverSSLEnabled(true)
                .superPeerEndpoints(endpoints)
                .superPeerChannelInitializer(TestClientChannelInitializer.class)
                .build();

        session1 = clientSessionAfterJoin(config, identitySession1);
        session2 = clientSessionAfterJoin(config, identitySession2);

        // create message with max allowed payload size
        bigPayload = new byte[serverConfig.getMessageMaxContentLength()];
        new Random().nextBytes(bigPayload);
    }

    @AfterAll
    static void afterAll() throws IdentityManagerException {
        server.close();

        IdentityManager.deleteIdentityFile(config.getIdentityPath());

        workerGroup.shutdownGracefully().syncUninterruptibly();
        bossGroup.shutdownGracefully().syncUninterruptibly();
    }

    private static void awaitClientJoin(final Identity identity) {
        await().until(() -> server.getChannelGroup().find(identity.getPublicKey()) != null);
    }

    private static TestSuperPeerClient clientSessionAfterJoin(final DrasylConfig config,
                                                              final Identity identity) {
        final TestSuperPeerClient client = new TestSuperPeerClient(config, identity, workerGroup, true, true, endpoints);
        client.open();
        awaitClientJoin(identity);
        return client;
    }
}