/*
 * Copyright (c) 2020-2021.
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
