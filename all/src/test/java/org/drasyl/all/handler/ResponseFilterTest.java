/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.handler;

import org.drasyl.all.messages.Leave;
import org.drasyl.all.messages.Response;
import io.netty.channel.ChannelHandlerContext;
import io.sentry.util.CircularFifoQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResponseFilterTest {
    ChannelHandlerContext ctx;
    Leave msg;

    @BeforeEach
    void setUp() {
        ctx = mock(ChannelHandlerContext.class);
        msg = new Leave();
    }

    @Test
    void testChannelWrite0NotInBucket() throws Exception {
        ResponseFilter filter = new ResponseFilter(3);

        filter.channelWrite0(ctx, msg);

        assertEquals(1, filter.outboundMessagesQueue.size());
        verify(ctx, times(1)).write(any(Leave.class));
    }

    @Test
    void testChannelWrite0InBucket() throws Exception {
        CircularFifoQueue<String> queue = new CircularFifoQueue<>();
        queue.add(msg.getMessageID());
        ResponseFilter filter = new ResponseFilter(queue);

        filter.channelWrite0(ctx, msg);

        assertEquals(1, queue.size());
        verify(ctx, times(1)).write(any(Leave.class));
    }

    @Test
    void testChannelWrite0InBucketCompatibleMode() throws Exception {
        CircularFifoQueue<String> queue = new CircularFifoQueue<>();
        ResponseFilter filter = new ResponseFilter(queue);

        filter.channelWrite0(ctx, msg);

        assertEquals(0, queue.size());
        verify(ctx, times(1)).write(any(Leave.class));
    }

    @Test
    void channelRead0() throws Exception {
        CircularFifoQueue<String> queue = new CircularFifoQueue<>();
        queue.add(msg.getMessageID());
        ResponseFilter filter = new ResponseFilter(queue);
        Response response = new Response(msg, msg.getMessageID());

        filter.channelRead0(ctx, response);


        verify(ctx, times(1)).fireChannelRead(any(Response.class));
        verify(ctx, never()).writeAndFlush(any(Response.class));
    }

    @Test
    void channelRead0NotExpected() throws Exception {
        ResponseFilter filter = new ResponseFilter(3);
        Response response = new Response(msg, msg.getMessageID());

        filter.channelRead0(ctx, response);

        verify(ctx, never()).fireChannelRead(any(Response.class));
        verify(ctx, times(1)).writeAndFlush(any(Response.class));
    }
}