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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class NettyUtilTest {
    @Nested
    class GetBestEventLoopGroup {
        @SuppressWarnings("ConstantConditions")
        @Test
        void shouldReturnCorrectGroup() {
            EventLoopGroup group = null;
            try {
                group = NettyUtil.getBestEventLoopGroup(1);

                if (Epoll.isAvailable()) {
                    assertThat(group, instanceOf(EpollEventLoopGroup.class));
                }
                else if (KQueue.isAvailable()) {
                    assertThat(group, instanceOf(KQueueEventLoopGroup.class));
                }
                else {
                    assertThat(group, instanceOf(NioEventLoopGroup.class));
                }
            }
            finally {
                group.shutdownGracefully().awaitUninterruptibly();
            }
        }
    }

    @Nested
    class GetBestDatagramChannel {
        @Test
        void shouldReturnCorrectChannel() {
            final Class<? extends DatagramChannel> channel = NettyUtil.getBestDatagramChannel();

            if (Epoll.isAvailable()) {
                assertEquals(EpollDatagramChannel.class, channel);
            }
            else if (KQueue.isAvailable()) {
                assertEquals(KQueueDatagramChannel.class, channel);
            }
            else {
                assertEquals(NioDatagramChannel.class, channel);
            }
        }
    }

    @Nested
    class GetBestServerSocketChannel {
        @Test
        void shouldReturnCorrectChannel() {
            final Class<? extends ServerSocketChannel> channel = NettyUtil.getBestServerSocketChannel();

            if (Epoll.isAvailable()) {
                assertEquals(EpollServerSocketChannel.class, channel);
            }
            else if (KQueue.isAvailable()) {
                assertEquals(KQueueServerSocketChannel.class, channel);
            }
            else {
                assertEquals(NioServerSocketChannel.class, channel);
            }
        }
    }

    @Nested
    class GetBestSocketChannel {
        @Test
        void shouldReturnCorrectChannel() {
            final Class<? extends SocketChannel> channel = NettyUtil.getBestSocketChannel();

            if (Epoll.isAvailable()) {
                assertEquals(EpollSocketChannel.class, channel);
            }
            else if (KQueue.isAvailable()) {
                assertEquals(KQueueSocketChannel.class, channel);
            }
            else {
                assertEquals(NioSocketChannel.class, channel);
            }
        }
    }
}
