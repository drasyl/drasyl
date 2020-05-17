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
import org.drasyl.peer.connection.message.ExceptionMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class ExceptionHandlerTest {
    private ExceptionHandler.ChannelExceptionListener listener;
    private ChannelHandlerContext ctx;
    private ChannelPromise promise;
    private Throwable cause;
    private SocketAddress address;
    private Object msg;

    @BeforeEach
    void setUp() {
        listener = mock(ExceptionHandler.ChannelExceptionListener.class);
        ctx = mock(ChannelHandlerContext.class);
        promise = mock(ChannelPromise.class);
        address = mock(SocketAddress.class);
        cause = mock(Throwable.class);
        msg = mock(Object.class);

        Channel channel = mock(Channel.class);

        when(ctx.channel()).thenReturn(channel);
        when(channel.isWritable()).thenReturn(true);
        when(cause.getMessage()).thenReturn("Exception");
    }

    // invoke listener
    @Test
    void write() {
        ExceptionHandler handler = new ExceptionHandler(listener, null, false);
        handler.write(ctx, msg, promise);

        verify(ctx).write(msg,
                listener.getListener(promise, ctx));
    }

    // invoke listener
    @Test
    void connect() {
        ExceptionHandler handler = new ExceptionHandler();
        handler.connect(ctx, address, address, promise);

        verify(ctx).connect(address, address,
                listener.getListener(promise, ctx));
    }

    // sendMSG the exception as exception message
    @Test
    void exceptionCaughtWithoutRethrow() {
        ExceptionHandler handler = new ExceptionHandler(listener, null, false);
        handler.exceptionCaught(ctx, cause);

        assertEquals(cause, handler.handledCause);
        verify(ctx).writeAndFlush(any(ExceptionMessage.class));
    }

    // do nothing
    @Test
    void exceptionCaughtClosedChannelException() {
        ExceptionHandler handler = new ExceptionHandler(listener, null, false);
        handler.exceptionCaught(ctx, new ClosedChannelException());

        assertNull(handler.handledCause);
        verify(ctx, never()).writeAndFlush(any(ExceptionMessage.class));
    }

    // sendMSG the exception as exception message and pass to the next handler in the pipeline
    @Test
    void exceptionCaughtWithRethrow() {
        ExceptionHandler handler = new ExceptionHandler(listener, null, true);
        handler.exceptionCaught(ctx, cause);

        assertEquals(cause, handler.handledCause);
        verify(ctx).writeAndFlush(any(ExceptionMessage.class));
        verify(ctx).fireExceptionCaught(cause);
    }

    // only rethrow to next pipeline
    @Test
    void exceptionCaughtAlreadyHandled() {
        ExceptionHandler handler = new ExceptionHandler(listener, cause, true);
        handler.exceptionCaught(ctx, cause);

        assertEquals(cause, handler.handledCause);
        verify(ctx, never()).writeAndFlush(any(ExceptionMessage.class));
        verify(ctx).fireExceptionCaught(cause);
    }
}
