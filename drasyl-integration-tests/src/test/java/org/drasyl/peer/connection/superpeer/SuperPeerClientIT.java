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
import org.drasyl.peer.connection.server.NodeServer;
import org.junit.Ignore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import testutils.AnsiColor;
import testutils.TestHelper;

import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Execution(ExecutionMode.SAME_THREAD)
class SuperPeerClientIT {
    public static final long TIMEOUT = 10000L;
    private static EventLoopGroup workerGroup;
    private static EventLoopGroup bossGroup;
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

        TestHelper.colorizedPrintln("FINISHED " + info.getDisplayName(), AnsiColor.COLOR_CYAN, AnsiColor.STYLE_REVERSED);
    }

    @Ignore("Muss noch implementiert werden")
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldSendJoinMessageOnConnect() throws SuperPeerClientException {
        // start client
        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, event -> {
        });
        client.open(server.getEntryPoints());

        // TODO: check if JoinMessage has been received by server
    }

    @Ignore("Muss noch implementiert werden")
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldSendXXXMessageOnClientSideDisconnect() {

    }

    @Ignore("Muss noch implementiert werden")
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldRespondToPingMessageWithPongMessage() {

    }

    @Ignore("Muss noch implementiert werden")
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldRespondToApplicationMessageWithStatusOk() {

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

    @Ignore("Muss noch implementiert werden")
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldEmitNodeOfflineEventAfterReceivingQuitMessage() throws SuperPeerClientException, InterruptedException {

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
