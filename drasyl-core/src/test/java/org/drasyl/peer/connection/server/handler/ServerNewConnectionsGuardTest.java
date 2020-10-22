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
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_PEER_UNAVAILABLE;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerNewConnectionsGuardTest {
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private Message message;
    @Mock
    private ChannelFuture channelFuture;
    @Mock
    private Channel channel;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;

    @Test
    void shouldFireOnOpenGuard() {
        final ServerNewConnectionsGuard handler = new ServerNewConnectionsGuard(identity, () -> true);

        handler.channelRead0(ctx, message);

        verify(ctx).fireChannelRead(message);
    }

    @Test
    void shouldCloseConnectionOnClosedGuard() {
        when(ctx.channel()).thenReturn(channel);
        when(ctx.writeAndFlush(any(Message.class))).thenReturn(channelFuture);

        final ServerNewConnectionsGuard handler = new ServerNewConnectionsGuard(identity, () -> false);

        handler.channelRead0(ctx, message);

        verify(ctx).writeAndFlush(new ConnectionExceptionMessage(identity.getPublicKey(), identity.getProofOfWork(), CONNECTION_ERROR_PEER_UNAVAILABLE));
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
    }
}