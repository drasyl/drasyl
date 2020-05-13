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
package org.drasyl.core.server.handler;

import io.netty.channel.*;
import org.drasyl.core.common.message.ConnectionExceptionMessage;
import org.drasyl.core.common.message.Message;
import org.drasyl.core.node.PeersManager;
import org.drasyl.core.server.NodeServer;
import org.drasyl.crypto.Crypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KillOnExceptionHandlerTest {
    private ChannelHandlerContext ctx;
    private PeersManager peersManager;
    private NodeServer nodeServer;
    private Throwable cause;
    private String id;
    private ChannelFuture channelFuture;

    @BeforeEach
    void setUp() {
        ctx = mock(ChannelHandlerContext.class);
        peersManager = mock(PeersManager.class);
        nodeServer = mock(NodeServer.class);
        Channel channel = mock(Channel.class);
        cause = mock(Throwable.class);
        ChannelId channelId = mock(ChannelId.class);
        id = Crypto.randomString(3);
        channelFuture = mock(ChannelFuture.class);

        when(nodeServer.getPeersManager()).thenReturn(peersManager);
        when(ctx.channel()).thenReturn(channel);
        when(channel.id()).thenReturn(channelId);
        when(channelId.asLongText()).thenReturn(id);
        when(ctx.writeAndFlush(any(Message.class))).thenReturn(channelFuture);
    }

    @Test
    void exceptionCaughtShouldWriteExceptionToChannelAndThenCloseIt() {
        when(peersManager.getPeers()).thenReturn(Map.of());
        KillOnExceptionHandler handler = KillOnExceptionHandler.INSTANCE;
        handler.exceptionCaught(ctx, cause);

        verify(ctx).writeAndFlush(any(ConnectionExceptionMessage.class));
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
    }
}
