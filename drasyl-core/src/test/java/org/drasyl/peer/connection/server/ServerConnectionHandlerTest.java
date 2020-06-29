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
package org.drasyl.peer.connection.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.Path;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.RegisterGrandchildMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.peer.connection.message.UnregisterGrandchildMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofMillis;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_HANDSHAKE_TIMEOUT;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_IDENTITY_COLLISION;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_FORBIDDEN;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.drasyl.peer.connection.server.ServerChannelGroup.ATTRIBUTE_PUBLIC_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerConnectionHandlerTest {
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private ScheduledFuture<?> timeoutFuture;
    @Mock
    private ChannelFuture channelFuture;
    @Mock
    private Throwable cause;
    @Mock
    private ApplicationMessage applicationMessage;
    @Mock
    private JoinMessage joinMessage;
    @Mock
    private Messenger messenger;
    @Mock
    private CompletableFuture<Void> handshakeFuture;
    @Mock
    private JoinMessage requestMessage;
    @Mock
    private CompressedPublicKey publicKey0;
    @Mock
    private PeersManager peersManager;
    @Mock
    private QuitMessage quitMessage;
    @Mock
    private ChannelId channelId;
    @Mock
    private Channel nettyChannel;
    @Mock
    private ServerChannelGroup channelGroup;
    @Mock
    private RegisterGrandchildMessage registerGrandchildMessage;
    @Mock
    private CompressedPublicKey grandchildrenPublicKey0;
    @Mock
    private UnregisterGrandchildMessage unregisterGrandchildrenMessage;
    @Mock
    private CompressedPublicKey superPeerPublicKey;
    @Mock
    private PeerInformation superPeerInformation;
    @Mock
    private Path superPeerPath;
    @Mock
    private StatusMessage statusMessage;
    @Mock
    private WelcomeMessage offerMessage;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ServerEnvironment environment;

    @Test
    void shouldSendExceptionMessageIfHandshakeIsNotDoneInTime() {
        ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(0), messenger, handshakeFuture, null, requestMessage, offerMessage);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        assertEquals(new ConnectionExceptionMessage(CONNECTION_ERROR_HANDSHAKE_TIMEOUT), channel.readOutbound());
    }

    @Test
    void closeBeforeTimeoutShouldNotSendHandshakeTimeoutExceptionMessage() {
        ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.close();

        assertNull(channel.readOutbound());
    }

    @Test
    void shouldRejectIncomingJoinMessageWithSamePublicKey() {
        when(environment.getIdentity().getPublicKey()).thenReturn(publicKey0);
        when(joinMessage.getPublicKey()).thenReturn(publicKey0);

        ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), messenger, handshakeFuture, timeoutFuture, null, offerMessage);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(joinMessage);
        channel.flush();

        assertEquals(new ConnectionExceptionMessage(CONNECTION_ERROR_IDENTITY_COLLISION), channel.readOutbound());
    }

    @Test
    void shouldRejectUnexpectedMessagesDuringHandshake() {
        when(applicationMessage.getId()).thenReturn("123");

        ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(applicationMessage);

        assertEquals(new StatusMessage(STATUS_FORBIDDEN, "123"), channel.readOutbound());
        assertNull(channel.readInbound());
    }

    @Test
    void exceptionCaughtShouldWriteExceptionToChannelAndThenCloseIt() {
        when(ctx.writeAndFlush(any(Message.class))).thenReturn(channelFuture);
        when(ctx.channel()).thenReturn(nettyChannel);
        when(nettyChannel.id()).thenReturn(channelId);

        ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
        handler.exceptionCaught(ctx, cause);

        verify(ctx).writeAndFlush(any(ConnectionExceptionMessage.class));
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
    }

    @Test
    void shouldCloseChannelOnQuitMessage() {
        when(handshakeFuture.isDone()).thenReturn(true);

        ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(quitMessage);
        channel.flush();

        assertFalse(channel.isOpen());
    }

    @Nested
    class ClientAsChildren {
        @Test
        void shouldAddGrandchildRouteAndInformSuperPeerOnRegisterGrandchildMessageAndRemoveGrandchildRouteAndInformSuperPeerOnClose() {
            when(environment.getPeersManager()).thenReturn(peersManager);
            when(handshakeFuture.isDone()).thenReturn(true);
            when(registerGrandchildMessage.getGrandchildren()).thenReturn(Set.of(grandchildrenPublicKey0));
            when(peersManager.getSuperPeer()).thenReturn(Pair.of(superPeerPublicKey, superPeerInformation));
            when(superPeerInformation.getPaths()).thenReturn(Set.of(superPeerPath));

            ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
            EmbeddedChannel channel = new EmbeddedChannel(handler);
            channel.attr(ATTRIBUTE_PUBLIC_KEY).set(publicKey0);

            channel.writeInbound(registerGrandchildMessage);
            channel.flush();

            // update peers manager
            verify(peersManager).addGrandchildrenRoute(grandchildrenPublicKey0, publicKey0);

            // inform super peer
            verify(superPeerPath).send(new RegisterGrandchildMessage(Set.of(grandchildrenPublicKey0)));

            channel.close();

            // update peers manager
            verify(peersManager).removeGrandchildrenRoute(grandchildrenPublicKey0);

            // inform super peer
            verify(superPeerPath).send(new UnregisterGrandchildMessage(Set.of(grandchildrenPublicKey0)));
        }

        @Test
        void shouldRemoveGrandchildRouteAndInformSuperPeerOnUnregisterGrandchildMessage() {
            when(environment.getPeersManager()).thenReturn(peersManager);
            when(handshakeFuture.isDone()).thenReturn(true);
            when(unregisterGrandchildrenMessage.getGrandchildren()).thenReturn(Set.of(grandchildrenPublicKey0));
            when(peersManager.getSuperPeer()).thenReturn(Pair.of(superPeerPublicKey, superPeerInformation));
            when(superPeerInformation.getPaths()).thenReturn(Set.of(superPeerPath));

            ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
            EmbeddedChannel channel = new EmbeddedChannel(handler);
            channel.attr(ATTRIBUTE_PUBLIC_KEY).set(publicKey0);

            channel.writeInbound(unregisterGrandchildrenMessage);
            channel.flush();

            // update peers manager
            verify(peersManager).removeGrandchildrenRoute(grandchildrenPublicKey0);

            // inform super peer
            verify(superPeerPath).send(new UnregisterGrandchildMessage(Set.of(grandchildrenPublicKey0)));
        }

        @Test
        void shouldAddPeerInformationAndGrandchildRouteAndInformSuperPeerOnSessionCreationAndRemovePeerInformationAndGrandchildRouteAndInformSuperPeerOnClose() {
            when(environment.getPeersManager()).thenReturn(peersManager);
            when(environment.getChannelGroup()).thenReturn(channelGroup);
            when(offerMessage.getId()).thenReturn("123");
            when(requestMessage.getPublicKey()).thenReturn(publicKey0);
            when(requestMessage.getChildrenAndGrandchildren()).thenReturn(Set.of(grandchildrenPublicKey0));
            when(requestMessage.isChildrenJoin()).thenReturn(true);
            when(statusMessage.getCorrespondingId()).thenReturn("123");
            when(statusMessage.getCode()).thenReturn(STATUS_OK);
            when(peersManager.getSuperPeer()).thenReturn(Pair.of(superPeerPublicKey, superPeerInformation));
            when(superPeerInformation.getPaths()).thenReturn(Set.of(superPeerPath));

            ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
            EmbeddedChannel channel = new EmbeddedChannel(handler);
            channel.attr(ATTRIBUTE_PUBLIC_KEY).set(publicKey0);

            channel.writeInbound(statusMessage);
            channel.flush();

            Set<CompressedPublicKey> childrenAndGrandchildren = Set.of(publicKey0, grandchildrenPublicKey0);

            // update peers manager
            verify(peersManager).addPeerInformationAndAddChildren(eq(publicKey0), any());
            verify(peersManager).addGrandchildrenRoute(grandchildrenPublicKey0, publicKey0);

            // inform super peer
            verify(superPeerPath).send(new RegisterGrandchildMessage(childrenAndGrandchildren));

            channel.close();

            // update peers manager
            verify(peersManager).removeChildrenAndRemovePeerInformation(eq(publicKey0), any());
            verify(peersManager).removeGrandchildrenRoute(grandchildrenPublicKey0);

            // inform super peer
            verify(superPeerPath).send(new UnregisterGrandchildMessage(childrenAndGrandchildren));
        }
    }

    @Nested
    class ClientAsPeer {
        @Test
        void shouldAddPeerInformationOnSessionCreationAndRemovePeerInformationOnClose() {
            when(environment.getPeersManager()).thenReturn(peersManager);
            when(environment.getChannelGroup()).thenReturn(channelGroup);
            when(offerMessage.getId()).thenReturn("123");
            when(requestMessage.getPublicKey()).thenReturn(publicKey0);
            when(requestMessage.isChildrenJoin()).thenReturn(false);
            when(statusMessage.getCorrespondingId()).thenReturn("123");
            when(statusMessage.getCode()).thenReturn(STATUS_OK);

            ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
            EmbeddedChannel channel = new EmbeddedChannel(handler);
            channel.attr(ATTRIBUTE_PUBLIC_KEY).set(publicKey0);

            channel.writeInbound(statusMessage);
            channel.flush();

            // update peers manager
            verify(peersManager).addPeerInformation(eq(publicKey0), any());

            channel.close();

            // update peers manager
            verify(peersManager).removePeerInformation(eq(publicKey0), any());
        }
    }
}
