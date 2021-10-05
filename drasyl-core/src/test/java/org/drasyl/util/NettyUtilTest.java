/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.util;

import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.drasyl.util.NettyUtil.NettyUtilImpl;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NettyUtilTest {
    @Mock
    BooleanSupplier epollAvailable;
    @Mock
    BooleanSupplier kqueueAvailable;
    @Mock
    Function<Integer, EpollEventLoopGroup> epollGroupProvider;
    @Mock
    Function<Integer, KQueueEventLoopGroup> kqueueGroupProvider;
    @Mock
    Function<Integer, NioEventLoopGroup> nioGroupProvider;

    @Nested
    class GetBestEventLoopGroup {
        @Test
        void shouldReturnCorrectGroup() {
            when(epollAvailable.getAsBoolean()).thenReturn(true);
            new NettyUtilImpl(epollAvailable, kqueueAvailable, epollGroupProvider, kqueueGroupProvider, nioGroupProvider).getBestEventLoopGroup(1);
            verify(epollGroupProvider).apply(1);

            when(epollAvailable.getAsBoolean()).thenReturn(false);
            when(kqueueAvailable.getAsBoolean()).thenReturn(true);
            new NettyUtilImpl(epollAvailable, kqueueAvailable, epollGroupProvider, kqueueGroupProvider, nioGroupProvider).getBestEventLoopGroup(1);
            verify(kqueueGroupProvider).apply(1);

            when(epollAvailable.getAsBoolean()).thenReturn(false);
            when(kqueueAvailable.getAsBoolean()).thenReturn(false);
            new NettyUtilImpl(epollAvailable, kqueueAvailable, epollGroupProvider, kqueueGroupProvider, nioGroupProvider).getBestEventLoopGroup(1);
            verify(nioGroupProvider).apply(1);
        }
    }

    @Nested
    class GetBestDatagramChannel {
        @Test
        void shouldReturnCorrectChannel() {
            when(epollAvailable.getAsBoolean()).thenReturn(true);
            assertEquals(EpollDatagramChannel.class, new NettyUtilImpl(epollAvailable, kqueueAvailable, epollGroupProvider, kqueueGroupProvider, nioGroupProvider).getBestDatagramChannel());

            when(epollAvailable.getAsBoolean()).thenReturn(false);
            when(kqueueAvailable.getAsBoolean()).thenReturn(true);
            assertEquals(KQueueDatagramChannel.class, new NettyUtilImpl(epollAvailable, kqueueAvailable, epollGroupProvider, kqueueGroupProvider, nioGroupProvider).getBestDatagramChannel());

            when(epollAvailable.getAsBoolean()).thenReturn(false);
            when(kqueueAvailable.getAsBoolean()).thenReturn(false);
            assertEquals(NioDatagramChannel.class, new NettyUtilImpl(epollAvailable, kqueueAvailable, epollGroupProvider, kqueueGroupProvider, nioGroupProvider).getBestDatagramChannel());
        }
    }

    @Nested
    class GetBestServerSocketChannel {
        @Test
        void shouldReturnCorrectChannel() {
            when(epollAvailable.getAsBoolean()).thenReturn(true);
            assertEquals(EpollServerSocketChannel.class, new NettyUtilImpl(epollAvailable, kqueueAvailable, epollGroupProvider, kqueueGroupProvider, nioGroupProvider).getBestServerSocketChannel());

            when(epollAvailable.getAsBoolean()).thenReturn(false);
            when(kqueueAvailable.getAsBoolean()).thenReturn(true);
            assertEquals(KQueueServerSocketChannel.class, new NettyUtilImpl(epollAvailable, kqueueAvailable, epollGroupProvider, kqueueGroupProvider, nioGroupProvider).getBestServerSocketChannel());

            when(epollAvailable.getAsBoolean()).thenReturn(false);
            when(kqueueAvailable.getAsBoolean()).thenReturn(false);
            assertEquals(NioServerSocketChannel.class, new NettyUtilImpl(epollAvailable, kqueueAvailable, epollGroupProvider, kqueueGroupProvider, nioGroupProvider).getBestServerSocketChannel());
        }
    }

    @Nested
    class GetBestSocketChannel {
        @Test
        void shouldReturnCorrectChannel() {
            when(epollAvailable.getAsBoolean()).thenReturn(true);
            assertEquals(EpollSocketChannel.class, new NettyUtilImpl(epollAvailable, kqueueAvailable, epollGroupProvider, kqueueGroupProvider, nioGroupProvider).getBestSocketChannel());

            when(epollAvailable.getAsBoolean()).thenReturn(false);
            when(kqueueAvailable.getAsBoolean()).thenReturn(true);
            assertEquals(KQueueSocketChannel.class, new NettyUtilImpl(epollAvailable, kqueueAvailable, epollGroupProvider, kqueueGroupProvider, nioGroupProvider).getBestSocketChannel());

            when(epollAvailable.getAsBoolean()).thenReturn(false);
            when(kqueueAvailable.getAsBoolean()).thenReturn(false);
            assertEquals(NioSocketChannel.class, new NettyUtilImpl(epollAvailable, kqueueAvailable, epollGroupProvider, kqueueGroupProvider, nioGroupProvider).getBestSocketChannel());
        }
    }
}
