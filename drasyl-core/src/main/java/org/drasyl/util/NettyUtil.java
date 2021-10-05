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

import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Utility class for netty-related operations
 */
public final class NettyUtil {
    private static final NettyUtilImpl impl = new NettyUtilImpl();

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
        return impl.getBestEventLoopGroup(nThreads);
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
        return impl.getBestDatagramChannel();
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
        return impl.getBestServerSocketChannel();
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
        return impl.getBestSocketChannel();
    }

    static class NettyUtilImpl {
        private final BooleanSupplier epollAvailable;
        private final BooleanSupplier kqueueAvailable;
        private final Function<Integer, EpollEventLoopGroup> epollGroupProvider;
        private final Function<Integer, KQueueEventLoopGroup> kqueueGroupProvider;
        private final Function<Integer, NioEventLoopGroup> nioGroupProvider;

        NettyUtilImpl(final BooleanSupplier epollAvailable,
                      final BooleanSupplier kqueueAvailable,
                      final Function<Integer, EpollEventLoopGroup> epollGroupProvider,
                      final Function<Integer, KQueueEventLoopGroup> kqueueGroupProvider,
                      final Function<Integer, NioEventLoopGroup> nioGroupProvider) {
            this.epollAvailable = requireNonNull(epollAvailable);
            this.kqueueAvailable = requireNonNull(kqueueAvailable);
            this.epollGroupProvider = requireNonNull(epollGroupProvider);
            this.kqueueGroupProvider = requireNonNull(kqueueGroupProvider);
            this.nioGroupProvider = requireNonNull(nioGroupProvider);
        }

        NettyUtilImpl() {
            this(Epoll::isAvailable, KQueue::isAvailable, EpollEventLoopGroup::new, KQueueEventLoopGroup::new, NioEventLoopGroup::new);
        }

        EventLoopGroup getBestEventLoopGroup(final int nThreads) {
            if (epollAvailable.getAsBoolean()) {
                return epollGroupProvider.apply(nThreads);
            }
            else if (kqueueAvailable.getAsBoolean()) {
                return kqueueGroupProvider.apply(nThreads);
            }
            else {
                return nioGroupProvider.apply(nThreads);
            }
        }

        Class<? extends DatagramChannel> getBestDatagramChannel() {
            if (epollAvailable.getAsBoolean()) {
                return EpollDatagramChannel.class;
            }
            else if (kqueueAvailable.getAsBoolean()) {
                return KQueueDatagramChannel.class;
            }
            else {
                return NioDatagramChannel.class;
            }
        }

        Class<? extends ServerSocketChannel> getBestServerSocketChannel() {
            if (epollAvailable.getAsBoolean()) {
                return EpollServerSocketChannel.class;
            }
            else if (kqueueAvailable.getAsBoolean()) {
                return KQueueServerSocketChannel.class;
            }
            else {
                return NioServerSocketChannel.class;
            }
        }

        Class<? extends SocketChannel> getBestSocketChannel() {
            if (epollAvailable.getAsBoolean()) {
                return EpollSocketChannel.class;
            }
            else if (kqueueAvailable.getAsBoolean()) {
                return KQueueSocketChannel.class;
            }
            else {
                return NioSocketChannel.class;
            }
        }
    }
}
