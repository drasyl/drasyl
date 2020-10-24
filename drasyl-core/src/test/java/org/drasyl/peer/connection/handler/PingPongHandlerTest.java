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
package org.drasyl.peer.connection.handler;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.connection.message.ErrorMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.MessageId;
import org.drasyl.peer.connection.message.PingMessage;
import org.drasyl.peer.connection.message.PongMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.drasyl.peer.connection.message.ErrorMessage.Error.ERROR_IDENTITY_COLLISION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PingPongHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChannelHandlerContext ctx;
    @Mock
    private IdleStateEvent evt;
    @Mock
    private ChannelFuture channelFuture;
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private CompressedPublicKey recipient;
    @Mock
    private ProofOfWork recipientsProofOfWork;
    @Mock
    private MessageId correspondingId;

    @Test
    void userEventTriggeredShouldSendPingMessageIfThresholdNotReached(@Mock final CompressedPublicKey publicKey) throws Exception {
        when(evt.state()).thenReturn(IdleState.READER_IDLE);
        when(ctx.writeAndFlush(any(Message.class))).thenReturn(channelFuture);
        when(ctx.channel().hasAttr(any())).thenReturn(true);
        when(ctx.channel().attr(any()).get()).thenReturn(publicKey);

        final PingPongHandler handler = new PingPongHandler(identity, (short) 1, new AtomicInteger(0));
        handler.userEventTriggered(ctx, evt);

        verify(ctx).writeAndFlush(any(PingMessage.class));
    }

    @Test
    void userEventTriggeredShouldCloseChannelIfThresholdIsReached() throws Exception {
        when(evt.state()).thenReturn(IdleState.READER_IDLE);

        final PingPongHandler handler = new PingPongHandler(identity, (short) 1, new AtomicInteger(2));
        handler.userEventTriggered(ctx, evt);

        verify(ctx).close();
    }

    @Test
    void userEventTriggeredShouldSendCorrectNumberOfPingMessages(@Mock final CompressedPublicKey publicKey) throws Exception {
        when(evt.state()).thenReturn(IdleState.READER_IDLE);
        when(ctx.writeAndFlush(any(Message.class))).thenReturn(channelFuture);
        when(ctx.channel().hasAttr(any())).thenReturn(true);
        when(ctx.channel().attr(any()).get()).thenReturn(publicKey);

        final PingPongHandler handler = new PingPongHandler(identity, (short) 2, new AtomicInteger(0));

        for (int i = 0; i < 3; i++) {
            handler.userEventTriggered(ctx, evt);
        }

        assertEquals(3, handler.retries.get());
        verify(ctx, times(3)).writeAndFlush(any(PingMessage.class));
    }

    @Test
    void userEventTriggeredShouldIgnoreUnrelatedEvents() throws Exception {
        when(evt.state()).thenReturn(IdleState.READER_IDLE);
        when(evt.state()).thenReturn(IdleState.WRITER_IDLE);

        final PingPongHandler handler = new PingPongHandler(identity, (short) 1, new AtomicInteger(0));
        handler.userEventTriggered(ctx, evt);

        verify(ctx, never()).writeAndFlush(any());
    }

    @Test
    void shouldReplyWithPongMessageToPingMessage() {
        final PingPongHandler handler = new PingPongHandler(identity, (short) 1, new AtomicInteger(0));
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final PingMessage pingMessage = new PingMessage(sender, proofOfWork, identity.getPublicKey());
        channel.writeInbound(pingMessage);
        channel.flush();

        assertEquals(new PongMessage(identity.getPublicKey(), identity.getProofOfWork(), sender, pingMessage.getId()), channel.readOutbound());
    }

    @Test
    void shouldResetCounterIfPingMessageReceived() {
        final PingPongHandler handler = new PingPongHandler(identity, (short) 1, new AtomicInteger(0));
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(new PongMessage(recipient, recipientsProofOfWork, sender, MessageId.of("412176952b5b81fd13f84a7c")));
        channel.flush();

        assertEquals(0, handler.retries.get());
    }

    @Test
    void shouldPassThroughAllUnrelatedMessages() {
        final PingPongHandler handler = new PingPongHandler(identity, (short) 1, new AtomicInteger(0));
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final Message message = new ErrorMessage(sender, proofOfWork, recipient, ERROR_IDENTITY_COLLISION, correspondingId);
        channel.writeInbound(message);
        channel.flush();

        assertEquals(0, handler.retries.get());
        assertEquals(message, channel.readInbound());
    }
}