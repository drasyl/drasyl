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
package org.drasyl.core.node.connections;

import org.drasyl.core.common.message.*;
import org.drasyl.core.models.Event;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityManager;
import org.drasyl.crypto.Crypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AutoreferentialPeerConnectionTest {
    private Consumer<Event> onEvent;
    private IdentityManager identityManager;
    private URI endpoint;
    private CompletableFuture<Boolean> closedCompletable;
    private AtomicBoolean isClosed;

    @BeforeEach
    void setUp() {
        onEvent = mock(Consumer.class);
        identityManager = mock(IdentityManager.class);
        endpoint = URI.create("ws://127.0.0.1:22527");
        closedCompletable = mock(CompletableFuture.class);
        isClosed = mock(AtomicBoolean.class);
    }

    @Test
    void sendShouldTriggerEvent() {
        AutoreferentialPeerConnection con = new AutoreferentialPeerConnection(onEvent, identityManager, endpoint, closedCompletable, isClosed);

        ApplicationMessage message = new ApplicationMessage(Identity.of(Crypto.randomString(5)), Identity.of(Crypto.randomString(5)), new byte[]{
                0x00,
                0x01
        });

        con.send(message);

        verify(onEvent).accept(any(Event.class));
    }

    @Test
    void sendShouldNotSendIfClosed() {
        AutoreferentialPeerConnection con = new AutoreferentialPeerConnection(onEvent, identityManager, endpoint, closedCompletable, isClosed);

        ApplicationMessage message = new ApplicationMessage(Identity.of(Crypto.randomString(5)), Identity.of(Crypto.randomString(5)), new byte[]{
                0x00,
                0x01
        });

        con.close();
        con.send(message);

        verifyNoInteractions(onEvent);
        verify(closedCompletable).complete(true);
        assertTrue(con.isClosed.get());
        assertEquals(closedCompletable, con.isClosed());
    }

    @Test
    void shouldThrowExceptionIfMessageIsNotAnApplicationMessage() {
        AutoreferentialPeerConnection con = new AutoreferentialPeerConnection(onEvent, identityManager, endpoint, closedCompletable, isClosed);

        assertThrows(IllegalArgumentException.class, () -> con.send(mock(JoinMessage.class), StatusMessage.class));
        verifyNoInteractions(onEvent);
    }

    @Test
    void shouldReturnStatusSingle() {
        AutoreferentialPeerConnection con = new AutoreferentialPeerConnection(onEvent, identityManager, endpoint, closedCompletable, isClosed);

        ApplicationMessage message = new ApplicationMessage(Identity.of(Crypto.randomString(5)), Identity.of(Crypto.randomString(5)), new byte[]{
                0x00,
                0x01
        });

        assertEquals(StatusMessage.Code.STATUS_OK, con.send(message, StatusMessage.class).blockingGet().getCode());
        verify(onEvent).accept(any(Event.class));
    }

    @Test
    void wrongReturnTypeShouldThrowError() {
        AutoreferentialPeerConnection con = new AutoreferentialPeerConnection(onEvent, identityManager, endpoint, closedCompletable, isClosed);

        assertThrows(IllegalArgumentException.class, () -> con.send(mock(ApplicationMessage.class), RejectMessage.class).blockingGet());

        verify(onEvent).accept(any(Event.class));
    }

    @Test
    void getterTest() {
        AutoreferentialPeerConnection con = new AutoreferentialPeerConnection(onEvent, identityManager, endpoint);

        assertEquals(AbstractMessageWithUserAgent.userAgentGenerator.get(), con.getUserAgent());
        assertEquals(endpoint, con.getEndpoint());
        assertEquals(identityManager.getIdentity(), con.getIdentity());
    }

    @Test
    void doNothingOnSetResponse() {
        AutoreferentialPeerConnection con = new AutoreferentialPeerConnection(onEvent, identityManager, endpoint, closedCompletable, isClosed);
        con.setResponse(mock(ResponseMessage.class));

        verifyNoInteractions(onEvent);
        verifyNoInteractions(identityManager);
        verifyNoInteractions(isClosed);
        verifyNoInteractions(closedCompletable);
    }
}