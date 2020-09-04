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
package org.drasyl.peer.connection.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import org.drasyl.DrasylConfig;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.util.DrasylFunction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.time.Duration.ofSeconds;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperPeerClientTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock
    private EventLoopGroup workerGroup;
    @Mock
    private Set<Endpoint> endpoints;
    @Mock
    private AtomicBoolean opened;
    @Mock
    private AtomicInteger nextEndpointPointer;
    @Mock
    private AtomicInteger nextRetryDelayPointer;
    @Mock
    private DrasylFunction<Endpoint, Bootstrap, ClientException> bootstrapSupplier;
    @Mock
    private List<Duration> superPeerRetryDelays;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Channel channel;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Bootstrap bootstrap;

    @Nested
    class Open {
        @Test
        void shouldConnectIfClientIsNotAlreadyOpen() throws ClientException {
            when(bootstrapSupplier.apply(any())).thenReturn(bootstrap);
            endpoints = Set.of(Endpoint.of("ws://localhost"));

            try (SuperPeerClient client = new SuperPeerClient(config, workerGroup, endpoints, new AtomicBoolean(false), () -> false, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, channel)) {
                client.open();

                verify(bootstrapSupplier).apply(any());
            }
        }

        @Test
        void shouldNotConnectIfClientIsAlreadyOpen() throws ClientException {
            try (SuperPeerClient client = new SuperPeerClient(config, workerGroup, endpoints, new AtomicBoolean(true), () -> false, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, channel)) {
                client.open();

                verify(bootstrapSupplier, never()).apply(any());
            }
        }
    }

    @Nested
    class ConditionalScheduledReconnect {
        @Test
        void shouldScheduleReconnectIfClientIsOpenAndSuperPeerRetryDelaysIsNotEmpty() {
            when(config.getSuperPeerRetryDelays()).thenReturn(List.of(ofSeconds(1)));

            try (SuperPeerClient client = new SuperPeerClient(config, workerGroup, endpoints, new AtomicBoolean(true), () -> true, nextEndpointPointer, new AtomicInteger(), bootstrapSupplier, channel)) {
                client.conditionalScheduledReconnect();

                verify(workerGroup).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
            }
        }

        @Test
        void shouldNotScheduleReconnectIfClientIsNotOpen() {
            try (SuperPeerClient client = new SuperPeerClient(config, workerGroup, endpoints, new AtomicBoolean(false), () -> false, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, channel)) {
                client.conditionalScheduledReconnect();

                verify(workerGroup, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
            }
        }
    }

    @Nested
    class NextEndpoint {
        @Test
        void shouldReturnCorrectEndpoint() {
            endpoints = new TreeSet<>();
            endpoints.add(Endpoint.of("ws://node1.org"));
            endpoints.add(Endpoint.of("ws://node2.org"));

            try (SuperPeerClient client = new SuperPeerClient(config, workerGroup, endpoints, opened, () -> false, new AtomicInteger(0), nextRetryDelayPointer, bootstrapSupplier, channel)) {
                assertEquals(Endpoint.of("ws://node1.org"), client.nextEndpoint());
                assertEquals(Endpoint.of("ws://node2.org"), client.nextEndpoint());
                assertEquals(Endpoint.of("ws://node1.org"), client.nextEndpoint());
            }
        }
    }

    @Nested
    class NextRetryDelay {
        @Test
        void shouldReturnCorrectDelay() {
            when(config.getSuperPeerRetryDelays()).thenReturn(superPeerRetryDelays);
            when(config.getSuperPeerRetryDelays()).thenReturn(List.of(ofSeconds(3), ofSeconds(6)));

            try (SuperPeerClient client = new SuperPeerClient(config, workerGroup, endpoints, opened, () -> false, nextEndpointPointer, new AtomicInteger(0), bootstrapSupplier, channel)) {
                assertEquals(ofSeconds(3), client.nextRetryDelay());
                assertEquals(ofSeconds(6), client.nextRetryDelay());
                assertEquals(ofSeconds(6), client.nextRetryDelay());
            }
        }
    }

    @Nested
    class Close {
        @Test
        void shouldSetOpenedToFalseIfClientIsOpen() {
            AtomicBoolean opened = new AtomicBoolean(true);
            SuperPeerClient client = new SuperPeerClient(config, workerGroup, endpoints, opened, () -> true, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, channel);

            client.close();

            assertFalse(opened.get());
        }

        @Test
        void shouldNotCloseConnectionIfClientIsNotOpen() {
            SuperPeerClient client = new SuperPeerClient(config, workerGroup, endpoints, new AtomicBoolean(false), () -> false, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, channel);

            client.close();

            verify(channel, never()).writeAndFlush(new QuitMessage(REASON_SHUTTING_DOWN));
            verify(channel, never()).close();
        }
    }
}