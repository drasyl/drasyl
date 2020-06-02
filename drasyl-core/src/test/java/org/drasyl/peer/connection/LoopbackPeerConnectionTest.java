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
package org.drasyl.peer.connection;

import org.drasyl.crypto.Crypto;
import org.drasyl.event.Event;
import org.drasyl.identity.Address;
import org.drasyl.peer.connection.message.AbstractMessageWithUserAgent;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoopbackPeerConnectionTest {
    private Consumer<Event> onEvent;
    private Address address;
    private CompletableFuture<Boolean> closedCompletable;
    private AtomicBoolean isClosed;
    private ConnectionsManager connectionsManager;
    private PeerConnection.CloseReason reason;
    private Consumer<Event> eventConsumer;

    @BeforeEach
    void setUp() {
        onEvent = mock(Consumer.class);
        address = mock(Address.class);
        closedCompletable = mock(CompletableFuture.class);
        isClosed = mock(AtomicBoolean.class);
        connectionsManager = mock(ConnectionsManager.class);
        reason = PeerConnection.CloseReason.REASON_SHUTTING_DOWN;
        eventConsumer = mock(Consumer.class);
    }

    @Test
    void sendShouldTriggerEvent() {
        LoopbackPeerConnection con = new LoopbackPeerConnection(onEvent, address, closedCompletable, isClosed, connectionsManager);

        ApplicationMessage message = new ApplicationMessage(Address.of(Crypto.randomString(5)), Address.of(Crypto.randomString(5)), new byte[]{
                0x00,
                0x01
        });

        con.send(message);

        verify(onEvent).accept(any(Event.class));
    }

    @Test
    void sendShouldNotSendIfClosed() {
        ConnectionsManager connectionsManager = new ConnectionsManager(eventConsumer);
        LoopbackPeerConnection con = new LoopbackPeerConnection(onEvent, address, closedCompletable, isClosed, connectionsManager);

        ApplicationMessage message = new ApplicationMessage(Address.of(Crypto.randomString(5)), Address.of(Crypto.randomString(5)), new byte[]{
                0x00,
                0x01
        });

        connectionsManager.closeConnection(con, reason);
        con.send(message);

        verifyNoInteractions(onEvent);
        verify(closedCompletable).complete(true);
        assertTrue(con.isClosed.get());
        assertEquals(closedCompletable, con.isClosed());
    }

    @Test
    void shouldThrowExceptionIfMessageIsNotAnApplicationMessage() {
        LoopbackPeerConnection con = new LoopbackPeerConnection(onEvent, address, closedCompletable, isClosed, connectionsManager);
        JoinMessage joinMessage = mock(JoinMessage.class);

        assertThrows(IllegalArgumentException.class, () -> con.send(joinMessage));
        verifyNoInteractions(onEvent);
    }

    @Test
    void getterTest() {
        LoopbackPeerConnection con = new LoopbackPeerConnection(onEvent, address, connectionsManager);
        LoopbackPeerConnection con2 = new LoopbackPeerConnection(onEvent, address, connectionsManager);

        assertEquals(AbstractMessageWithUserAgent.userAgentGenerator.get(), con.getUserAgent());
        assertEquals(address, con.getAddress());
        assertEquals(con, con);
        assertNotEquals(con, con2);
        assertNotEquals(con, mock(LoopbackPeerConnection.class));
        assertEquals(con.hashCode(), con.hashCode());
    }
}