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
import org.drasyl.event.Node;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofMillis;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_SERVICE_UNAVAILABLE;
import static org.drasyl.peer.connection.server.ServerChannelGroup.ATTRIBUTE_PUBLIC_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientConnectionHandlerTest {
    private EmbeddedChannel channel;
    @Mock
    private Messenger messenger;
    @Mock
    private QuitMessage quitMessage;
    @Mock
    private CompletableFuture<Void> handshakeFuture;
    @Mock
    private ScheduledFuture<?> timeoutFuture;
    @Mock
    private JoinMessage requestMessage;
    @Mock
    private StatusMessage statusMessage;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ClientEnvironment environment;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private WelcomeMessage offerMessage;
    @Mock
    private CompressedPublicKey publicKey;
    @Mock
    private PeersManager peersManager;

    @Test
    void shouldCloseChannelOnQuitMessage() {
        when(handshakeFuture.isDone()).thenReturn(true);

        ClientConnectionHandler handler = new ClientConnectionHandler(environment, ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage);
        channel = new EmbeddedChannel(handler);
        channel.readOutbound(); // join message
        channel.flush();

        channel.writeInbound(quitMessage);
        channel.flush();

        assertFalse(channel.isOpen());
    }

    @Test
    void shouldFailHandshakeIfServerReplyWithStatusUnavailableOnWelcomeMessage() {
        when(handshakeFuture.isDone()).thenReturn(false);
        when(requestMessage.getId()).thenReturn("123");
        when(statusMessage.getCorrespondingId()).thenReturn("123");
        when(statusMessage.getCode()).thenReturn(STATUS_SERVICE_UNAVAILABLE);

        ClientConnectionHandler handler = new ClientConnectionHandler(environment, ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage);
        channel = new EmbeddedChannel(handler);
        channel.readOutbound(); // join message
        channel.flush();

        channel.writeInbound(statusMessage);

        verify(handshakeFuture).completeExceptionally(any());
    }

    @Nested
    class ServerAsSuperPeer {
        @Test
        void shouldAddPeerInformationAndSetSuperPeerAndEmitNodeOnlineEventOnSessionCreationAndRemovePeerInformationAndUnsetSuperPeerAndEmitNodeOfflineEventOnClose() {
            when(requestMessage.getId()).thenReturn("123");
            when(offerMessage.getCorrespondingId()).thenReturn("123");
            when(offerMessage.getId()).thenReturn("456");
            when(environment.getPeersManager()).thenReturn(peersManager);
            when(environment.joinAsChildren()).thenReturn(true);

            ClientConnectionHandler handler = new ClientConnectionHandler(environment, ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage);
            channel = new EmbeddedChannel(handler);
            channel.attr(ATTRIBUTE_PUBLIC_KEY).set(publicKey);

            channel.writeInbound(offerMessage);
            channel.flush();

            verify(peersManager).addPeerInformationAndSetSuperPeer(eq(publicKey), any());
            verify(messenger).setSuperPeerSink(any());
            verify(environment.getConnected()).onNext(true);
            verify(environment.getEventConsumer()).accept(new NodeOnlineEvent(Node.of(environment.getIdentity())));
//        assertEquals(new StatusMessage(STATUS_OK, "456"), channel.readOutbound());

            channel.close();

            verify(peersManager).unsetSuperPeerAndRemovePeerInformation(any());
            verify(messenger).unsetSuperPeerSink();
            verify(environment.getConnected()).onNext(false);
            verify(environment.getEventConsumer()).accept(new NodeOfflineEvent(Node.of(environment.getIdentity())));
        }
    }

    @Nested
    class ServerAsPeer {
        @Test
        void shouldAddPeerInformationAndSetSuperPeerAndEmitNodeOnlineEventOnSessionCreationAndRemovePeerInformationAndUnsetSuperPeerAndEmitNodeOfflineEventOnClose() {
            when(requestMessage.getId()).thenReturn("123");
            when(offerMessage.getCorrespondingId()).thenReturn("123");
            when(offerMessage.getId()).thenReturn("456");
            when(environment.getPeersManager()).thenReturn(peersManager);
            when(environment.joinAsChildren()).thenReturn(false);

            ClientConnectionHandler handler = new ClientConnectionHandler(environment, ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage);
            channel = new EmbeddedChannel(handler);
            channel.attr(ATTRIBUTE_PUBLIC_KEY).set(publicKey);

            channel.writeInbound(offerMessage);
            channel.flush();

            verify(peersManager).addPeerInformation(eq(publicKey), any());
            verify(messenger).addClientSink(eq(publicKey), any());
            verify(environment.getConnected()).onNext(true);
//        assertEquals(new StatusMessage(STATUS_OK, "456"), channel.readOutbound());

            channel.close();

            verify(peersManager).removePeerInformation(eq(publicKey), any());
            verify(messenger).removeClientSink(publicKey);
            verify(environment.getConnected()).onNext(false);
        }
    }
}