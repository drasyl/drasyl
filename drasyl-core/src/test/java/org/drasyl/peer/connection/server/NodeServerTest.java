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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.crypto.Crypto;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityTestHelper;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.junit.Ignore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class NodeServerTest {
    private IdentityManager identityManager;
    private Messenger messenger;
    private PeersManager peersManager;
    private DrasylNodeConfig config;
    private Channel serverChannel;
    private ServerBootstrap serverBootstrap;
    private EventLoopGroup workerGroup;
    private EventLoopGroup bossGroup;
    private List<Runnable> beforeCloseListeners;
    private CompletableFuture<Void> startedFuture;
    private CompletableFuture<Void> stoppedFuture;
    private ApplicationMessage message;
    private NodeServerBootstrap nodeServerBootstrap;

    @BeforeEach
    void setUp() throws InterruptedException, NodeServerException {
        identityManager = mock(IdentityManager.class);
        messenger = mock(Messenger.class);
        peersManager = mock(PeersManager.class);
        config = mock(DrasylNodeConfig.class);
        serverChannel = mock(Channel.class);
        serverBootstrap = mock(ServerBootstrap.class);
        workerGroup = mock(EventLoopGroup.class);
        bossGroup = mock(EventLoopGroup.class);
        beforeCloseListeners = new ArrayList<>();
        startedFuture = new CompletableFuture<>();
        stoppedFuture = new CompletableFuture<>();
        Future future = mock(Future.class);
        nodeServerBootstrap = mock(NodeServerBootstrap.class);
        ChannelFuture channelFuture = mock(ChannelFuture.class);

        message = mock(ApplicationMessage.class);
        String msgID = Crypto.randomString(16);
        Identity identity1 = IdentityTestHelper.random();
        Identity identity2 = IdentityTestHelper.random();

        when(serverBootstrap.group(any(), any())).thenReturn(serverBootstrap);
        when(serverBootstrap.channel(any())).thenReturn(serverBootstrap);
        when(serverBootstrap.handler(any())).thenReturn(serverBootstrap);
        when(serverBootstrap.childHandler(any())).thenReturn(serverBootstrap);
        when(serverBootstrap.childOption(any(), any())).thenReturn(serverBootstrap);
        when(bossGroup.shutdownGracefully()).thenReturn(future);
        when(workerGroup.shutdownGracefully()).thenReturn(future);
        when(config.getServerEndpoints()).thenReturn(Set.of("ws://localhost:22527/"));
        when(nodeServerBootstrap.getChannel()).thenReturn(serverChannel);
        when(serverChannel.closeFuture()).thenReturn(channelFuture);

        when(message.getSender()).thenReturn(identity1);
        when(message.getRecipient()).thenReturn(identity2);
        when(message.getId()).thenReturn(msgID);
    }

    @AfterEach
    void tearDown() {
    }

    @Ignore("i'm unable to mock InetSocketAddress properly...")
    void openShouldSetOpenToTrue() throws NodeServerException {
        when(serverChannel.localAddress()).thenReturn(mock(InetSocketAddress.class));

        NodeServer server = new NodeServer(identityManager, messenger, peersManager,
                config, serverChannel, serverBootstrap, workerGroup, bossGroup,
                nodeServerBootstrap, new AtomicBoolean(false), -1, new HashSet<>());
        server.open();

        assertTrue(server.isOpen());
    }

    @Test
    void openShouldDoNothingIfServerHasAlreadyBeenStarted() throws NodeServerException {
        NodeServer server = new NodeServer(identityManager, messenger, peersManager,
                config, serverChannel, serverBootstrap, workerGroup, bossGroup,
                nodeServerBootstrap, new AtomicBoolean(true), -1, new HashSet<>());

        server.open();

        verify(nodeServerBootstrap, times(0)).getChannel();
    }

    @Test
    void closeShouldDoNothingIfServerHasAlreadyBeenShutDown() {
        NodeServer server = new NodeServer(identityManager, messenger, peersManager,
                config, serverChannel, serverBootstrap, workerGroup, bossGroup,
                nodeServerBootstrap, new AtomicBoolean(false), -1, new HashSet<>());

        server.close();

        verify(bossGroup, times(0)).shutdownGracefully();
    }

    @Test
    void correctObjectCreation() throws DrasylException {
        NodeServer server = new NodeServer(identityManager, messenger, peersManager, workerGroup, bossGroup);

        assertNotNull(server.getBossGroup());
        assertNotNull(server.getConfig());
        assertNotNull(server.getWorkerGroup());
        assertNotNull(server.getPeersManager());
        assertNotNull(server.getEntryPoints());
        assertFalse(server.isOpen());
    }
}
