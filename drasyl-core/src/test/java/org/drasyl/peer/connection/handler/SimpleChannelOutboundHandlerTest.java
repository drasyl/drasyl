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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SimpleChannelOutboundHandlerTest {
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private ChannelPromise promise;
    private String o;

    @BeforeEach
    void setUp() {
        o = "Test";
    }

    @Test
    void testWrite0() throws Exception {
        SimpleChannelOutboundHandler<String> handler = new SimpleChannelOutboundHandler<>() {
            @Override
            protected void channelWrite0(ChannelHandlerContext ctx,
                                         String msg,
                                         ChannelPromise promise) throws Exception {
                assertEquals(o, msg);
            }
        };
        handler.write(ctx, o, promise);

        verify(ctx, never()).write(o);
        verify(promise, never()).setSuccess();
    }

    @Test
    void testWrite0WithRelease() throws Exception {
        SimpleChannelOutboundHandler<String> handler = new SimpleChannelOutboundHandler<>(true, true) {
            @Override
            protected void channelWrite0(ChannelHandlerContext ctx,
                                         String msg,
                                         ChannelPromise promise) throws Exception {
                assertEquals(o, msg);
            }
        };
        handler.write(ctx, o, promise);

        verify(ctx, never()).write(o);
        verify(promise).setSuccess();
    }

    @Test
    void testNoMatch() throws Exception {
        SimpleChannelOutboundHandler<Number> handler = new SimpleChannelOutboundHandler<>(Number.class) {
            @Override
            protected void channelWrite0(ChannelHandlerContext ctx,
                                         Number msg,
                                         ChannelPromise promise) throws Exception {
                fail("this should not be triggered!");
            }
        };
        handler.write(ctx, o, promise);

        verify(ctx).write(o, promise);
        verify(promise, never()).setSuccess();
    }
}