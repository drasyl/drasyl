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

import io.netty.channel.*;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.mockito.Mockito.*;

class QuitMessageHandlerTest {
    private ChannelHandlerContext ctx;
    private ChannelFuture channelFuture;
    private QuitMessageHandler handler;
    private QuitMessage msg;

    @BeforeEach
    void setUp() {
        ctx = mock(ChannelHandlerContext.class);
        handler = QuitMessageHandler.INSTANCE;
        Channel channel = mock(Channel.class);
        ChannelId channelId = mock(ChannelId.class);
        channelFuture = mock(ChannelFuture.class);
        msg = new QuitMessage();

        when(ctx.channel()).thenReturn(channel);
        when(channel.id()).thenReturn(channelId);
        when(channelId.asShortText()).thenReturn("123123");
        when(ctx.writeAndFlush(any())).thenReturn(channelFuture);
    }

    @Test
    void channelRead0ShouldSendStatusOkAndThenCloseChannel() throws Exception {
        handler.channelRead0(ctx, msg);

        verify(ctx).writeAndFlush(new StatusMessage(STATUS_OK, msg.getId()));
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
    }
}
