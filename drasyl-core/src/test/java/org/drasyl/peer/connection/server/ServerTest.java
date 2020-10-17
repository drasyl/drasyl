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
import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.Endpoint;
import org.drasyl.util.NetworkUtil;
import org.drasyl.util.PortMappingUtil;
import org.drasyl.util.PortMappingUtil.PortMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.drasyl.peer.connection.server.Server.determineActualEndpoints;
import static org.drasyl.util.NetworkUtil.createInetAddress;
import static org.drasyl.util.PortMappingUtil.Protocol.TCP;
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
    @Mock
    private Identity identity;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock
    private Channel serverChannel;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ServerBootstrap serverBootstrap;
    @Mock
    private EventLoopGroup bossGroup;
    @Mock
    private Scheduler scheduler;
    @Mock
    private final Function<InetSocketAddress, Set<PortMapping>> portExposer = address -> PortMappingUtil.expose(address, TCP);

    @Nested
    class Open {
        @Test
        void shouldSetOpenToTrue() throws ServerException {
            when(config.getServerBindHost()).thenReturn(createInetAddress("0.0.0.0"));
            when(config.getServerEndpoints()).thenReturn(Set.of(Endpoint.of("ws://localhost:22527/#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")));
            when(serverBootstrap.bind().isSuccess()).thenReturn(true);
            when(serverBootstrap.bind().channel().localAddress()).thenReturn(new InetSocketAddress(22527));

            final AtomicBoolean opened = new AtomicBoolean(false);
            try (final Server server = new Server(
                    identity, config, serverBootstrap, opened, null, serverChannel,
                    new HashSet<>(), new HashSet<>(), scheduler, portExposer)) {
                server.open();

                assertTrue(opened.get());
            }
        }

        @Test
        void shouldDoNothingIfServerHasAlreadyBeenStarted() throws ServerException {
            try (final Server server = new Server(
                    identity, config, serverBootstrap, new AtomicBoolean(true), null, serverChannel,
                    new HashSet<>(), new HashSet<>(), scheduler, portExposer)) {
                server.open();

                verify(serverBootstrap, never()).group(any(), any());
            }
        }

        @Test
        void shouldExposeServerWhenExposingIsEnabled() throws ServerException {
            when(config.isServerExposeEnabled()).thenReturn(true);
            when(config.getServerBindHost()).thenReturn(createInetAddress("0.0.0.0"));
            when(config.getServerEndpoints()).thenReturn(Set.of(Endpoint.of("ws://localhost:22527/#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")));
            when(serverBootstrap.bind().isSuccess()).thenReturn(true);
            when(serverBootstrap.bind().channel().localAddress()).thenReturn(new InetSocketAddress(22527));

            try (final Server server = new Server(
                    identity, config, serverBootstrap, new AtomicBoolean(), null, serverChannel,
                    new HashSet<>(), new HashSet<>(), scheduler, portExposer)) {
                server.open();

                verify(scheduler).scheduleDirect(any());
            }
        }

        @Test
        void shouldNotExposeServerWhenExposingIsDisabled() throws ServerException {
            when(config.isServerExposeEnabled()).thenReturn(false);
            when(config.getServerBindHost()).thenReturn(createInetAddress("0.0.0.0"));
            when(config.getServerEndpoints()).thenReturn(Set.of(Endpoint.of("ws://localhost:22527/#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")));
            when(serverBootstrap.bind().isSuccess()).thenReturn(true);
            when(serverBootstrap.bind().channel().localAddress()).thenReturn(new InetSocketAddress(22527));

            try (final Server server = new Server(
                    identity, config, serverBootstrap, new AtomicBoolean(), null, serverChannel,
                    new HashSet<>(), new HashSet<>(), scheduler, portExposer)) {
                server.open();

                verify(scheduler, never()).scheduleDirect(any());
            }
        }
    }

    @Nested
    class Close {
        @Test
        void shouldDoNothingIfServerHasAlreadyBeenShutDown() {
            final Server server = new Server(
                    identity, config, serverBootstrap, new AtomicBoolean(false), null, serverChannel,
                    new HashSet<>(), new HashSet<>(), scheduler, portExposer);

            server.close();

            verify(bossGroup, times(0)).shutdownGracefully();
        }
    }

    @Nested
    class ExposeEndpoints {
        @Mock
        private Set<PortMapping> mappings;

        @BeforeEach
        void setUp() {
            when(config.getServerBindHost()).thenReturn(createInetAddress("0.0.0.0"));
            when(config.getServerEndpoints()).thenReturn(Set.of(Endpoint.of("ws://localhost:22527/#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")));
            when(serverBootstrap.bind(createInetAddress("0.0.0.0"), 0).channel().localAddress()).thenReturn(new InetSocketAddress(22527));
            when(scheduler.scheduleDirect(any())).then(invocation -> {
                final Runnable argument = invocation.getArgument(0, Runnable.class);
                argument.run();
                return null;
            });
            when(portExposer.apply(any())).thenReturn(mappings);
        }

        @Test
        void shouldExposeEndpoints() {
            final InetSocketAddress address = new InetSocketAddress(22527);
            try (final Server server = new Server(identity, config, serverBootstrap, new AtomicBoolean(), null, serverChannel,
                    new HashSet<>(), new HashSet<>(), scheduler, portExposer)) {
                server.exposeEndpoints(address);

                verify(portExposer).apply(address);
            }
        }
    }

    @Nested
    class DetermineActualEndpoints {
        @Test
        void shouldReturnConfigEndpointsIfSpecified() {
            when(config.getServerEndpoints()).thenReturn(Set.of(Endpoint.of("ws://foo.bar:22527#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")));

            assertEquals(
                    Set.of(Endpoint.of("ws://foo.bar:22527#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")),
                    determineActualEndpoints(identity, config, new InetSocketAddress(22527))
            );
        }

        @Test
        void shouldReturnEndpointForSpecificAddressesIfServerIsBoundToSpecificInterfaces() throws CryptoException {
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"));

            final InetAddress firstAddress = NetworkUtil.getAddresses().iterator().next();
            if (firstAddress != null) {
                when(config.getServerEndpoints().isEmpty()).thenReturn(true);

                assertEquals(
                        Set.of(Endpoint.of(createUri("ws", firstAddress.getHostAddress(), 22527), CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))),
                        determineActualEndpoints(identity, config, new InetSocketAddress(firstAddress, 22527))
                );
            }
        }
    }
}