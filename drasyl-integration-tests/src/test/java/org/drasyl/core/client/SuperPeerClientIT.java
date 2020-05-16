package org.drasyl.core.client;

import com.typesafe.config.ConfigFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import org.drasyl.core.models.Code;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.models.Event;
import org.drasyl.core.node.DrasylNodeConfig;
import org.drasyl.core.node.Messenger;
import org.drasyl.core.node.PeersManager;
import org.drasyl.core.node.identity.IdentityManager;
import org.drasyl.core.node.identity.IdentityManagerException;
import org.drasyl.core.server.NodeServer;
import org.drasyl.core.server.testutils.ANSI_COLOR;
import org.drasyl.core.server.testutils.TestHelper;
import org.junit.Ignore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Execution(ExecutionMode.SAME_THREAD)
public class SuperPeerClientIT {
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
    public void setup(TestInfo info) throws DrasylException {
        TestHelper.println("STARTING " + info.getDisplayName(), ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

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
    public void cleanUp(TestInfo info) throws IdentityManagerException {
        server.close();

        IdentityManager.deleteIdentityFile(config.getIdentityPath());

        TestHelper.println("FINISHED " + info.getDisplayName(), ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
    }

    @Ignore
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void clientShouldSendJoinMessageOnConnect() throws SuperPeerClientException, InterruptedException {
        // start client
        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, event -> {
        });
        client.open(server.getEntryPoints());

        // TODO: check if JoinMessage has been received by server
    }

    @Ignore
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void clientShouldSendXXXMessageOnClientSideDisconnect() {

    }

    @Ignore
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void clientShouldRespondToPingMessageWithPongMessage() {

    }

    @Ignore
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void clientShouldRespondToApplicationMessageWithStatusOk() {

    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void clientShouldEmitNodeOfflineEventOnClientSideDisconnect() throws InterruptedException, SuperPeerClientException {
        CountDownLatch lock = new CountDownLatch(2);

        ReplaySubject<Event> subject = ReplaySubject.create();

        // start client
        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, subject::onNext);
        client.open(server.getEntryPoints());
        subject.subscribe(event -> {
            if (lock.getCount() == 2) {
                lock.countDown();
                assertEquals(Code.NODE_ONLINE, event.getCode());
                client.close();
            }
            else if (lock.getCount() == 1) {
                lock.countDown();
                assertEquals(Code.NODE_OFFLINE, event.getCode());
            }
        });

        lock.await(TIMEOUT, MILLISECONDS);
        assertEquals(0, lock.getCount());
    }

    @Ignore
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void clientShouldEmitNodeOfflineEventAfterReceivingQuitMessage() throws SuperPeerClientException, InterruptedException {

    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void clientShouldEmitNodeOnlineEventAfterReceivingWelcomeMessage() throws InterruptedException, SuperPeerClientException {
        CountDownLatch lock = new CountDownLatch(1);
        // start client
        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, event -> {
            if (lock.getCount() == 1) {
                assertEquals(Code.NODE_ONLINE, event.getCode());
                lock.countDown();
            }
        });
        client.open(server.getEntryPoints());

        lock.await(TIMEOUT, MILLISECONDS);
        assertEquals(0, lock.getCount());
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    public void clientShouldReconnectOnDisconnect() throws SuperPeerClientException, InterruptedException {
        CountDownLatch lock = new CountDownLatch(2);

        ReplaySubject<Event> subject = ReplaySubject.create();

        // start client
        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, subject::onNext);
        client.open(server.getEntryPoints());
        subject.subscribe(event -> {
            if (lock.getCount() > 0 && event.getCode().equals(Code.NODE_ONLINE)) {
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
