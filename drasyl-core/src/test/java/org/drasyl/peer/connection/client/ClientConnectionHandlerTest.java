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

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.MessageId;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.drasyl.pipeline.Pipeline;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofMillis;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_SERVICE_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientConnectionHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ClientEnvironment environment;
    @Mock
    private Pipeline pipeline;
    @Mock
    private CompletableFuture<Void> handshakeFuture;
    @Mock
    private ScheduledFuture<?> timeoutFuture;
    @Mock
    private JoinMessage requestMessage;
    private EmbeddedChannel underTest;

    @Test
    void shouldCloseChannelOnQuitMessage(@Mock final QuitMessage quitMessage) {
        when(handshakeFuture.isDone()).thenReturn(true);

        final ClientConnectionHandler handler = new ClientConnectionHandler(environment, ofMillis(1000), pipeline, handshakeFuture, timeoutFuture, requestMessage);
        underTest = new EmbeddedChannel(handler);
        underTest.readOutbound(); // join message
        underTest.flush();

        underTest.writeInbound(quitMessage);
        underTest.flush();

        assertFalse(underTest.isOpen());
    }

    @Test
    void shouldFailHandshakeIfServerReplyWithStatusUnavailableOnWelcomeMessage(@Mock final StatusMessage statusMessage) {
        when(handshakeFuture.isDone()).thenReturn(false);
        when(requestMessage.getId()).thenReturn(MessageId.of("412176952b5b81fd13f84a7c"));
        when(statusMessage.getCorrespondingId()).thenReturn(MessageId.of("412176952b5b81fd13f84a7c"));
        when(statusMessage.getCode()).thenReturn(STATUS_SERVICE_UNAVAILABLE);

        final ClientConnectionHandler handler = new ClientConnectionHandler(environment, ofMillis(1000), pipeline, handshakeFuture, timeoutFuture, requestMessage);
        underTest = new EmbeddedChannel(handler);
        underTest.readOutbound(); // join message
        underTest.flush();

        underTest.writeInbound(statusMessage);

        verify(handshakeFuture).completeExceptionally(any());
    }

    @Nested
    class ServerAsSuperPeer {
        @Test
        void shouldAddPeerInformationAndSetSuperPeerAndEmitNodeOnlineEventOnSessionCreationAndRemovePeerInformationAndUnsetSuperPeer(
                @Mock(answer = RETURNS_DEEP_STUBS) final WelcomeMessage offerMessage,
                @Mock final CompressedPublicKey publicKey) {
            when(environment.joinAsChildren()).thenReturn(true);
            when(environment.getEndpoint().getPublicKey()).thenReturn(publicKey);
            when(environment.getConfig().getNetworkId()).thenReturn(1337);
            when(requestMessage.getId()).thenReturn(MessageId.of("412176952b5b81fd13f84a7c"));
            when(offerMessage.getCorrespondingId()).thenReturn(MessageId.of("412176952b5b81fd13f84a7c"));
            when(offerMessage.getId()).thenReturn(MessageId.of("78c36c82b8d11c7217a011b3"));
            when(offerMessage.getPublicKey()).thenReturn(publicKey);
            when(offerMessage.getProofOfWork().isValid(any(), anyShort())).thenReturn(true);
            when(offerMessage.getNetworkId()).thenReturn(1337);

            final ClientConnectionHandler handler = new ClientConnectionHandler(environment, ofMillis(1000), pipeline, handshakeFuture, timeoutFuture, requestMessage);
            underTest = new EmbeddedChannel(handler);

            underTest.writeInbound(offerMessage);
            underTest.flush();

            verify(environment.getPeersManager()).setPeerInformationAndAddPathAndSetSuperPeer(eq(publicKey), any(), any());
//        assertEquals(new StatusMessage(STATUS_OK, "78c36c82b8d11c7217a011b3"), channel.readOutbound());

            underTest.close();

            verify(environment.getPeersManager()).unsetSuperPeerAndRemovePath(any());
        }
    }

    @Nested
    class ServerAsPeer {
        @Test
        void shouldAddPeerInformationAndSetSuperPeerAndEmitNodeOnlineEventOnSessionCreationAndRemovePeerInformationAndUnsetSuperPeerAndEmitNodeOfflineEventOnClose(
                @Mock(answer = RETURNS_DEEP_STUBS) final WelcomeMessage offerMessage,
                @Mock final CompressedPublicKey publicKey) {
            when(environment.joinAsChildren()).thenReturn(false);
            when(environment.getEndpoint().getPublicKey()).thenReturn(publicKey);
            when(environment.getConfig().getNetworkId()).thenReturn(1337);
            when(requestMessage.getId()).thenReturn(MessageId.of("412176952b5b81fd13f84a7c"));
            when(offerMessage.getCorrespondingId()).thenReturn(MessageId.of("412176952b5b81fd13f84a7c"));
            when(offerMessage.getId()).thenReturn(MessageId.of("78c36c82b8d11c7217a011b3"));
            when(offerMessage.getPublicKey()).thenReturn(publicKey);
            when(offerMessage.getProofOfWork().isValid(any(), anyShort())).thenReturn(true);
            when(offerMessage.getNetworkId()).thenReturn(1337);

            final ClientConnectionHandler handler = new ClientConnectionHandler(environment, ofMillis(1000), pipeline, handshakeFuture, timeoutFuture, requestMessage);
            underTest = new EmbeddedChannel(handler);

            underTest.writeInbound(offerMessage);
            underTest.flush();

            verify(environment.getPeersManager()).setPeerInformationAndAddPath(eq(publicKey), any(), any());
//        assertEquals(new StatusMessage(STATUS_OK, "78c36c82b8d11c7217a011b3"), channel.readOutbound());

            underTest.close();

            verify(environment.getPeersManager()).removePath(eq(publicKey), any());
        }
    }
}
