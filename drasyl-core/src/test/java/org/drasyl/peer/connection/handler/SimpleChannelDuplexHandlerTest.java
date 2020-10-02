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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.SocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SimpleChannelDuplexHandlerTest {
    @Mock
    private SocketAddress socketAddress;
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private ChannelPromise promise;
    private String o;
    private Integer i;
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private SimpleChannelDuplexHandler handler;

    @BeforeEach
    void setUp() {
        o = "Test";
        i = 1;
    }

    @Test
    void testWrite0AndRead0() throws Exception {
        final SimpleChannelDuplexHandler handler = new SimpleChannelDuplexHandler<Integer, String>() {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final Integer msg) {
                assertEquals(i, msg);
            }

            @Override
            protected void channelWrite0(final ChannelHandlerContext ctx,
                                         final String msg,
                                         final ChannelPromise promise) {
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
        final SimpleChannelDuplexHandler<Exception, Number> handler = new SimpleChannelDuplexHandler<>(Exception.class,
                Number.class) {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final Exception msg) {
                fail("this should not be triggered!");
            }

            @Override
            protected void channelWrite0(final ChannelHandlerContext ctx,
                                         final Number msg,
                                         final ChannelPromise promise) {
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
        handler.bind(ctx, socketAddress, promise);

        verify(ctx).bind(socketAddress, promise);
    }

    @Test
    void testConnectFires() throws Exception {
        handler.connect(ctx, socketAddress, socketAddress, promise);

        verify(ctx).connect(socketAddress, socketAddress, promise);
    }

    @Test
    void testDisconnectFires() throws Exception {
        handler.disconnect(ctx, promise);

        verify(ctx).disconnect(promise);
    }

    @Test
    void testCloseFires() throws Exception {
        handler.close(ctx, promise);

        verify(ctx).close(promise);
    }

    @Test
    void testDeregisterFires() throws Exception {
        handler.deregister(ctx, promise);

        verify(ctx).deregister(promise);
    }

    @Test
    void testReadFires() throws Exception {
        handler.read(ctx);

        verify(ctx).read();
    }

    @Test
    void testFlushFires() throws Exception {
        handler.flush(ctx);

        verify(ctx).flush();
    }
}