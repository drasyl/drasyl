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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectClientTest {
    @Mock
    private EventLoopGroup workerGroup;
    @Mock
    private AtomicInteger nextEndpointPointer;
    @Mock
    private AtomicInteger nextRetryDelayPointer;
    @Mock
    private DrasylFunction<Endpoint, Bootstrap, ClientException> bootstrapSupplier;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Channel channel;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Bootstrap bootstrap;
    @Mock
    private Supplier<Set<Endpoint>> endpointsSupplier;
    @Mock
    private BooleanSupplier directConnectionDemand;
    @Mock
    private Runnable onFailure;
    @Mock
    private List<Duration> retryDelays;

    @Nested
    class Open {
        @Test
        void shouldConnectIfClientIsNotAlreadyOpen() throws ClientException {
            when(bootstrapSupplier.apply(any())).thenReturn(bootstrap);
            when(endpointsSupplier.get()).thenReturn(Set.of(Endpoint.of("ws://localhost")));

            try (final DirectClient client = new DirectClient(retryDelays, workerGroup, endpointsSupplier, new AtomicBoolean(), () -> false, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, channel, directConnectionDemand, onFailure)) {
                client.open();

                verify(bootstrapSupplier).apply(any());
            }
        }

        @Test
        void shouldNotConnectIfClientIsAlreadyOpen() throws ClientException {
            try (final DirectClient client = new DirectClient(retryDelays, workerGroup, endpointsSupplier, new AtomicBoolean(true), () -> true, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, channel, directConnectionDemand, onFailure)) {
                client.open();

                verify(bootstrapSupplier, never()).apply(any());
            }
        }
    }

    @Nested
    class ConditionalScheduledReconnect {
        @Test
        void shouldScheduleReconnectIfClientIsOpenAndSuperPeerRetryDelaysIsNotEmptyAndDemandStillExists() {
            when(directConnectionDemand.getAsBoolean()).thenReturn(true);
            when(retryDelays.get(anyInt())).thenReturn(ofSeconds(1));

            try (final DirectClient client = new DirectClient(retryDelays, workerGroup, endpointsSupplier, new AtomicBoolean(true), () -> true, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, channel, directConnectionDemand, onFailure)) {
                client.conditionalScheduledReconnect();

                verify(workerGroup).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
            }
        }

        @Test
        void shouldNotReconnectIfClientIsOpenAndSuperPeerRetryDelaysIsNotEmptyAndNoDemandExists() {
            when(directConnectionDemand.getAsBoolean()).thenReturn(false);

            try (final DirectClient client = new DirectClient(retryDelays, workerGroup, endpointsSupplier, new AtomicBoolean(true), () -> true, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, channel, directConnectionDemand, onFailure)) {
                client.conditionalScheduledReconnect();

                verify(workerGroup, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
            }
        }
    }

    @Nested
    class Close {
        @Test
        void shouldSetOpenedToFalseIfClientIsOpen() {
            final AtomicBoolean opened = new AtomicBoolean(true);
            final DirectClient client = new DirectClient(retryDelays, workerGroup, endpointsSupplier, opened, () -> true, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, channel, directConnectionDemand, onFailure);

            client.close();

            assertFalse(opened.get());
        }

        @Test
        void shouldNotCloseConnectionIfClientIsNotOpen() {
            final DirectClient client = new DirectClient(retryDelays, workerGroup, endpointsSupplier, new AtomicBoolean(false), () -> false, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, channel, directConnectionDemand, onFailure);

            client.close();

            verify(channel, never()).writeAndFlush(new QuitMessage(REASON_SHUTTING_DOWN));
            verify(channel, never()).close();
        }
    }
}