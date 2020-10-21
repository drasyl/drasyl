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
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.Message;
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
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_HANDSHAKE_TIMEOUT;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_IDENTITY_COLLISION;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_NOT_A_SUPER_PEER;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_OTHER_NETWORK;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_FORBIDDEN;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.anyShort;
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
    @Mock(answer = RETURNS_DEEP_STUBS)
    private JoinMessage joinMessage;
    @Mock
    private Pipeline pipeline;
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
    private PeerChannelGroup channelGroup;
    @Mock
    private StatusMessage statusMessage;
    @Mock
    private WelcomeMessage offerMessage;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ServerEnvironment environment;

    @Test
    void shouldSendExceptionMessageIfHandshakeIsNotDoneInTime() {
        final ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(0), pipeline, handshakeFuture, null, requestMessage, offerMessage);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        assertEquals(new ConnectionExceptionMessage(CONNECTION_ERROR_HANDSHAKE_TIMEOUT), channel.readOutbound());
    }

    @Test
    void closeBeforeTimeoutShouldNotSendHandshakeTimeoutExceptionMessage() {
        final ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), pipeline, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.close();

        assertNull(channel.readOutbound());
    }

    @Test
    void shouldRejectIncomingJoinMessageWithSamePublicKey() {
        when(environment.getIdentity().getPublicKey()).thenReturn(publicKey0);
        when(joinMessage.getSender()).thenReturn(publicKey0);
        when(joinMessage.getProofOfWork().isValid(any(), anyShort())).thenReturn(true);

        final ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), pipeline, handshakeFuture, timeoutFuture, null, offerMessage);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(joinMessage);
        channel.flush();

        assertEquals(new ConnectionExceptionMessage(CONNECTION_ERROR_IDENTITY_COLLISION), channel.readOutbound());
    }

    @Test
    void shouldRejectIncomingJoinMessageWithOtherNetworkId() {
        when(environment.getConfig().getNetworkId()).thenReturn(23);
        when(joinMessage.getNetworkId()).thenReturn(32);
        when(joinMessage.getProofOfWork().isValid(any(), anyShort())).thenReturn(true);

        final ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), pipeline, handshakeFuture, timeoutFuture, null, offerMessage);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(joinMessage);
        channel.flush();

        assertEquals(new ConnectionExceptionMessage(CONNECTION_ERROR_OTHER_NETWORK), channel.readOutbound());
    }

    @Test
    void shouldRejectUnexpectedMessagesDuringHandshake() {
        when(applicationMessage.getId()).thenReturn(MessageId.of("412176952b5b81fd13f84a7c"));

        final ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), pipeline, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(applicationMessage);

        assertEquals(new StatusMessage(STATUS_FORBIDDEN, MessageId.of("412176952b5b81fd13f84a7c")), channel.readOutbound());
        assertNull(channel.readInbound());
    }

    @Test
    void exceptionCaughtShouldWriteExceptionToChannelAndThenCloseIt() {
        when(ctx.writeAndFlush(any(Message.class))).thenReturn(channelFuture);
        when(ctx.channel()).thenReturn(nettyChannel);
        when(nettyChannel.id()).thenReturn(channelId);

        final ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), pipeline, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
        handler.exceptionCaught(ctx, cause);

        verify(ctx).writeAndFlush(any(ConnectionExceptionMessage.class));
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
    }

    @Test
    void shouldCloseChannelOnQuitMessage() {
        when(handshakeFuture.isDone()).thenReturn(true);

        final ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), pipeline, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(quitMessage);
        channel.flush();

        assertFalse(channel.isOpen());
    }

    @Nested
    class ClientAsChildren {
        @Test
        void shouldAddPeerInformationOnSessionCreationAndRemovePeerInformationOnClose() {
            when(environment.getPeersManager()).thenReturn(peersManager);
            when(environment.getChannelGroup()).thenReturn(channelGroup);
            when(offerMessage.getId()).thenReturn(MessageId.of("412176952b5b81fd13f84a7c"));
            when(requestMessage.getSender()).thenReturn(publicKey0);
            when(requestMessage.isChildrenJoin()).thenReturn(true);
            when(statusMessage.getCorrespondingId()).thenReturn(MessageId.of("412176952b5b81fd13f84a7c"));
            when(statusMessage.getCode()).thenReturn(STATUS_OK);

            final ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), pipeline, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            channel.writeInbound(statusMessage);
            channel.flush();

            // update peers manager
            verify(peersManager).setPeerInformationAndAddPathAndChildren(eq(publicKey0), any(), any());

            channel.close();

            // update peers manager
            verify(peersManager).removeChildrenAndPath(eq(publicKey0), any());
        }

        @Test
        void shouldRejectClientJoins() {
            when(environment.getConfig().isSuperPeerEnabled()).thenReturn(true);
            when(joinMessage.isChildrenJoin()).thenReturn(true);
            when(joinMessage.getProofOfWork().isValid(any(), anyShort())).thenReturn(true);

            final ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), pipeline, handshakeFuture, timeoutFuture, null, offerMessage);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            channel.writeInbound(joinMessage);
            channel.flush();

            assertEquals(new ConnectionExceptionMessage(CONNECTION_ERROR_NOT_A_SUPER_PEER), channel.readOutbound());
        }
    }

    @Nested
    class ClientAsPeer {
        @Test
        void shouldAddPeerInformationOnSessionCreationAndRemovePeerInformationOnClose() {
            when(environment.getPeersManager()).thenReturn(peersManager);
            when(environment.getChannelGroup()).thenReturn(channelGroup);
            when(offerMessage.getId()).thenReturn(MessageId.of("412176952b5b81fd13f84a7c"));
            when(requestMessage.getSender()).thenReturn(publicKey0);
            when(requestMessage.isChildrenJoin()).thenReturn(false);
            when(statusMessage.getCorrespondingId()).thenReturn(MessageId.of("412176952b5b81fd13f84a7c"));
            when(statusMessage.getCode()).thenReturn(STATUS_OK);

            final ServerConnectionHandler handler = new ServerConnectionHandler(environment, ofMillis(1000), pipeline, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            channel.writeInbound(statusMessage);
            channel.flush();

            // update peers manager
            verify(peersManager).setPeerInformationAndAddPath(eq(publicKey0), any(), any());

            channel.close();

            // update peers manager
            verify(peersManager).removePath(eq(publicKey0), any());
        }
    }
}