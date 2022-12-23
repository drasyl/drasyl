/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.util.concurrent.ThreadFactory;

/**
 * Utility class for operations on {@link io.netty.channel.EventLoopGroup}s.
 */
public final class EventLoopGroupUtil {
    private EventLoopGroupUtil() {
        // util class
    }

    /**
     * Returns a {@link EventLoopGroup} that fits best to the current platform (epoll, kqueue, or
     * nio).
     *
     * @return {@link EventLoopGroup} that fits best to the current environment
     */
    public static EventLoopGroup getBestEventLoopGroup(final int nThreads) {
        return getBestEventLoopGroup(nThreads, null);
    }

    /**
     * Returns a {@link EventLoopGroup} that fits best to the current platform (epoll, kqueue, or
     * nio).
     *
     * @return {@link EventLoopGroup} that fits best to the current environment
     */
    public static EventLoopGroup getBestEventLoopGroup(final int nThreads,
                                                       final ThreadFactory threadFactory) {
        return new NioEventLoopGroup(nThreads, threadFactory);
    }

    /**
     * Returns a {@link DatagramChannel} that fits best to the current platform (epoll, kqueue, or
     * nio).
     *
     * @return {@link DatagramChannel} that fits best to the current environment
     */
    public static Class<? extends DatagramChannel> getBestDatagramChannel() {
        return NioDatagramChannel.class;
    }

    /**
     * Returns a {@link DatagramChannel} that fits best to the current platform (epoll, kqueue, or
     * nio).
     *
     * @return {@link DatagramChannel} that fits best to the current environment
     */
    public static DatagramChannel getBestDatagramChannel(final InternetProtocolFamily family) {
        return new NioDatagramChannel(family);
    }
}
