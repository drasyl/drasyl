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
package org.drasyl.peer.connection.client;

import io.netty.channel.EventLoopGroup;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityManagerException;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.SuccessMessage;
import org.drasyl.peer.connection.server.TestServer;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.pipeline.Pipeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.drasyl.util.AnsiColor.COLOR_CYAN;
import static org.drasyl.util.AnsiColor.STYLE_REVERSED;
import static org.drasyl.util.NetworkUtil.createInetAddress;
import static testutils.TestHelper.colorizedPrintln;

@Execution(ExecutionMode.SAME_THREAD)
class SuperPeerClientIT {
    public static final long TIMEOUT = 10000L;
    DrasylConfig config;
    DrasylConfig serverConfig;
    private EventLoopGroup workerGroup;
    private EventLoopGroup serverWorkerGroup;
    private EventLoopGroup bossGroup;
    private IdentityManager identityManager;
    private IdentityManager identityManagerServer;
    private TestServer server;
    private PeersManager peersManager;
    private PeersManager peersManagerServer;
    private Subject<Event> emittedEventsSubject;
    private PeerChannelGroup channelGroup;
    private PeerChannelGroup channelGroupServer;
    private Set<Endpoint> endpoints;
    private int networkId;
    private Pipeline pipeline;
    private Pipeline pipelineServer;

    @BeforeEach
    void setup(final TestInfo info) throws DrasylException, CryptoException {
//        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.drasyl");
//        root.setLevel(Level.TRACE);

        colorizedPrintln("STARTING " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);

        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        System.setProperty("io.netty.leakDetection.level", "PARANOID");

        workerGroup = DrasylNode.getBestEventLoop();
        serverWorkerGroup = DrasylNode.getBestEventLoop();
        bossGroup = DrasylNode.getBestEventLoop(1);
        emittedEventsSubject = ReplaySubject.<Event>create().toSerialized();

        config = DrasylConfig.newBuilder()
                .networkId(0)
                .identityProofOfWork(ProofOfWork.of(6657650))
                .identityPublicKey(CompressedPublicKey.of("023d34f317616c3bb0fa1e4b425e9419d1704ef57f6e53afe9790e00998134f5ff"))
                .identityPrivateKey(CompressedPrivateKey.of("0c27af38c77f2cd5cc2a0ff5c461003a9c24beb955f316135d251ecaf4dda03f"))
                .serverExposeEnabled(false)
                .serverBindHost(createInetAddress("127.0.0.1"))
                .serverBindPort(0)
                .serverHandshakeTimeout(ofSeconds(5))
                .serverSSLEnabled(true)
                .superPeerEndpoints(Set.of(Endpoint.of("wss://127.0.0.1:22527#0234789936c7941f850c382ea9d14ecb0aad03b99a9e29a9c15b42f5f1b0c4cf3d")))
                .superPeerRetryDelays(List.of(ofSeconds(0), ofSeconds(1), ofSeconds(2), ofSeconds(4), ofSeconds(8), ofSeconds(16), ofSeconds(32), ofSeconds(60)))
                .build();
        identityManager = new IdentityManager(config);
        identityManager.loadOrCreateIdentity();

        serverConfig = DrasylConfig.newBuilder()
                .networkId(0)
                .identityProofOfWork(ProofOfWork.of(5344366))
                .identityPublicKey(CompressedPublicKey.of("0234789936c7941f850c382ea9d14ecb0aad03b99a9e29a9c15b42f5f1b0c4cf3d"))
                .identityPrivateKey(CompressedPrivateKey.of("064f10d37111303ee20443661c8ea758045bbf809e4950dd84b8a1348863d0f8"))
                .serverExposeEnabled(false)
                .serverBindHost(createInetAddress("127.0.0.1"))
                .serverBindPort(0)
                .serverExposeEnabled(false)
                .serverHandshakeTimeout(ofSeconds(5))
                .serverSSLEnabled(true)
                .superPeerEnabled(false)
                .build();
        identityManagerServer = new IdentityManager(serverConfig);
        identityManagerServer.loadOrCreateIdentity();
        peersManager = new PeersManager(emittedEventsSubject::onNext, identityManager.getIdentity());
        channelGroup = new PeerChannelGroup(networkId, identityManager.getIdentity());
        peersManagerServer = new PeersManager(event -> {
        }, identityManagerServer.getIdentity());
        channelGroupServer = new PeerChannelGroup(networkId, identityManagerServer.getIdentity());
        final AtomicBoolean started = new AtomicBoolean(true);
        pipeline = new DrasylPipeline(event -> {
        }, config, identityManager.getIdentity(), channelGroup, peersManager, started, endpoints);
        final AtomicBoolean startedServer = new AtomicBoolean(true);
        pipelineServer = new DrasylPipeline(event -> {
        }, serverConfig, identityManagerServer.getIdentity(), channelGroupServer, peersManagerServer, startedServer, endpoints);
        endpoints = new HashSet<>();

        server = new TestServer(identityManagerServer.getIdentity(), pipelineServer, peersManagerServer, serverConfig, channelGroupServer, serverWorkerGroup, bossGroup, endpoints);
        server.open();

        config = DrasylConfig.newBuilder()
                .networkId(0)
                .identityProofOfWork(ProofOfWork.of(6657650))
                .identityPublicKey(CompressedPublicKey.of("023d34f317616c3bb0fa1e4b425e9419d1704ef57f6e53afe9790e00998134f5ff"))
                .identityPrivateKey(CompressedPrivateKey.of("0c27af38c77f2cd5cc2a0ff5c461003a9c24beb955f316135d251ecaf4dda03f"))
                .serverExposeEnabled(false)
                .serverBindHost(createInetAddress("127.0.0.1"))
                .serverBindPort(0)
                .serverHandshakeTimeout(ofSeconds(5))
                .serverSSLEnabled(true)
                .superPeerEndpoints(endpoints)
                .serverBindPort(0)
                .superPeerRetryDelays(List.of(ofSeconds(0), ofSeconds(1), ofSeconds(2), ofSeconds(4), ofSeconds(8), ofSeconds(16), ofSeconds(32), ofSeconds(60)))
                .build();
        identityManager = new IdentityManager(config);
        identityManager.loadOrCreateIdentity();

        networkId = 0;
    }

    @AfterEach
    void cleanUp(final TestInfo info) throws IdentityManagerException {
        server.close();

        IdentityManager.deleteIdentityFile(config.getIdentityPath());
        workerGroup.shutdownGracefully().syncUninterruptibly();
        bossGroup.shutdownGracefully().syncUninterruptibly();
        serverWorkerGroup.shutdownGracefully().syncUninterruptibly();
        colorizedPrintln("FINISHED " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldSendJoinMessageOnConnect() {
        final TestObserver<Message> receivedMessages = server.receivedMessages().test();

        // start client
        try (final SuperPeerClient client = new SuperPeerClient(config, identityManager.getIdentity(), peersManager, pipeline, channelGroup, workerGroup, () -> true)) {
            client.open();

            // verify received messages
            receivedMessages.awaitCount(1);
            receivedMessages.assertValue(message -> message.equals(new JoinMessage(networkId, identityManager.getPublicKey(), identityManager.getProofOfWork(), config.getSuperPeerEndpoints().iterator().next().getPublicKey(), ((JoinMessage) message).getJoinTime())));
        }
    }

    @Disabled("Race Condition error")
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldSendQuitMessageOnClientSideDisconnect() throws CryptoException {
        final TestObserver<Message> receivedMessages = server.receivedMessages().filter(m -> m instanceof QuitMessage).test();
        final TestObserver<Event> emittedEvents = emittedEventsSubject.test();

        // start client
        final SuperPeerClient client = new SuperPeerClient(config, identityManager.getIdentity(), peersManager, pipeline, channelGroup, workerGroup, () -> true);
        client.open();

        // wait for node to become online, before closing it
        emittedEvents.awaitCount(1).assertValueCount(1);
        client.close();

        // verify emitted events
        receivedMessages.awaitCount(1);
        receivedMessages.assertValueAt(0, new QuitMessage(networkId, identityManager.getIdentity().getPublicKey(), identityManager.getIdentity().getProofOfWork(), CompressedPublicKey.of("023d34f317616c3bb0fa1e4b425e9419d1704ef57f6e53afe9790e00998134f5ff"), REASON_SHUTTING_DOWN));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldEmitNodeOfflineEventOnClientSideDisconnect() {
        final TestObserver<Event> emittedEvents = emittedEventsSubject.test();

        // start client
        final SuperPeerClient client = new SuperPeerClient(config, identityManager.getIdentity(), peersManager, pipeline, channelGroup, workerGroup, () -> true);
        client.open();

        // wait for node to become online, before closing it
        emittedEvents.awaitCount(2).assertValueCount(2);
        client.close();

        // verify emitted events
        emittedEvents.awaitCount(3);
        emittedEvents.assertValueAt(2, new NodeOfflineEvent(Node.of(identityManager.getIdentity())));
    }

    @Test
    @Disabled("disabled, because StatusMessage is currently not used and therefore has been removed.")
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldRespondToApplicationMessageWithStatusOk() {
        final TestObserver<Message> receivedMessages = server.receivedMessages().filter(m -> m instanceof SuccessMessage).test();

        // start client
        try (final SuperPeerClient client = new SuperPeerClient(config, identityManager.getIdentity(), peersManager, pipeline, channelGroup, workerGroup, () -> true)) {
            client.open();
            server.awaitClient(identityManager.getPublicKey());

            // send message
            final ApplicationMessage request = new ApplicationMessage(networkId, identityManagerServer.getPublicKey(), identityManagerServer.getProofOfWork(), identityManager.getPublicKey(), new byte[]{
                    0x00,
                    0x01
            });
            server.sendMessage(identityManager.getPublicKey(), request);

            // verify received message
            receivedMessages.awaitCount(2);
            receivedMessages.assertValueAt(1, new SuccessMessage(networkId, identityManager.getPublicKey(), identityManager.getProofOfWork(), identityManagerServer.getPublicKey(), request.getId()));
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldEmitNodeOfflineEventAfterReceivingQuitMessage() {
        final TestObserver<Event> emittedEvents = emittedEventsSubject.test();

        // start client
        final DrasylConfig noRetryConfig = DrasylConfig.newBuilder(config).superPeerRetryDelays(List.of()).build();
        try (final SuperPeerClient client = new SuperPeerClient(noRetryConfig, identityManager.getIdentity(), peersManager, pipeline, channelGroup, workerGroup, () -> true)) {
            client.open();
            server.awaitClient(identityManager.getPublicKey());

            // send message
            server.sendMessage(identityManager.getPublicKey(), new QuitMessage(networkId, identityManagerServer.getIdentity().getPublicKey(), identityManagerServer.getIdentity().getProofOfWork(), identityManager.getIdentity().getPublicKey(), REASON_SHUTTING_DOWN));

            // verify emitted events
            emittedEvents.awaitCount(3);
            emittedEvents.assertValueAt(2, new NodeOfflineEvent(Node.of(identityManager.getIdentity())));
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldEmitNodeOnlineEventAfterReceivingWelcomeMessage() {
        final TestObserver<Event> emittedEvents = emittedEventsSubject.test();

        // start client
        try (final SuperPeerClient client = new SuperPeerClient(config, identityManager.getIdentity(), peersManager, pipeline, channelGroup, workerGroup, () -> true)) {
            client.open();

            // verify emitted events
            emittedEvents.awaitCount(2);
            emittedEvents.assertValueAt(1, new NodeOnlineEvent(Node.of(identityManager.getIdentity())));
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldEmitNodeOnlineAlsoWithoutSuperPeerPubKeyInConfig() {
        final TestObserver<Event> emittedEvents = emittedEventsSubject.test();

        final DrasylConfig config1 = DrasylConfig.newBuilder(config).build();

        // start client
        try (final SuperPeerClient client = new SuperPeerClient(config1, identityManager.getIdentity(), peersManager, pipeline, channelGroup, workerGroup, () -> true)) {
            client.open();

            // verify emitted events
            emittedEvents.awaitCount(2);
            emittedEvents.assertValueAt(1, new NodeOnlineEvent(Node.of(identityManager.getIdentity())));
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldReconnectOnDisconnect() {
        final TestObserver<Event> emittedEvents = emittedEventsSubject.test();

        final DrasylConfig config1 = DrasylConfig.newBuilder(config).build();

        // start client
        try (final SuperPeerClient client = new SuperPeerClient(config1, identityManager.getIdentity(), peersManager, pipeline, channelGroup, workerGroup, () -> true)) {
            client.open();
            server.awaitClient(identityManager.getPublicKey());

            // server-side disconnect
            server.closeClient(identityManager.getPublicKey());

            // verify emitted events
            emittedEvents.awaitCount(5);
            emittedEvents.assertValueAt(1, new NodeOnlineEvent(Node.of(identityManager.getIdentity(), Set.of())));
            emittedEvents.assertValueAt(2, new NodeOfflineEvent(Node.of(identityManager.getIdentity(), Set.of())));
            emittedEvents.assertValueAt(4, new NodeOnlineEvent(Node.of(identityManager.getIdentity(), Set.of())));
        }
    }
}