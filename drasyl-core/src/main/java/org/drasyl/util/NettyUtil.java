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
import org.drasyl.annotation.NonNull;

/**
 * Utility class for netty-related operations
 */
public final class NettyUtil {
    private NettyUtil() {
        // util class
    }

    /**
     * Returns the {@link EventLoopGroup} that fits best to the current environment.
     * <p>
     * Under Linux the more performant {@link EpollEventLoopGroup} is returned.
     * <p>
     * Under MacOS/BSD the more performant {@link KQueueEventLoopGroup} is returned.
     * <p>
     * In all other environments {@link NioEventLoopGroup} is returned.
     *
     * @return {@link EventLoopGroup} that fits best to the current environment
     */
    @NonNull
    public static EventLoopGroup getBestEventLoopGroup(final int nThreads) {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(nThreads);
        }
        else if (KQueue.isAvailable()) {
            return new KQueueEventLoopGroup(nThreads);
        }
        else {
            return new NioEventLoopGroup(nThreads);
        }
    }

    /**
     * Returns the {@link DatagramChannel} that fits best to the current environment.
     * <p>
     * Under Linux the more performant {@link EpollDatagramChannel} is returned.
     * <p>
     * Under MacOS/BSD the more performant {@link KQueueDatagramChannel} is returned.
     * <p>
     * In all other environments {@link NioDatagramChannel} is returned.
     *
     * @return {@link DatagramChannel} that fits best to the current environment
     */
    public static Class<? extends DatagramChannel> getBestDatagramChannel() {
        if (Epoll.isAvailable()) {
            return EpollDatagramChannel.class;
        }
        else if (KQueue.isAvailable()) {
            return KQueueDatagramChannel.class;
        }
        else {
            return NioDatagramChannel.class;
        }
    }

    /**
     * Returns the {@link ServerSocketChannel} that fits best to the current environment.
     * <p>
     * Under Linux the more performant {@link EpollServerSocketChannel} is returned.
     * <p>
     * Under MacOS/BSD the more performant {@link KQueueServerSocketChannel} is returned.
     * <p>
     * In all other environments {@link NioServerSocketChannel} is returned.
     *
     * @return {@link ServerSocketChannel} that fits best to the current environment
     */
    public static Class<? extends ServerSocketChannel> getBestServerSocketChannel() {
        if (Epoll.isAvailable()) {
            return EpollServerSocketChannel.class;
        }
        else if (KQueue.isAvailable()) {
            return KQueueServerSocketChannel.class;
        }
        else {
            return NioServerSocketChannel.class;
        }
    }

    /**
     * Returns the {@link SocketChannel} that fits best to the current environment.
     * <p>
     * Under Linux the more performant {@link EpollSocketChannel} is returned.
     * <p>
     * Under MacOS/BSD the more performant {@link KQueueSocketChannel} is returned.
     * <p>
     * In all other environments {@link NioSocketChannel} is returned.
     *
     * @return {@link SocketChannel} that fits best to the current environment
     */
    public static Class<? extends SocketChannel> getBestSocketChannel() {
        if (Epoll.isAvailable()) {
            return EpollSocketChannel.class;
        }
        else if (KQueue.isAvailable()) {
            return KQueueSocketChannel.class;
        }
        else {
            return NioSocketChannel.class;
        }
    }
}
