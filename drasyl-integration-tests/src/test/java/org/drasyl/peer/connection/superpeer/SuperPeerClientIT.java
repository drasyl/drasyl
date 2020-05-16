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
import testutils.AnsiColor;
import testutils.TestHelper;
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
    public void cleanUp(TestInfo info) throws IdentityManagerException {
        server.close();

        IdentityManager.deleteIdentityFile(config.getIdentityPath());

        TestHelper.colorizedPrintln("FINISHED " + info.getDisplayName(), AnsiColor.COLOR_CYAN, AnsiColor.STYLE_REVERSED);
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
    public void clientShouldReconnectOnDisconnect() throws SuperPeerClientException, InterruptedException {
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
