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
package org.drasyl.peer.connection.superpeer.handler;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofMillis;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SuperPeerClientConnectionHandlerTest {
    private EmbeddedChannel channel;
    private CompressedPublicKey expectedPublicKey;
    private CompressedPublicKey ownPublicKey;
    private PeersManager peersManager;
    private Messenger messenger;
    private QuitMessage quitMessage;
    private CompletableFuture<Void> handshakeFuture;
    private ScheduledFuture<?> timeoutFuture;
    private JoinMessage requestMessage;

    @BeforeEach
    void setUp() {
        messenger = mock(Messenger.class);
        handshakeFuture = mock(CompletableFuture.class);
        peersManager = mock(PeersManager.class);
        quitMessage = mock(QuitMessage.class);
        timeoutFuture = mock(ScheduledFuture.class);
        requestMessage = mock(JoinMessage.class);
        expectedPublicKey = mock(CompressedPublicKey.class);
        ownPublicKey = mock(CompressedPublicKey.class);
    }

    @Test
    void shouldReplyWithStatusOkAndThenCloseChannelIfHandshakeIsDone() {
        when(handshakeFuture.isDone()).thenReturn(true);
        when(quitMessage.getId()).thenReturn("123");

        SuperPeerClientConnectionHandler handler = new SuperPeerClientConnectionHandler(expectedPublicKey, ownPublicKey, peersManager, messenger, ofMillis(1000), handshakeFuture, timeoutFuture, requestMessage);
        channel = new EmbeddedChannel(handler);
        channel.readOutbound();
        channel.flush();

        channel.writeInbound(quitMessage);
        channel.flush();

        assertEquals(new StatusMessage(STATUS_OK, quitMessage.getId()), channel.readOutbound());
        assertFalse(channel.isOpen());
    }
}