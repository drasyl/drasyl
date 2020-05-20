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
package org.drasyl.peer.connection.superpeer;

import com.typesafe.config.ConfigFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.event.Event;
import org.drasyl.event.EventCode;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityManagerException;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.*;
import org.drasyl.peer.connection.server.NodeServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import testutils.AnsiColor;
import testutils.TestHelper;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Execution(ExecutionMode.SAME_THREAD)
class SuperPeerClientIT {
    public static final long TIMEOUT = 10000L;
    private EventLoopGroup workerGroup;
    private EventLoopGroup bossGroup;
    DrasylNodeConfig config;
    DrasylNodeConfig serverConfig;
    private IdentityManager identityManager;
    private IdentityManager identityManagerServer;
    private NodeServer server;
    private Messenger messenger;
    private PeersManager peersManager;

    @BeforeEach
    void setup(TestInfo info) throws DrasylException {
        TestHelper.colorizedPrintln("STARTING " + info.getDisplayName(), AnsiColor.COLOR_CYAN, AnsiColor.STYLE_REVERSED);

        System.setProperty("io.netty.tryReflectionSetAccessible", "true");

        workerGroup = new NioEventLoopGroup();
        bossGroup = new NioEventLoopGroup(1);

        config = new DrasylNodeConfig(ConfigFactory.load("configs/SuperPeerClientIT.conf"));
        identityManager = new IdentityManager(config);
        identityManager.loadOrCreateIdentity();

        serverConfig = new DrasylNodeConfig(ConfigFactory.load("configs/DummyServer.conf"));
        identityManagerServer = new IdentityManager(serverConfig);
        identityManagerServer.loadOrCreateIdentity();
        peersManager = new PeersManager();
        messenger = new Messenger();

        server = new NodeServer(identityManagerServer, messenger, peersManager, serverConfig, workerGroup, bossGroup);
        server.open();
    }

    @AfterEach
    void cleanUp(TestInfo info) throws IdentityManagerException {
        server.close();

        IdentityManager.deleteIdentityFile(config.getIdentityPath());
        workerGroup.shutdownGracefully().syncUninterruptibly();
        bossGroup.shutdownGracefully().syncUninterruptibly();
        TestHelper.colorizedPrintln("FINISHED " + info.getDisplayName(), AnsiColor.COLOR_CYAN, AnsiColor.STYLE_REVERSED);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldSendJoinMessageOnConnect() throws SuperPeerClientException, ExecutionException, InterruptedException {
        CompletableFuture<Message<?>> sentMessage = IntegrationTestHandler.sentMessages().firstElement().toCompletionStage().toCompletableFuture();
        CompletableFuture<List<Message<?>>> receivedMessages = IntegrationTestHandler.receivedMessages().take(5).toList().toCompletionStage().toCompletableFuture();
        // start client
        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, event -> {
        });
        client.open(server.getEntryPoints());
        sentMessage.join();

        assertThat(receivedMessages.get(), hasItem(instanceOf(JoinMessage.class)));
    }

    @Disabled("Muss noch implementiert werden")
    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldSendXXXMessageOnClientSideDisconnect() throws SuperPeerClientException, ExecutionException, InterruptedException {

    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldEmitNodeOfflineEventOnClientSideDisconnect() throws InterruptedException, SuperPeerClientException {
        CountDownLatch lock = new CountDownLatch(2);

        ReplaySubject<Event> subject = ReplaySubject.create();

        // start client
        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, subject::onNext);
        client.open(server.getEntryPoints());
        subject.subscribe(event -> {
            if (lock.getCount() == 2) {
                lock.countDown();
                assertEquals(EventCode.EVENT_NODE_ONLINE, event.getCode());
                client.close();
            }
            else if (lock.getCount() == 1) {
                lock.countDown();
                assertEquals(EventCode.EVENT_NODE_OFFLINE, event.getCode());
            }
        });

        lock.await(TIMEOUT, MILLISECONDS);
        assertEquals(0, lock.getCount());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldRespondToPingMessageWithPongMessage() throws SuperPeerClientException, ExecutionException, InterruptedException {
        CompletableFuture<Message<?>> sentMessage = IntegrationTestHandler.sentMessages().firstElement().toCompletionStage().toCompletableFuture();
        CompletableFuture<List<Message<?>>> receivedMessages = IntegrationTestHandler.receivedMessages().take(2).toList().toCompletionStage().toCompletableFuture();
        // start client
        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, event -> {
        });
        client.open(server.getEntryPoints());
        sentMessage.join();

        IntegrationTestHandler.injectMessage(new PingMessage());
        assertThat(receivedMessages.get(), hasItem(instanceOf(PongMessage.class)));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldRespondToApplicationMessageWithStatusOk() throws SuperPeerClientException, ExecutionException, InterruptedException {
        CompletableFuture<Message<?>> sentMessage = IntegrationTestHandler.sentMessages().firstElement().toCompletionStage().toCompletableFuture();
        CompletableFuture<List<Message<?>>> receivedMessages = IntegrationTestHandler.receivedMessages().take(5).toList().toCompletionStage().toCompletableFuture();
        // start client
        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, event -> {
        });
        client.open(server.getEntryPoints());

        sentMessage.join();

        IntegrationTestHandler.injectMessage(new ApplicationMessage(TestHelper.random(), identityManager.getIdentity(), new byte[]{
                0x00,
                0x01
        }));

        assertThat(receivedMessages.get(), hasItem(hasProperty("code", is(STATUS_OK))));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldEmitNodeOfflineEventAfterReceivingQuitMessage() throws SuperPeerClientException, InterruptedException, ExecutionException {
        CountDownLatch lock = new CountDownLatch(1);
        ReplaySubject<Event> subject = ReplaySubject.create();
        CompletableFuture<Message<?>> receivedMessage = IntegrationTestHandler.receivedMessages().firstElement().toCompletionStage().toCompletableFuture();
        // start client
        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, subject::onNext);
        client.open(server.getEntryPoints());
        subject.subscribe(event -> {
            if (event.getCode().equals(EventCode.EVENT_NODE_OFFLINE)) {
                lock.countDown();
            }
        });

        receivedMessage.join();

        IntegrationTestHandler.injectMessage(new QuitMessage());
        lock.await(TIMEOUT, MILLISECONDS);
        assertEquals(0, lock.getCount());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldEmitNodeOnlineEventAfterReceivingWelcomeMessage() throws InterruptedException, SuperPeerClientException {
        CountDownLatch lock = new CountDownLatch(1);
        // start client
        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, event -> {
            if (lock.getCount() == 1) {
                assertEquals(EventCode.EVENT_NODE_ONLINE, event.getCode());
                lock.countDown();
            }
        });
        client.open(server.getEntryPoints());

        lock.await(TIMEOUT, MILLISECONDS);
        assertEquals(0, lock.getCount());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldReconnectOnDisconnect() throws SuperPeerClientException, InterruptedException {
        CountDownLatch lock = new CountDownLatch(2);

        ReplaySubject<Event> subject = ReplaySubject.create();

        // start client
        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, subject::onNext);
        client.open(server.getEntryPoints());
        subject.subscribe(event -> {
            if (lock.getCount() > 0 && event.getCode().equals(EventCode.EVENT_NODE_ONLINE)) {
                lock.countDown();
            }

            if (lock.getCount() == 1) {
                client.close();
                client.open(server.getEntryPoints());
            }
        });

        lock.await(TIMEOUT, MILLISECONDS);
        assertEquals(0, lock.getCount());
    }
}
