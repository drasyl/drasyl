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

import org.drasyl.all.messages.RelayException;
import org.drasyl.all.Drasyl;
import org.drasyl.all.session.util.ClientSessionBucket;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KillOnExceptionHandlerTest {
    private ChannelHandlerContext ctx;
    private ClientSessionBucket bucket;
    private Drasyl relay;
    private Throwable cause;
    private ChannelId id;

    @BeforeEach
    void setUp() {
        ctx = mock(ChannelHandlerContext.class);
        bucket = mock(ClientSessionBucket.class);
        relay = mock(Drasyl.class);
        Channel channel = mock(Channel.class);
        cause = mock(Throwable.class);
        id = mock(ChannelId.class);

        when(relay.getClientBucket()).thenReturn(bucket);
        when(ctx.channel()).thenReturn(channel);
        when(channel.id()).thenReturn(id);
    }

    // throw exception and kill connection
    @Test
    void exceptionCaught() {
        when(bucket.getInitializedChannels()).thenReturn(Set.of());
        KillOnExceptionHandler handler = new KillOnExceptionHandler(relay);
        handler.exceptionCaught(ctx, cause);

        verify(ctx, times(1)).writeAndFlush(any(RelayException.class));
        verify(ctx, times(1)).close();
    }

    // do nothing
    @Test
    void exceptionCaughtInitializedChannels() {
        when(bucket.getInitializedChannels()).thenReturn(Set.of(id));
        KillOnExceptionHandler handler = new KillOnExceptionHandler(relay);
        handler.exceptionCaught(ctx, cause);

        verify(ctx, never()).writeAndFlush(any(RelayException.class));
        verify(ctx, never()).close();
    }
}