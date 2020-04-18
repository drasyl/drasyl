/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package city.sane.relay.server;

import city.sane.relay.common.messages.ForwardableMessage;
import city.sane.relay.common.models.SessionUID;
import city.sane.relay.common.util.random.RandomUtil;
import city.sane.relay.server.session.Session;
import city.sane.relay.server.session.util.AutoDeletionBucket;
import city.sane.relay.server.session.util.ClientSessionBucket;
import city.sane.relay.server.session.util.MessageBucket;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RelayServerTest {
    private AutoDeletionBucket<SessionUID> newClientsBucket;
    private ClientSessionBucket clientSessionBucket;
    private MessageBucket messageBucket;
    private RelayServerConfig config;
    private boolean running;
    private long bootTime;
    private Channel serverChannel;
    private ServerBootstrap serverBootstrap;
    private EventLoopGroup workerGroup;
    private EventLoopGroup bossGroup;
    private AutoDeletionBucket deadClientsBucket;
    private List<Runnable> beforeCloseListeners;
    private CompletableFuture<Void> startedFuture;
    private CompletableFuture<Void> stoppedFuture;
    private ForwardableMessage message;
    private SessionUID clientUID1;
    private Session client1;
    private Map<SessionUID, Session> localClientSessions;
    private SessionUID clientUID2;
    private Session client2;
    private RelayBootstrap relayBootstrap;

    @BeforeEach
    void setUp() throws InterruptedException, URISyntaxException {
        newClientsBucket = mock(AutoDeletionBucket.class);
        clientSessionBucket = mock(ClientSessionBucket.class);
        messageBucket = mock(MessageBucket.class);
        config = mock(RelayServerConfig.class);
        running = false;
        bootTime = 1;
        serverChannel = mock(Channel.class);
        serverBootstrap = mock(ServerBootstrap.class);
        workerGroup = mock(EventLoopGroup.class);
        bossGroup = mock(EventLoopGroup.class);
        deadClientsBucket = mock(AutoDeletionBucket.class);
        beforeCloseListeners = new ArrayList<>();
        startedFuture = new CompletableFuture<>();
        stoppedFuture = new CompletableFuture<>();
        Future future = mock(Future.class);
        relayBootstrap = mock(RelayBootstrap.class);
        ChannelFuture channelFuture = mock(ChannelFuture.class);

        message = mock(ForwardableMessage.class);
        String msgID = RandomUtil.randomString(16);
        clientUID1 = SessionUID.random();
        client1 = mock(Session.class);
        clientUID2 = SessionUID.random();
        client2 = mock(Session.class);
        localClientSessions = new HashMap<>();

        when(serverBootstrap.group(any(), any())).thenReturn(serverBootstrap);
        when(serverBootstrap.channel(any())).thenReturn(serverBootstrap);
        when(serverBootstrap.handler(any())).thenReturn(serverBootstrap);
        when(serverBootstrap.childHandler(any())).thenReturn(serverBootstrap);
        when(serverBootstrap.childOption(any(), any())).thenReturn(serverBootstrap);
        when(bossGroup.shutdownGracefully()).thenReturn(future);
        when(workerGroup.shutdownGracefully()).thenReturn(future);
        when(config.getRelayEntrypoint()).thenReturn(new URI("ws://localhost:22527/"));
        when(relayBootstrap.getChannel()).thenReturn(serverChannel);
        when(serverChannel.closeFuture()).thenReturn(channelFuture);

        when(message.getSenderUID()).thenReturn(clientUID1);
        when(message.getReceiverUID()).thenReturn(clientUID2);
        when(message.getMessageID()).thenReturn(msgID);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void runShouldSetRunningToTrue() throws RelayServerException {
        RelayServer server = new RelayServer(newClientsBucket, clientSessionBucket, messageBucket, config, bootTime,
                serverChannel, serverBootstrap, workerGroup, bossGroup, deadClientsBucket, beforeCloseListeners,
                startedFuture, stoppedFuture, relayBootstrap, running);
        server.open();

        assertTrue(server.getStarted());
    }

    @Test
    void runShouldHandleDuplicateCalls() throws RelayServerException {
        RelayServer server = new RelayServer(newClientsBucket, clientSessionBucket, messageBucket, config, bootTime,
                serverChannel, serverBootstrap, workerGroup, bossGroup, deadClientsBucket, beforeCloseListeners,
                startedFuture, stoppedFuture, relayBootstrap, false);
        server.open();

        assertThrows(RelayServerException.class, server::open);
    }

    @Test
    void runShouldNotifyAboutSuccessfulStart() {
        RelayServer server = new RelayServer(newClientsBucket, clientSessionBucket, messageBucket, config, bootTime,
                serverChannel, serverBootstrap, workerGroup, bossGroup, deadClientsBucket, beforeCloseListeners,
                startedFuture, stoppedFuture, relayBootstrap, false);
        server.openServerChannel();

        assertTrue(server.getStartedFuture().isDone());
    }

    @Test
    void runShouldNotifyAboutFailedStart() throws InterruptedException {
        RelayServer server = new RelayServer(newClientsBucket, clientSessionBucket, messageBucket, config, bootTime,
                serverChannel, serverBootstrap, workerGroup, bossGroup, deadClientsBucket, beforeCloseListeners,
                startedFuture, stoppedFuture, relayBootstrap, false);

        when(relayBootstrap.getChannel()).thenThrow(new InterruptedException());
        server.openServerChannel();

        assertTrue(server.getStartedFuture().isCompletedExceptionally());
    }

    @Test
    void broadcastMessageLocallyShouldForwardMessageToEveryClient() {
        localClientSessions.put(clientUID1, client1);
        localClientSessions.put(clientUID2, client2);

        when(clientSessionBucket.getLocalClientUIDs()).thenReturn(Set.of(clientUID1, clientUID2));
        when(clientSessionBucket.getLocalClientSessions()).thenReturn(Set.of(client1, client2));

        RelayServer server = new RelayServer(newClientsBucket, clientSessionBucket, messageBucket, config, bootTime,
                serverChannel, serverBootstrap, workerGroup, bossGroup, deadClientsBucket, beforeCloseListeners,
                startedFuture, stoppedFuture, relayBootstrap, false);

        server.broadcastMessageLocally(message, localClientSessions);

        verify(client1, times(1)).sendMessage(new ForwardableMessage(message.getSenderUID(), clientUID1, message.getBlob()));
        verify(client2, times(1)).sendMessage(new ForwardableMessage(message.getSenderUID(), clientUID2, message.getBlob()));
    }

    @Test
    void forwardMessageShouldForwardMessageToGivenClient() {
        localClientSessions.put(clientUID1, client1);

        when(clientSessionBucket.getLocalClientUIDs()).thenReturn(Set.of(clientUID1));
        when(clientSessionBucket.getLocalClientSessions()).thenReturn(Set.of(client1));

        RelayServer server = new RelayServer(newClientsBucket, clientSessionBucket, messageBucket, config, bootTime,
                serverChannel, serverBootstrap, workerGroup, bossGroup, deadClientsBucket, beforeCloseListeners,
                startedFuture, stoppedFuture, relayBootstrap, false);

        server.forwardMessage(message, clientUID1, client1);

        verify(client1, times(1)).sendMessage(new ForwardableMessage(message.getSenderUID(), clientUID1, message.getBlob()));
    }

    @Test
    void getStoppedFutureShouldNotifyAboutStop() {
        RelayServer server = new RelayServer(newClientsBucket, clientSessionBucket, messageBucket, config, bootTime,
                serverChannel, serverBootstrap, workerGroup, bossGroup, deadClientsBucket, beforeCloseListeners,
                startedFuture, stoppedFuture, relayBootstrap, false);
        server.close();

        assertTrue(server.getStoppedFuture().isDone());
    }

    @Test
    void correctObjectCreation() throws RelayServerException, URISyntaxException {
        RelayServer server = new RelayServer();

        assertNotNull(server.getMessageBucket());
        assertNotNull(server.getBootTime());
        assertNotNull(server.getBossGroup());
        assertNotNull(server.getConfig());
        assertNotNull(server.getWorkerGroup());
        assertNotNull(server.getClientBucket());
        assertNotNull(server.getDeadClientsBucket());
        assertNotNull(server.getStartedFuture());
        assertNotNull(server.getStoppedFuture());
        assertNotNull(server.getUID());
        assertNotNull(server.getEntrypoint());
        assertNotNull(server.getNewClientsBucket());
        assertFalse(server.getStarted());
    }
}