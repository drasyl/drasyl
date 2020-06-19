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
import io.reactivex.rxjava3.core.Observable;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.crypto.Crypto;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.IdentityManager;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.testutils.IdentityRandomGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeServerTest {
    private IdentityManager identityManager;
    private Messenger messenger;
    private PeersManager peersManager;
    private DrasylNodeConfig config;
    private Channel serverChannel;
    private ServerBootstrap serverBootstrap;
    private EventLoopGroup workerGroup;
    private EventLoopGroup bossGroup;
    private ApplicationMessage message;
    private NodeServerChannelBootstrap nodeServerChannelBootstrap;
    private NodeServerChannelGroup channelGroup;
    private Map<CompressedPublicKey, CompressedPublicKey> grandchildren;
    private Observable<Boolean> superPeerConnected;

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
        Future future = mock(Future.class);
        nodeServerChannelBootstrap = mock(NodeServerChannelBootstrap.class);
        ChannelFuture channelFuture = mock(ChannelFuture.class);
        channelGroup = mock(NodeServerChannelGroup.class);
        grandchildren = Map.of();
        superPeerConnected = mock(Observable.class);

        message = mock(ApplicationMessage.class);
        String msgID = Crypto.randomString(16);
        CompressedPublicKey address1 = IdentityRandomGenerator.random();
        CompressedPublicKey address2 = IdentityRandomGenerator.random();

        when(serverBootstrap.group(any(), any())).thenReturn(serverBootstrap);
        when(serverBootstrap.channel(any())).thenReturn(serverBootstrap);
        when(serverBootstrap.handler(any())).thenReturn(serverBootstrap);
        when(serverBootstrap.childHandler(any())).thenReturn(serverBootstrap);
        when(serverBootstrap.childOption(any(), any())).thenReturn(serverBootstrap);
        when(bossGroup.shutdownGracefully()).thenReturn(future);
        when(workerGroup.shutdownGracefully()).thenReturn(future);
        when(config.getServerEndpoints()).thenReturn(Set.of(URI.create("ws://localhost:22527/")));
        when(nodeServerChannelBootstrap.getChannel()).thenReturn(serverChannel);
        when(serverChannel.closeFuture()).thenReturn(channelFuture);

        when(message.getSender()).thenReturn(address1);
        when(message.getRecipient()).thenReturn(address2);
        when(message.getId()).thenReturn(msgID);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void openShouldSetOpenToTrue() throws NodeServerException {
        when(serverChannel.localAddress()).thenReturn(new InetSocketAddress(22527));

        NodeServer server = new NodeServer(identityManager, messenger, peersManager,
                config, serverChannel, serverBootstrap, workerGroup, bossGroup,
                nodeServerChannelBootstrap, new AtomicBoolean(false), -1, new HashSet<>(), channelGroup, superPeerConnected);
        server.open();

        assertTrue(server.isOpen());
    }

    @Test
    void openShouldDoNothingIfServerHasAlreadyBeenStarted() throws NodeServerException {
        NodeServer server = new NodeServer(identityManager, messenger, peersManager,
                config, serverChannel, serverBootstrap, workerGroup, bossGroup,
                nodeServerChannelBootstrap, new AtomicBoolean(true), -1, new HashSet<>(), channelGroup, superPeerConnected);

        server.open();

        verify(nodeServerChannelBootstrap, times(0)).getChannel();
    }

    @Test
    void closeShouldDoNothingIfServerHasAlreadyBeenShutDown() {
        NodeServer server = new NodeServer(identityManager, messenger, peersManager,
                config, serverChannel, serverBootstrap, workerGroup, bossGroup,
                nodeServerChannelBootstrap, new AtomicBoolean(false), -1, new HashSet<>(), channelGroup, superPeerConnected);

        server.close();

        verify(bossGroup, times(0)).shutdownGracefully();
    }

    @Test
    void correctObjectCreation() throws DrasylException {
        NodeServer server = new NodeServer(identityManager, messenger, peersManager, workerGroup, bossGroup, superPeerConnected);

        assertNotNull(server.getBossGroup());
        assertNotNull(server.getConfig());
        assertNotNull(server.getWorkerGroup());
        assertNotNull(server.getPeersManager());
        assertNotNull(server.getEndpoints());
        assertFalse(server.isOpen());
    }
}
