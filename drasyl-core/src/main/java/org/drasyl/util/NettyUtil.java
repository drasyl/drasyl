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
