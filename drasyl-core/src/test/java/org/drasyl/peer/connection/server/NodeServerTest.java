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
import io.netty.channel.EventLoopGroup;
import io.reactivex.rxjava3.core.Observable;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.identity.IdentityManager;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServerTest {
    @Mock
    private IdentityManager identityManager;
    @Mock
    private Messenger messenger;
    @Mock
    private PeersManager peersManager;
    @Mock
    private DrasylNodeConfig config;
    @Mock
    private Channel serverChannel;
    @Mock
    private ServerBootstrap serverBootstrap;
    @Mock
    private EventLoopGroup workerGroup;
    @Mock
    private EventLoopGroup bossGroup;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private NodeServerChannelBootstrap channelBootstrap;
    @Mock
    private NodeServerChannelGroup channelGroup;
    @Mock
    private Observable<Boolean> superPeerConnected;

    @Nested
    class Open {
        @Test
        void shouldSetOpenToTrue() throws NodeServerException {
            when(config.getServerEndpoints()).thenReturn(Set.of(URI.create("ws://localhost:22527/")));
            when(channelBootstrap.getChannel().isSuccess()).thenReturn(true);
            when(channelBootstrap.getChannel().channel().localAddress()).thenReturn(new InetSocketAddress(22527));

            NodeServer server = new NodeServer(identityManager, messenger, peersManager,
                    config, serverChannel, serverBootstrap, workerGroup, bossGroup,
                    channelBootstrap, new AtomicBoolean(false), -1, new HashSet<>(), channelGroup, superPeerConnected);
            server.open();

            assertTrue(server.isOpen());
        }

        @Test
        void shouldDoNothingIfServerHasAlreadyBeenStarted() throws NodeServerException {
            NodeServer server = new NodeServer(identityManager, messenger, peersManager,
                    config, serverChannel, serverBootstrap, workerGroup, bossGroup,
                    channelBootstrap, new AtomicBoolean(true), -1, new HashSet<>(), channelGroup, superPeerConnected);

            server.open();

            verify(channelBootstrap, times(0)).getChannel();
        }
    }

    @Nested
    class Close {
        @Test
        void shouldDoNothingIfServerHasAlreadyBeenShutDown() {
            NodeServer server = new NodeServer(identityManager, messenger, peersManager,
                    config, serverChannel, serverBootstrap, workerGroup, bossGroup,
                    channelBootstrap, new AtomicBoolean(false), -1, new HashSet<>(), channelGroup, superPeerConnected);

            server.close();

            verify(bossGroup, times(0)).shutdownGracefully();
        }
    }

    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() throws DrasylException {
            NodeServer server = new NodeServer(identityManager, messenger, peersManager, workerGroup, bossGroup, superPeerConnected);

            assertNotNull(server.getBossGroup());
            assertNotNull(server.getWorkerGroup());
            assertNotNull(server.getPeersManager());
            assertNotNull(server.getEndpoints());
            assertFalse(server.isOpen());
        }
    }
}
