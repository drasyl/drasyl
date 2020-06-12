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
package org.drasyl.peer.connection.server.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
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
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerChannelGroup;
import org.drasyl.util.KeyValue;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofMillis;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_HANDSHAKE_TIMEOUT;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_SAME_PUBLIC_KEY;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_FORBIDDEN;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.drasyl.peer.connection.server.NodeServerChannelGroup.ATTRIBUTE_IDENTITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeServerConnectionHandlerTest {
    private ChannelHandlerContext ctx;
    private ScheduledFuture<?> timeoutFuture;
    private CompressedPublicKey publicKey;
    private ChannelFuture channelFuture;
    private Throwable cause;
    private ApplicationMessage applicationMessage;
    private JoinMessage joinMessage;
    private NodeServer server;
    private Messenger messenger;
    private CompletableFuture<Void> handshakeFuture;
    private JoinMessage requestMessage;
    private Identity identity;
    private PeersManager peersManager;
    private QuitMessage quitMessage;
    private ChannelId channelId;
    private Channel nettyChannel;
    private NodeServerChannelGroup channelGroup;
    private RegisterGrandchildMessage registerGrandchildMessage;
    private Identity grandchildIdentity;
    private Set<URI> grandchildEndpoints;
    private UnregisterGrandchildMessage unregisterGrandchildMessage;
    private Identity superPeerIdentity;
    private PeerInformation superPeerInformation;
    private Path superPeerPath;
    private StatusMessage statusMessage;
    private WelcomeMessage offerMessage;
    private CompressedPublicKey grandchildPublicKey;

    @BeforeEach
    void setUp() {
        ctx = mock(ChannelHandlerContext.class);
        timeoutFuture = mock(ScheduledFuture.class);
        publicKey = mock(CompressedPublicKey.class);
        channelFuture = mock(ChannelFuture.class);
        cause = mock(Throwable.class);
        server = mock(NodeServer.class);
        messenger = mock(Messenger.class);
        handshakeFuture = mock(CompletableFuture.class);
        requestMessage = mock(JoinMessage.class);
        identity = mock(Identity.class);
        peersManager = mock(PeersManager.class);
        quitMessage = mock(QuitMessage.class);
        nettyChannel = mock(Channel.class);
        channelId = mock(ChannelId.class);
        channelGroup = mock(NodeServerChannelGroup.class);
        registerGrandchildMessage = mock(RegisterGrandchildMessage.class);
        grandchildIdentity = mock(Identity.class);
        grandchildPublicKey = mock(CompressedPublicKey.class);
        grandchildEndpoints = Set.of(URI.create("ws://grandchild.com"));
        unregisterGrandchildMessage = mock(UnregisterGrandchildMessage.class);
        superPeerIdentity = mock(Identity.class);
        superPeerInformation = mock(PeerInformation.class);
        superPeerPath = mock(Path.class);
        statusMessage = mock(StatusMessage.class);
        offerMessage = mock(WelcomeMessage.class);

        when(ctx.writeAndFlush(any(Message.class))).thenReturn(channelFuture);
        applicationMessage = mock(ApplicationMessage.class);
        joinMessage = mock(JoinMessage.class);
    }

    @Test
    void shouldSendExceptionMessageIfHandshakeIsNotDoneInTime() {
        NodeServerConnectionHandler handler = new NodeServerConnectionHandler(identity, peersManager, Set.of(), ofMillis(0), messenger, handshakeFuture, timeoutFuture, requestMessage, channelGroup, offerMessage);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        assertEquals(new ConnectionExceptionMessage(CONNECTION_ERROR_HANDSHAKE_TIMEOUT), channel.readOutbound());
    }

    @Test
    void closeBeforeTimeoutShouldNotSendHandshakeTimeoutExceptionMessage() {
        NodeServerConnectionHandler handler = new NodeServerConnectionHandler(identity, peersManager, Set.of(), ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage, channelGroup, offerMessage);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.close();

        assertNull(channel.readOutbound());
    }

    @Test
    void shouldRejectIncomingJoinMessageWithSamePublicKey() {
        when(server.getMessenger()).thenReturn(messenger);
        when(joinMessage.getIdentity()).thenReturn(identity);
        when(identity.getPublicKey()).thenReturn(publicKey);

        NodeServerConnectionHandler handler = new NodeServerConnectionHandler(identity, peersManager, Set.of(), ofMillis(1000), messenger, handshakeFuture, timeoutFuture, null, channelGroup, offerMessage);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(joinMessage);
        channel.flush();

        assertEquals(new ConnectionExceptionMessage(CONNECTION_ERROR_SAME_PUBLIC_KEY), channel.readOutbound());
    }

    @Test
    void shouldRejectUnexpectedMessagesDuringHandshake() {
        when(applicationMessage.getId()).thenReturn("123");

        NodeServerConnectionHandler handler = new NodeServerConnectionHandler(identity, peersManager, Set.of(), ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage, channelGroup, offerMessage);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(applicationMessage);

        assertEquals(new StatusMessage(STATUS_FORBIDDEN, "123"), channel.readOutbound());
        assertNull(channel.readInbound());
    }

    @Test
    void exceptionCaughtShouldWriteExceptionToChannelAndThenCloseIt() {
        when(ctx.channel()).thenReturn(nettyChannel);
        when(nettyChannel.id()).thenReturn(channelId);

        NodeServerConnectionHandler handler = new NodeServerConnectionHandler(identity, peersManager, Set.of(), ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage, channelGroup, offerMessage);
        handler.exceptionCaught(ctx, cause);

        verify(ctx).writeAndFlush(any(ConnectionExceptionMessage.class));
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
    }

    @Test
    void shouldCloseChannelOnQuitMessage() {
        when(handshakeFuture.isDone()).thenReturn(true);
        when(quitMessage.getId()).thenReturn("123");

        NodeServerConnectionHandler handler = new NodeServerConnectionHandler(identity, peersManager, Set.of(), ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage, channelGroup, offerMessage);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(quitMessage);
        channel.flush();

        assertFalse(channel.isOpen());
    }

    @Test
    void shouldAddGrandchildRouteAndInformSuperPeerOnRegisterGrandchildMessageAndRemoveGrandchildRouteAndInformSuperPeerOnClose() {
        when(handshakeFuture.isDone()).thenReturn(true);
        when(registerGrandchildMessage.getId()).thenReturn("123");
        when(registerGrandchildMessage.getIdentity()).thenReturn(grandchildIdentity);
        when(registerGrandchildMessage.getEndpoints()).thenReturn(grandchildEndpoints);
        when(peersManager.getSuperPeer()).thenReturn(Pair.of(superPeerIdentity, superPeerInformation));
        when(superPeerInformation.getPaths()).thenReturn(Set.of(superPeerPath));

        NodeServerConnectionHandler handler = new NodeServerConnectionHandler(identity, peersManager, Set.of(), ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage, channelGroup, offerMessage);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.attr(ATTRIBUTE_IDENTITY).set(identity);

        channel.writeInbound(registerGrandchildMessage);
        channel.flush();

        verify(peersManager).addPeerInformationAndAddGrandchildren(grandchildIdentity, PeerInformation.of(grandchildEndpoints), identity);
        verify(superPeerPath).send(new RegisterGrandchildMessage(grandchildIdentity, grandchildEndpoints));

        channel.close();

        verify(peersManager).removeGrandchildrenRouteAndRemovePeerInformation(grandchildIdentity, PeerInformation.of(grandchildEndpoints));
        verify(superPeerPath).send(new UnregisterGrandchildMessage(grandchildIdentity, grandchildEndpoints));
    }

    @Test
    void shouldRemoveGrandchildRouteAndInformSuperPeerOnUnregisterGrandchildMessage() {
        when(handshakeFuture.isDone()).thenReturn(true);
        when(unregisterGrandchildMessage.getId()).thenReturn("123");
        when(unregisterGrandchildMessage.getIdentity()).thenReturn(grandchildIdentity);
        when(unregisterGrandchildMessage.getEndpoints()).thenReturn(grandchildEndpoints);
        when(peersManager.getSuperPeer()).thenReturn(Pair.of(superPeerIdentity, superPeerInformation));
        when(superPeerInformation.getPaths()).thenReturn(Set.of(superPeerPath));

        NodeServerConnectionHandler handler = new NodeServerConnectionHandler(identity, peersManager, Set.of(), ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage, channelGroup, offerMessage);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.attr(ATTRIBUTE_IDENTITY).set(identity);

        channel.writeInbound(unregisterGrandchildMessage);
        channel.flush();

        verify(peersManager).removeGrandchildrenRouteAndRemovePeerInformation(grandchildIdentity, PeerInformation.of(grandchildEndpoints));
        verify(superPeerPath).send(new UnregisterGrandchildMessage(grandchildIdentity, grandchildEndpoints));
    }

    @Test
    void shouldAddGrandchildRouteAndInformSuperPeerOnSessionCreationAndRemoveGrandchildRouteAndInformSuperPeerOnClose() {
        when(offerMessage.getId()).thenReturn("123");
        when(requestMessage.getIdentity()).thenReturn(identity);
        when(identity.getPublicKey()).thenReturn(publicKey);
        when(grandchildIdentity.getPublicKey()).thenReturn(grandchildPublicKey);
        when(requestMessage.getChildrenAndGrandchildren()).thenReturn(Set.of(KeyValue.of(grandchildIdentity, grandchildEndpoints)));
        when(statusMessage.getCorrespondingId()).thenReturn("123");
        when(statusMessage.getCode()).thenReturn(STATUS_OK);
        when(peersManager.getSuperPeer()).thenReturn(Pair.of(superPeerIdentity, superPeerInformation));
        when(superPeerInformation.getPaths()).thenReturn(Set.of(superPeerPath));

        NodeServerConnectionHandler handler = new NodeServerConnectionHandler(identity, peersManager, Set.of(), ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage, channelGroup, offerMessage);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.attr(ATTRIBUTE_IDENTITY).set(identity);

        channel.writeInbound(statusMessage);
        channel.flush();

        // my new children
        verify(peersManager).addPeerInformationAndAddChildren(eq(identity), any());
        verify(superPeerPath).send(new RegisterGrandchildMessage(identity, Set.of()));

        // my new grandchildren
        verify(peersManager).addPeerInformationAndAddGrandchildren(grandchildIdentity, PeerInformation.of(grandchildEndpoints), identity);
        verify(superPeerPath).send(new RegisterGrandchildMessage(grandchildIdentity, grandchildEndpoints));

        channel.close();

        // children
        verify(peersManager).removeGrandchildrenRouteAndRemovePeerInformation(grandchildIdentity, PeerInformation.of(grandchildEndpoints));
        verify(superPeerPath).send(new UnregisterGrandchildMessage(grandchildIdentity, grandchildEndpoints));

        // grandchildren
        verify(peersManager).removeGrandchildrenRouteAndRemovePeerInformation(grandchildIdentity, PeerInformation.of(grandchildEndpoints));
        verify(superPeerPath).send(new UnregisterGrandchildMessage(grandchildIdentity, grandchildEndpoints));
    }
}
