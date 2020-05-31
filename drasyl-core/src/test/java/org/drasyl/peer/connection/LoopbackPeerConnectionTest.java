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
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoopbackPeerConnectionTest {
    private Consumer<Event> onEvent;
    private Identity identity;
    private CompletableFuture<Boolean> closedCompletable;
    private AtomicBoolean isClosed;
    private ConnectionsManager connectionsManager;
    private PeerConnection.CloseReason reason;

    @BeforeEach
    void setUp() {
        onEvent = mock(Consumer.class);
        identity = mock(Identity.class);
        closedCompletable = mock(CompletableFuture.class);
        isClosed = mock(AtomicBoolean.class);
        connectionsManager = mock(ConnectionsManager.class);
        reason = PeerConnection.CloseReason.REASON_SHUTTING_DOWN;
    }

    @Test
    void sendShouldTriggerEvent() {
        LoopbackPeerConnection con = new LoopbackPeerConnection(onEvent, identity, closedCompletable, isClosed, connectionsManager);

        ApplicationMessage message = new ApplicationMessage(Identity.of(Crypto.randomString(5)), Identity.of(Crypto.randomString(5)), new byte[]{
                0x00,
                0x01
        });

        con.send(message);

        verify(onEvent).accept(any(Event.class));
    }

    @Test
    void sendShouldNotSendIfClosed() {
        ConnectionsManager connectionsManager = new ConnectionsManager();
        LoopbackPeerConnection con = new LoopbackPeerConnection(onEvent, identity, closedCompletable, isClosed, connectionsManager);

        ApplicationMessage message = new ApplicationMessage(Identity.of(Crypto.randomString(5)), Identity.of(Crypto.randomString(5)), new byte[]{
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
        LoopbackPeerConnection con = new LoopbackPeerConnection(onEvent, identity, closedCompletable, isClosed, connectionsManager);
        JoinMessage joinMessage = mock(JoinMessage.class);

        assertThrows(IllegalArgumentException.class, () -> con.send(joinMessage));
        verifyNoInteractions(onEvent);
    }

    @Test
    void getterTest() {
        LoopbackPeerConnection con = new LoopbackPeerConnection(onEvent, identity, connectionsManager);
        LoopbackPeerConnection con2 = new LoopbackPeerConnection(onEvent, identity, connectionsManager);

        assertEquals(AbstractMessageWithUserAgent.userAgentGenerator.get(), con.getUserAgent());
        assertEquals(identity, con.getIdentity());
        assertEquals(con, con);
        assertNotEquals(con, con2);
        assertNotEquals(con, mock(LoopbackPeerConnection.class));
        assertEquals(con.hashCode(), con.hashCode());
    }
}