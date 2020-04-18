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

import org.drasyl.core.common.messages.Leave;
import org.drasyl.core.common.messages.Response;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class LeaveHandlerTest {
    private ChannelHandlerContext ctx;
    private Channel channel;
    private LeaveHandler handler;
    private Leave msg;

    @BeforeEach
    void setUp() {
        ctx = mock(ChannelHandlerContext.class);
        handler = LeaveHandler.INSTANCE;
        channel = mock(Channel.class);
        msg = new Leave();

        when(ctx.channel()).thenReturn(channel);
    }

    @Test
    void channelRead0() throws Exception {
        handler.channelRead0(ctx, msg);

        verify(ctx, times(1)).writeAndFlush(any(Response.class));
        verify(channel, times(1)).close();
    }
}