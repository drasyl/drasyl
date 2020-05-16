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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.net.SocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

class SimpleChannelDuplexHandlerTest {
    private SocketAddress socketAddress;
    private ChannelHandlerContext ctx;
    private ChannelPromise promise;
    private String o;
    private Integer i;

    @BeforeEach
    void setUp() {
        socketAddress = mock(SocketAddress.class);
        ctx = mock(ChannelHandlerContext.class);
        promise = mock(ChannelPromise.class);
        o = "Test";
        i = 1;
    }

    @Test
    void testWrite0AndRead0() throws Exception {
        SimpleChannelDuplexHandler handler = new SimpleChannelDuplexHandler<Integer, String>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Integer msg) throws Exception {
                assertEquals(i, msg);
            }

            @Override
            protected void channelWrite0(ChannelHandlerContext ctx,
                                         String msg,
                                         ChannelPromise promise) throws Exception {
                assertEquals(o, msg);
            }
        };
        handler.write(ctx, o, promise);
        handler.channelRead(ctx, i);

        verify(ctx, never()).write(o);
        verify(ctx, never()).fireChannelRead(i);
    }

    @Test
    void testNoMatch() throws Exception {
        SimpleChannelDuplexHandler handler = new SimpleChannelDuplexHandler<>(Exception.class,
                Number.class) {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Exception msg) throws Exception {
                fail("this should not be triggered!");
            }

            @Override
            protected void channelWrite0(ChannelHandlerContext ctx,
                                         Number msg,
                                         ChannelPromise promise) throws Exception {
                fail("this should not be triggered!");
            }
        };
        handler.write(ctx, o, promise);
        handler.channelRead(ctx, i);

        verify(ctx).write(o, promise);
        verify(ctx).fireChannelRead(i);
    }

    @Test
    void testBindFires() throws Exception {
        SimpleChannelDuplexHandler handler = mock(SimpleChannelDuplexHandler.class, InvocationOnMock::callRealMethod);
        handler.bind(ctx, socketAddress, promise);

        verify(ctx).bind(socketAddress, promise);
    }

    @Test
    void testConnectFires() throws Exception {
        SimpleChannelDuplexHandler handler = mock(SimpleChannelDuplexHandler.class, InvocationOnMock::callRealMethod);
        handler.connect(ctx, socketAddress, socketAddress, promise);

        verify(ctx).connect(socketAddress, socketAddress, promise);
    }

    @Test
    void testDisconnectFires() throws Exception {
        SimpleChannelDuplexHandler handler = mock(SimpleChannelDuplexHandler.class, InvocationOnMock::callRealMethod);
        handler.disconnect(ctx, promise);

        verify(ctx).disconnect(promise);
    }

    @Test
    void testCloseFires() throws Exception {
        SimpleChannelDuplexHandler handler = mock(SimpleChannelDuplexHandler.class, InvocationOnMock::callRealMethod);
        handler.close(ctx, promise);

        verify(ctx).close(promise);
    }

    @Test
    void testDeregisterFires() throws Exception {
        SimpleChannelDuplexHandler handler = mock(SimpleChannelDuplexHandler.class, InvocationOnMock::callRealMethod);
        handler.deregister(ctx, promise);

        verify(ctx).deregister(promise);
    }

    @Test
    void testReadFires() throws Exception {
        SimpleChannelDuplexHandler handler = mock(SimpleChannelDuplexHandler.class, InvocationOnMock::callRealMethod);
        handler.read(ctx);

        verify(ctx).read();
    }

    @Test
    void testFlushFires() throws Exception {
        SimpleChannelDuplexHandler handler = mock(SimpleChannelDuplexHandler.class, InvocationOnMock::callRealMethod);
        handler.flush(ctx);

        verify(ctx).flush();
    }
}