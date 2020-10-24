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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ErrorMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExceptionHandlerTest {
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private ChannelPromise promise;
    @Mock
    private Throwable cause;
    @Mock
    private SocketAddress address;
    @Mock
    private Object msg;
    @Mock
    private Channel channel;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;

    // sendMSG the exception as exception message
    @Test
    void exceptionCaughtWithoutRethrow() {
        when(cause.getMessage()).thenReturn("Exception");

        final ExceptionHandler handler = new ExceptionHandler(identity, null, false);
        handler.exceptionCaught(ctx, cause);

        assertEquals(cause, handler.handledCause);
    }

    // do nothing
    @Test
    void exceptionCaughtClosedChannelException() {
        final ExceptionHandler handler = new ExceptionHandler(identity, null, false);
        handler.exceptionCaught(ctx, new ClosedChannelException());

        assertNull(handler.handledCause);
        verify(ctx, never()).writeAndFlush(any(ErrorMessage.class));
    }

    // sendMSG the exception as exception message and pass to the next handler in the pipeline
    @Test
    void exceptionCaughtWithRethrow() {
        when(cause.getMessage()).thenReturn("Exception");

        final ExceptionHandler handler = new ExceptionHandler(identity, null, true);
        handler.exceptionCaught(ctx, cause);

        assertEquals(cause, handler.handledCause);
        verify(ctx).fireExceptionCaught(cause);
    }

    // only rethrow to next pipeline
    @Test
    void exceptionCaughtAlreadyHandled() {
        when(cause.getMessage()).thenReturn("Exception");

        final ExceptionHandler handler = new ExceptionHandler(identity, cause, true);
        handler.exceptionCaught(ctx, cause);

        assertEquals(cause, handler.handledCause);
        verify(ctx, never()).writeAndFlush(any(ErrorMessage.class));
        verify(ctx).fireExceptionCaught(cause);
    }
}