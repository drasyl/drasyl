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
import org.drasyl.DrasylConfig;
import org.drasyl.util.NetworkUtil;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.drasyl.peer.connection.server.Server.determineActualEndpoints;
import static org.drasyl.util.NetworkUtil.createInetAddress;
import static org.drasyl.util.UriUtil.createUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock
    private Channel serverChannel;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ServerBootstrap serverBootstrap;
    @Mock
    private EventLoopGroup bossGroup;

    @Nested
    class Open {
        @Test
        void shouldSetOpenToTrue() throws ServerException {
            when(config.getServerBindHost()).thenReturn(createInetAddress("0.0.0.0"));
            when(config.getServerEndpoints()).thenReturn(Set.of(URI.create("ws://localhost:22527/")));
            when(serverBootstrap.bind(createInetAddress("0.0.0.0"), 0).isSuccess()).thenReturn(true);
            when(serverBootstrap.bind(createInetAddress("0.0.0.0"), 0).channel().localAddress()).thenReturn(new InetSocketAddress(22527));

            AtomicBoolean opened = new AtomicBoolean(false);
            try (Server server = new Server(
                    config, serverBootstrap, opened, -1, serverChannel,
                    new HashSet<>(), new HashSet<>())) {
                server.open();

                assertTrue(opened.get());
            }
        }

        @Test
        void shouldDoNothingIfServerHasAlreadyBeenStarted() throws ServerException {
            try (Server server = new Server(
                    config, serverBootstrap, new AtomicBoolean(true), -1, serverChannel,
                    new HashSet<>(), new HashSet<>())) {
                server.open();

                verify(serverBootstrap, never()).group(any(), any());
            }
        }
    }

    @Nested
    class Close {
        @Test
        void shouldDoNothingIfServerHasAlreadyBeenShutDown() {
            Server server = new Server(
                    config, serverBootstrap, new AtomicBoolean(false), -1, serverChannel,
                    new HashSet<>(), new HashSet<>());

            server.close();

            verify(bossGroup, times(0)).shutdownGracefully();
        }
    }

    @Nested
    class DetermineActualEndpoints {
        @Test
        void shouldReturnConfigEndpointsIfSpecified() {
            when(config.getServerEndpoints()).thenReturn(Set.of(URI.create("ws://foo.bar:22527")));

            assertEquals(
                    Set.of(URI.create("ws://foo.bar:22527")),
                    determineActualEndpoints(config, new InetSocketAddress(22527))
            );
        }

        @Test
        void shouldReturnEndpointForSpecificAddressesIfServerIsBoundToSpecificInterfaces() throws UnknownHostException {
            InetAddress firstAddress = NetworkUtil.getAddresses().iterator().next();
            if (firstAddress != null) {
                when(config.getServerEndpoints().isEmpty()).thenReturn(true);

                assertEquals(
                        Set.of(createUri("ws", firstAddress.getHostName(), 22527)),
                        determineActualEndpoints(config, new InetSocketAddress(firstAddress, 22527))
                );
            }
        }
    }
}
