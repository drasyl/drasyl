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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.ExceptionMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.MessageId;
import org.drasyl.peer.connection.message.PingMessage;
import org.drasyl.peer.connection.message.PongMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.drasyl.peer.connection.message.ExceptionMessage.Error.ERROR_FORMAT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PingPongHandlerTest {
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private IdleStateEvent evt;
    @Mock
    private ChannelFuture channelFuture;
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private ProofOfWork proofOfwork;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;

    @Test
    void userEventTriggeredShouldSendPingMessageIfThresholdNotReached() throws Exception {
        when(evt.state()).thenReturn(IdleState.READER_IDLE);
        when(ctx.writeAndFlush(any(Message.class))).thenReturn(channelFuture);

        final PingPongHandler handler = new PingPongHandler(identity, (short) 1, new AtomicInteger(0));
        handler.userEventTriggered(ctx, evt);

        verify(ctx).writeAndFlush(any(PingMessage.class));
    }

    @Test
    void userEventTriggeredShouldSendExceptionMessageIfThresholdIsReached() throws Exception {
        when(evt.state()).thenReturn(IdleState.READER_IDLE);
        when(ctx.writeAndFlush(any(Message.class))).thenReturn(channelFuture);

        final PingPongHandler handler = new PingPongHandler(identity, (short) 1, new AtomicInteger(2));
        handler.userEventTriggered(ctx, evt);

        verify(ctx).writeAndFlush(any(ConnectionExceptionMessage.class));
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
    }

    @Test
    void userEventTriggeredShouldSendCorrectNumberOfPingMessages() throws Exception {
        when(evt.state()).thenReturn(IdleState.READER_IDLE);
        when(ctx.writeAndFlush(any(Message.class))).thenReturn(channelFuture);

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

        final PingMessage pingMessage = new PingMessage();
        channel.writeInbound(pingMessage);
        channel.flush();

        assertEquals(new PongMessage(pingMessage.getId()), channel.readOutbound());
    }

    @Test
    void shouldResetCounterIfPingMessageReceived() {
        final PingPongHandler handler = new PingPongHandler(identity, (short) 1, new AtomicInteger(0));
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(new PongMessage(MessageId.of("412176952b5b81fd13f84a7c")));
        channel.flush();

        assertEquals(0, handler.retries.get());
    }

    @Test
    void shouldPassThroughAllUnrelatedMessages() {
        final PingPongHandler handler = new PingPongHandler(identity, (short) 1, new AtomicInteger(0));
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final Message message = new ExceptionMessage(sender, proofOfwork, ERROR_FORMAT);
        channel.writeInbound(message);
        channel.flush();

        assertEquals(0, handler.retries.get());
        assertEquals(message, channel.readInbound());
    }
}