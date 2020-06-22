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
package org.drasyl.peer.connection.superpeer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.util.DrasylFunction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperPeerClientTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private Supplier<Identity> identitySupplier;
    @Mock
    private PeersManager peersManager;
    @Mock
    private Messenger messenger;
    @Mock
    private EventLoopGroup workerGroup;
    @Mock
    private Set<URI> endpoints;
    @Mock
    private AtomicBoolean opened;
    @Mock
    private AtomicInteger nextEndpointPointer;
    @Mock
    private AtomicInteger nextRetryDelayPointer;
    @Mock
    private Consumer<Event> onEvent;
    @Mock
    private Supplier<Thread> threadSupplier;
    @Mock
    private List<Duration> superPeerRetryDelays;
    @Mock
    private Channel channel;
    @Mock
    private ChannelFuture channelFuture;
    @Mock
    private Subject<Boolean> connected;
    @Mock
    private Thread thread;
    @Mock
    private Supplier<Set<URI>> ownEndpoints;
    @Mock
    private DrasylFunction<URI, ChannelInitializer<SocketChannel>> channelInitializerSupplier;

    @Nested
    class Open {
        @Test
        void shouldCreateKeepConnectionAliveIfClientIsNotAlreadyOpen() {
            when(threadSupplier.get()).thenReturn(thread);

            SuperPeerClient client = new SuperPeerClient(config, identitySupplier, peersManager, messenger, workerGroup, onEvent, endpoints, new AtomicBoolean(false), nextEndpointPointer, nextRetryDelayPointer, threadSupplier, connected, channelInitializerSupplier, channel);

            client.open();

            verify(threadSupplier).get();
        }

        @Test
        void shouldNotCreateKeepConnectionAliveIfClientIsAlreadyOpen() {
            SuperPeerClient client = new SuperPeerClient(config, identitySupplier, peersManager, messenger, workerGroup, onEvent, endpoints, new AtomicBoolean(true), nextEndpointPointer, nextRetryDelayPointer, threadSupplier, connected, channelInitializerSupplier, channel);

            client.open();

            verify(threadSupplier, never()).get();
        }
    }

    @Nested
    class RetryConnection {
        @Test
        void shouldReturnTrueIfClientIsOpenAndSuperPeerRetryDelaysIsNotEmpty() {
            when(config.getSuperPeerRetryDelays()).thenReturn(superPeerRetryDelays);
            when(superPeerRetryDelays.isEmpty()).thenReturn(false);
            when(superPeerRetryDelays.get(0)).thenReturn(ofMillis(1));

            SuperPeerClient client = new SuperPeerClient(config, identitySupplier, peersManager, messenger, workerGroup, onEvent, endpoints, new AtomicBoolean(true), nextEndpointPointer, new AtomicInteger(), threadSupplier, connected, channelInitializerSupplier, channel);

            assertTrue(client.retryConnection());
        }

        @Test
        void shouldReturnFalseIfClientIsNotOpen() {
            SuperPeerClient client = new SuperPeerClient(config, identitySupplier, peersManager, messenger, workerGroup, onEvent, endpoints, new AtomicBoolean(false), nextEndpointPointer, nextRetryDelayPointer, threadSupplier, connected, channelInitializerSupplier, channel);

            assertFalse(client.retryConnection());
        }
    }

    @Nested
    class DoRetryCycle {
        @Test
        void shouldJumpToNextEndpoint() {
            when(endpoints.size()).thenReturn(3);

            nextEndpointPointer = new AtomicInteger(0);

            SuperPeerClient client = new SuperPeerClient(config, identitySupplier, peersManager, messenger, workerGroup, onEvent, endpoints, opened, nextEndpointPointer, nextRetryDelayPointer, threadSupplier, connected, channelInitializerSupplier, channel);

            client.doRetryCycle();
            assertEquals(1, nextEndpointPointer.get());

            client.doRetryCycle();
            assertEquals(2, nextEndpointPointer.get());

            client.doRetryCycle();
            assertEquals(0, nextEndpointPointer.get());
        }

        @Test
        void shouldJumpToNextRetryDelay() {
            when(endpoints.size()).thenReturn(3);
            when(config.getSuperPeerRetryDelays()).thenReturn(superPeerRetryDelays);
            when(superPeerRetryDelays.size()).thenReturn(3);

            nextRetryDelayPointer = new AtomicInteger(0);

            SuperPeerClient client = new SuperPeerClient(config, identitySupplier, peersManager, messenger, workerGroup, onEvent, endpoints, opened, nextEndpointPointer, nextRetryDelayPointer, threadSupplier, connected, channelInitializerSupplier, channel);

            client.doRetryCycle();
            assertEquals(1, nextRetryDelayPointer.get());

            client.doRetryCycle();
            assertEquals(2, nextRetryDelayPointer.get());

            client.doRetryCycle();
            assertEquals(2, nextRetryDelayPointer.get());
        }
    }

    @Nested
    class RetryDelay {
        @Test
        void shouldReturnCorrectDelay() {
            when(config.getSuperPeerRetryDelays()).thenReturn(superPeerRetryDelays);
            when(config.getSuperPeerRetryDelays().get(0)).thenReturn(ofSeconds(3));

            SuperPeerClient client = new SuperPeerClient(config, identitySupplier, peersManager, messenger, workerGroup, onEvent, endpoints, opened, nextEndpointPointer, new AtomicInteger(0), threadSupplier, connected, channelInitializerSupplier, channel);

            assertEquals(ofSeconds(3), client.retryDelay());
        }
    }

    @Nested
    class Close {
        @Test
        void shouldCloseConnectionIfClientIsOpen() {
            when(channel.isOpen()).thenReturn(true);
            when(channel.closeFuture()).thenReturn(channelFuture);
            when(channel.writeAndFlush(any())).thenReturn(channelFuture);

            SuperPeerClient client = new SuperPeerClient(config, identitySupplier, peersManager, messenger, workerGroup, onEvent, endpoints, new AtomicBoolean(true), nextEndpointPointer, nextRetryDelayPointer, threadSupplier, connected, channelInitializerSupplier, channel);

            client.close();

            verify(channel).writeAndFlush(new QuitMessage(REASON_SHUTTING_DOWN));
            verify(channel).closeFuture();
        }

        @Test
        void shouldNotCloseConnectionIfClientIsNotOpen() {
            SuperPeerClient client = new SuperPeerClient(config, identitySupplier, peersManager, messenger, workerGroup, onEvent, endpoints, new AtomicBoolean(false), nextEndpointPointer, nextRetryDelayPointer, threadSupplier, connected, channelInitializerSupplier, channel);

            client.close();

            verify(channel, never()).writeAndFlush(new QuitMessage(REASON_SHUTTING_DOWN));
            verify(channel, never()).close();
        }
    }
}