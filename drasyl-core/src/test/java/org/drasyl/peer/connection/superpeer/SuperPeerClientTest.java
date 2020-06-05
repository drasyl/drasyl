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

import com.google.common.base.Function;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.IdentityManager;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.ConnectionsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.time.Duration.ofSeconds;
import static org.drasyl.peer.connection.PeerConnection.CloseReason.REASON_SHUTTING_DOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuperPeerClientTest {
    private DrasylNodeConfig config;
    private IdentityManager identityManager;
    private PeersManager peersManager;
    private Messenger messenger;
    private EventLoopGroup workerGroup;
    private Set<URI> endpoints;
    private AtomicBoolean opened;
    private AtomicInteger nextEndpointPointer;
    private AtomicInteger nextRetryDelayPointer;
    private Consumer<Event> onEvent;
    private Set<URI> entryPoints;
    private Function<Set<URI>, Thread> threadSupplier;
    private List<Duration> superPeerRetryDelays;
    private ConnectionsManager connectionsManager;
    private Channel clientChannel;

    @BeforeEach
    void setUp() {
        config = mock(DrasylNodeConfig.class);
        identityManager = mock(IdentityManager.class);
        peersManager = mock(PeersManager.class);
        messenger = mock(Messenger.class);
        workerGroup = mock(EventLoopGroup.class);
        endpoints = mock(Set.class);
        opened = mock(AtomicBoolean.class);
        nextEndpointPointer = mock(AtomicInteger.class);
        nextRetryDelayPointer = mock(AtomicInteger.class);
        onEvent = mock(Consumer.class);
        entryPoints = mock(Set.class);
        threadSupplier = mock(Function.class);
        superPeerRetryDelays = mock(List.class);
        connectionsManager = mock(ConnectionsManager.class);
        clientChannel = mock(Channel.class);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void openShouldCreateKeepConnectionAliveIfClientIsNotAlreadyOpen() {
        when(threadSupplier.apply(any())).thenReturn(mock(Thread.class));

        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, connectionsManager, endpoints, new AtomicBoolean(false), nextEndpointPointer, nextRetryDelayPointer, onEvent, clientChannel, threadSupplier);

        client.open(entryPoints);

        verify(threadSupplier).apply(entryPoints);
    }

    @Test
    void openShouldNotCreateKeepConnectionAliveIfClientIsAlreadyOpen() {
        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, connectionsManager, endpoints, new AtomicBoolean(true), nextEndpointPointer, nextRetryDelayPointer, onEvent, clientChannel, threadSupplier);

        client.open(entryPoints);

        verify(threadSupplier, never()).apply(any());
    }

    @Test
    void retryConnectionShouldReturnTrueIfClientIsOpenAndSuperPeerRetryDelaysIsNotEmpty() {
        when(config.getSuperPeerRetryDelays()).thenReturn(superPeerRetryDelays);
        when(superPeerRetryDelays.isEmpty()).thenReturn(false);
        when(superPeerRetryDelays.get(0)).thenReturn(ofSeconds(3));

        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, connectionsManager, endpoints, new AtomicBoolean(true), nextEndpointPointer, new AtomicInteger(), onEvent, clientChannel, threadSupplier);

        assertTrue(client.retryConnection());
    }

    @Test
    void retryConnectionShouldReturnFalseIfClientIsNotOpen() {
        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, connectionsManager, endpoints, new AtomicBoolean(false), nextEndpointPointer, nextRetryDelayPointer, onEvent, clientChannel, threadSupplier);

        assertFalse(client.retryConnection());
    }

    @Test
    void doRetryCycleShouldJumpToNextEntrypoint() {
        when(endpoints.size()).thenReturn(3);

        nextEndpointPointer = new AtomicInteger(0);

        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, connectionsManager, endpoints, opened, nextEndpointPointer, nextRetryDelayPointer, onEvent, clientChannel, threadSupplier);

        client.doRetryCycle();
        assertEquals(1, nextEndpointPointer.get());

        client.doRetryCycle();
        assertEquals(2, nextEndpointPointer.get());

        client.doRetryCycle();
        assertEquals(0, nextEndpointPointer.get());
    }

    @Test
    void doRetryCycleShouldJumpToNextRetryDelay() {
        when(endpoints.size()).thenReturn(3);
        when(config.getSuperPeerRetryDelays()).thenReturn(superPeerRetryDelays);
        when(superPeerRetryDelays.size()).thenReturn(3);

        nextRetryDelayPointer = new AtomicInteger(0);

        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, connectionsManager, endpoints, opened, nextEndpointPointer, nextRetryDelayPointer, onEvent, clientChannel, threadSupplier);

        client.doRetryCycle();
        assertEquals(1, nextRetryDelayPointer.get());

        client.doRetryCycle();
        assertEquals(2, nextRetryDelayPointer.get());

        client.doRetryCycle();
        assertEquals(2, nextRetryDelayPointer.get());
    }

    @Test
    void retryDelayShouldReturnCorrectDelay() {
        when(config.getSuperPeerRetryDelays()).thenReturn(superPeerRetryDelays);
        when(config.getSuperPeerRetryDelays().get(0)).thenReturn(ofSeconds(3));

        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, connectionsManager, endpoints, opened, nextEndpointPointer, new AtomicInteger(0), onEvent, clientChannel, threadSupplier);

        assertEquals(ofSeconds(3), client.retryDelay());
    }

    @Test
    void closeShouldCloseConnectionIfClientIsOpen() {
        when(clientChannel.isOpen()).thenReturn(true);
        when(clientChannel.close()).thenReturn(mock(ChannelFuture.class));

        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, connectionsManager, endpoints, new AtomicBoolean(true), nextEndpointPointer, nextRetryDelayPointer, onEvent, clientChannel, threadSupplier);

        client.close();

        verify(connectionsManager).closeConnectionsOfType(SuperPeerClientConnection.class, REASON_SHUTTING_DOWN);
        verify(clientChannel).close();
    }

    @Test
    void closeShouldNotCloseConnectionIfClientIsNotOpen() {
        when(clientChannel.isOpen()).thenReturn(true);
        when(clientChannel.close()).thenReturn(mock(ChannelFuture.class));

        SuperPeerClient client = new SuperPeerClient(config, identityManager, peersManager, messenger, workerGroup, connectionsManager, endpoints, new AtomicBoolean(false), nextEndpointPointer, nextRetryDelayPointer, onEvent, clientChannel, threadSupplier);

        client.close();

        verify(connectionsManager, never()).closeConnectionsOfType(any(), any());
        verify(clientChannel, never()).close();
    }
}