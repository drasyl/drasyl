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
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
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

    public static EventLoopGroup getBestEventLoopGroup(final int nThreads, final ThreadFactory threadFactory) {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(nThreads, threadFactory);
        }
        else if (KQueue.isAvailable()) {
            return new KQueueEventLoopGroup(nThreads, threadFactory);
        }
        else {
            return new NioEventLoopGroup(nThreads, threadFactory);
        }
    }

    public static EventLoopGroup getBestEventLoopGroup(final int nThreads) {
        return getBestEventLoopGroup(nThreads, null);
    }

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

    public static DatagramChannel getBestDatagramChannel(final InternetProtocolFamily family) {
        if (Epoll.isAvailable()) {
            return new EpollDatagramChannel(family);
        }
        else if (KQueue.isAvailable()) {
            return new KQueueDatagramChannel(family);
        }
        else {
            return new NioDatagramChannel(family);
        }
    }
}
