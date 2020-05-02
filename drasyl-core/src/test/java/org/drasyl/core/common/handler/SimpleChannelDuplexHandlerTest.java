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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.net.SocketAddress;

import static org.junit.jupiter.api.Assertions.*;
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
        var handler = new SimpleChannelDuplexHandler<Integer, String>() {
            @Override
            protected void channelWrite0(ChannelHandlerContext ctx, String msg) throws Exception {
                assertEquals(o, msg);
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Integer msg) throws Exception {
                assertEquals(i, msg);
            }
        };
        handler.write(ctx, o, promise);
        handler.channelRead(ctx, i);

        verify(ctx, never()).write(o);
        verify(ctx, never()).fireChannelRead(i);
    }

    @Test
    void testNoMatch() throws Exception {
        var handler = new SimpleChannelDuplexHandler<>(Exception.class,
                Number.class) {
            @Override
            protected void channelWrite0(ChannelHandlerContext ctx, Number msg) throws Exception {
                fail("this should not be triggered!");
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Exception msg) throws Exception {
                fail("this should not be triggered!");
            }
        };
        handler.write(ctx, o, promise);
        handler.channelRead(ctx, i);

        verify(ctx, times(1)).write(o, promise);
        verify(ctx, times(1)).fireChannelRead(i);
    }

    @Test
    void testBindFires() throws Exception {
        var handler = mock(SimpleChannelDuplexHandler.class, InvocationOnMock::callRealMethod);
        handler.bind(ctx, socketAddress, promise);

        verify(ctx, times(1)).bind(socketAddress, promise);
    }

    @Test
    void testConnectFires() throws Exception {
        var handler = mock(SimpleChannelDuplexHandler.class, InvocationOnMock::callRealMethod);
        handler.connect(ctx, socketAddress, socketAddress, promise);

        verify(ctx, times(1)).connect(socketAddress, socketAddress, promise);
    }

    @Test
    void testDisconnectFires() throws Exception {
        var handler = mock(SimpleChannelDuplexHandler.class, InvocationOnMock::callRealMethod);
        handler.disconnect(ctx, promise);

        verify(ctx, times(1)).disconnect(promise);
    }

    @Test
    void testCloseFires() throws Exception {
        var handler = mock(SimpleChannelDuplexHandler.class, InvocationOnMock::callRealMethod);
        handler.close(ctx, promise);

        verify(ctx, times(1)).close(promise);
    }

    @Test
    void testDeregisterFires() throws Exception {
        var handler = mock(SimpleChannelDuplexHandler.class, InvocationOnMock::callRealMethod);
        handler.deregister(ctx, promise);

        verify(ctx, times(1)).deregister(promise);
    }

    @Test
    void testReadFires() throws Exception {
        var handler = mock(SimpleChannelDuplexHandler.class, InvocationOnMock::callRealMethod);
        handler.read(ctx);

        verify(ctx, times(1)).read();
    }

    @Test
    void testFlushFires() throws Exception {
        var handler = mock(SimpleChannelDuplexHandler.class, InvocationOnMock::callRealMethod);
        handler.flush(ctx);

        verify(ctx, times(1)).flush();
    }
}