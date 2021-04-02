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
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.util.NettyUtil.getBestEventLoopGroup;

/**
 * https://github.com/netty/netty/issues/639#issuecomment-9263566
 */
public final class EventLoopGroupUtil {
    private static final Logger LOG = LoggerFactory.getLogger(EventLoopGroupUtil.class);
    public static final int BEST_DEFAULT_THREADS = 2;
    public static final int NIO_DEFAULT_THREADS = 2;
    static volatile boolean bestGroupCreated;
    static volatile boolean nioGroupCreated;

    private EventLoopGroupUtil() {
        // util class
    }

    /**
     * Returns a {@link EventLoopGroup} that fits best to the current environment (epoll, kqueue,
     * etc.). This method caches the created group and always returns the same group on subsequent
     * invocations. By default the group has {@link #BEST_DEFAULT_THREADS} threads. This number can
     * be changed by using the java system property {@code org.drasyl.event-loop.best}.
     *
     * @return {@link EventLoopGroup} that fits best to the current environment
     */
    @SuppressWarnings("unused")
    public static EventLoopGroup getInstanceBest() {
        return LazyBestGroupHolder.INSTANCE;
    }

    /**
     * Returns a {@link NioEventLoopGroup}. If the group returned by {@link #getInstanceBest()} is
     * of type {@link NioEventLoopGroup}, this method returns the same group. Otherwise, the
     * following applies: This method caches the created {@link NioEventLoopGroup} and always
     * returns the same group on subsequent invocations. By default the group has {@link
     * #NIO_DEFAULT_THREADS} threads. This number can be changed by using the java system property
     * {@code org.drasyl.event-loop.nio}.
     */
    @SuppressWarnings("unused")
    public static NioEventLoopGroup getInstanceNio() {
        if (getInstanceBest() instanceof NioEventLoopGroup) {
            return (NioEventLoopGroup) getInstanceBest();
        }
        else {
            return LazyNioGroupHolder.INSTANCE;
        }
    }

    /**
     * Shutdown the two {@link EventLoopGroup}s {@link #getInstanceBest()} and {@link
     * #getInstanceNio()}.
     * <p>
     * Make sure that this method is <b>never</b> called while {@link org.drasyl.DrasylNode}s are
     * still running! This operation cannot be undone! After performing this operation, no nodes can
     * be started!
     */
    public static CompletableFuture<Void> shutdown() {
        final FutureCombiner combiner = FutureCombiner.getInstance();

        if (bestGroupCreated) {
            combiner.add(FutureUtil.toFuture(EventLoopGroupUtil.LazyBestGroupHolder.INSTANCE.shutdownGracefully()));
        }

        if (nioGroupCreated) {
            combiner.add(FutureUtil.toFuture(EventLoopGroupUtil.LazyNioGroupHolder.INSTANCE.shutdownGracefully()));
        }

        return combiner.combine(new CompletableFuture<>());
    }

    @SuppressWarnings("java:S109")
    private static final class LazyBestGroupHolder {
        static final int SIZE;

        static {
            SIZE = SystemPropertyUtil.getInt("org.drasyl.event-loop.best", BEST_DEFAULT_THREADS);
            LOG.debug("Best event loop group size: {}", SIZE);
        }

        static final EventLoopGroup INSTANCE = getBestEventLoopGroup(SIZE);
        @SuppressWarnings("unused")
        static final boolean LOCK = bestGroupCreated = true;

        private LazyBestGroupHolder() {
        }
    }

    @SuppressWarnings("java:S109")
    private static final class LazyNioGroupHolder {
        static final int SIZE;

        static {
            SIZE = SystemPropertyUtil.getInt("org.drasyl.event-loop.nio", NIO_DEFAULT_THREADS);
            LOG.debug("Nio event loop group size: {}", SIZE);
        }

        static final NioEventLoopGroup INSTANCE = new NioEventLoopGroup(SIZE);
        @SuppressWarnings("unused")
        static final boolean LOCK = nioGroupCreated = true;

        private LazyNioGroupHolder() {
        }
    }
}
