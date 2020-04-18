/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.common.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.drasyl.core.common.messages.RelayException;
import org.drasyl.core.common.messages.Leave;
import org.drasyl.core.common.messages.Ping;
import org.drasyl.core.common.messages.Pong;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

class PingPongHandlerTest {
    private ChannelHandlerContext ctx;
    private IdleStateEvent evt;

    @BeforeEach
    void setUp() throws Exception {
        ctx = mock(ChannelHandlerContext.class);
        evt = mock(IdleStateEvent.class);
        when(evt.state()).thenReturn(IdleState.READER_IDLE);
    }

    // should send a ping message if count threshold not reached
    @Test
    void testUserEventTriggeredChannelHandlerContextPing() throws Exception {
        PingPongHandler handler = new PingPongHandler((short) 1, (short) 0);
        handler.userEventTriggered(ctx, evt);

        verify(ctx, times(1)).writeAndFlush(any(Ping.class));
    }

    // should respond with exception message if count threshold is reached
    @Test
    void testUserEventTriggeredChannelHandlerContextThresholdReached() throws Exception {
        PingPongHandler handler = new PingPongHandler((short) 1, (short) 2);
        handler.userEventTriggered(ctx, evt);

        verify(ctx, times(1)).writeAndFlush(any(RelayException.class));
    }

    // should send a ping message if count threshold not reached, simulates 3 tries in a row without corresponding
    // pong message
    @Test
    void testUserEventTriggeredChannelHandlerContextCorrectCount() throws Exception {
        PingPongHandler handler = new PingPongHandler((short) 2, (short) 0);

        for (int i = 0; i < 3; i++)
            handler.userEventTriggered(ctx, evt);

        assertEquals((short) 3, handler.counter);
        verify(ctx, times(3)).writeAndFlush(any(Ping.class));
    }

    // should do nothing, because this is not the correct event
    @Test
    void testUserEventTriggeredFalseEvent() throws Exception {
        when(evt.state()).thenReturn(IdleState.WRITER_IDLE);
        PingPongHandler handler = new PingPongHandler((short) 1, (short) 0);
        handler.userEventTriggered(ctx, evt);

        verify(ctx, never()).writeAndFlush(any());
    }

    // should respond with a pong message
    @Test
    void testChannelRead0PingMessage() throws Exception {
        PingPongHandler handler = new PingPongHandler((short) 1, (short) 0);
        handler.channelRead0(ctx, new Ping());

        verify(ctx, times(1)).writeAndFlush(any(Pong.class));
    }

    // should reset the counter
    @Test
    void testChannelRead0PongMessage() throws Exception {
        PingPongHandler handler = new PingPongHandler((short) 1, (short) 0);
        handler.channelRead0(ctx, new Pong());

        assertEquals((short) 0, handler.counter);
    }

    // should pass the message to the next handler in the pipeline
    @Test
    void testChannelRead0FireChannelRead() throws Exception {
        PingPongHandler handler = new PingPongHandler((short) 1, (short) 0);
        handler.channelRead0(ctx, new Leave());

        assertEquals((short) 0, handler.counter);
        verify(ctx, times(1)).fireChannelRead(any(Leave.class));
    }
}
