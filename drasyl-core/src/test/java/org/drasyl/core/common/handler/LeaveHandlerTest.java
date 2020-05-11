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
package org.drasyl.core.common.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.core.common.message.LeaveMessage;
import org.drasyl.core.common.message.ResponseMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class LeaveHandlerTest {
    private ChannelHandlerContext ctx;
    private ChannelFuture channelFuture;
    private LeaveHandler handler;
    private LeaveMessage msg;

    @BeforeEach
    void setUp() {
        ctx = mock(ChannelHandlerContext.class);
        handler = LeaveHandler.INSTANCE;
        Channel channel = mock(Channel.class);
        channelFuture = mock(ChannelFuture.class);
        msg = new LeaveMessage();

        when(ctx.channel()).thenReturn(channel);
        when(ctx.writeAndFlush(any())).thenReturn(channelFuture);
    }

    @Test
    void channelRead0ShouldSendResponseOkAndThenCloseChannel() throws Exception {
        handler.channelRead0(ctx, msg);

        verify(ctx).writeAndFlush(any(ResponseMessage.class));
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
    }
}