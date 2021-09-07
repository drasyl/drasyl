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
package org.drasyl;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * https://github.com/netty/netty/issues/639#issuecomment-9263566
 */
public final class DrasylChannelEventLoopGroupUtil {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylChannelEventLoopGroupUtil.class);
    // pool should have at least all available processors minus two threads
    public static final int PARENT_DEFAULT_THREADS = Math.max(2, (int) Math.ceil(Runtime.getRuntime().availableProcessors() * 0.1));
    // pool should have at least 2 and max 10% of available processors
    public static final int CHILD_DEFAULT_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
    static volatile boolean parentEventLoopGroupCreated;
    static volatile boolean childEventLoopGroupCreated;

    private DrasylChannelEventLoopGroupUtil() {
        // util class
    }

    /**
     * Use this {@link NioEventLoopGroup} for the {@link org.drasyl.DrasylNode}'s {@link
     * io.netty.channel.ServerChannel}. By default the group has {@link #PARENT_DEFAULT_THREADS}
     * threads. This number can be changed by using the java system property {@code
     * org.drasyl.event-loop.parent}.
     *
     * @return a {@link NioEventLoopGroup} for parent channels
     */
    public static NioEventLoopGroup getParentGroup() {
        return LazyParentEventLoopGroupHolder.INSTANCE;
    }

    /**
     * Use this {@link NioEventLoopGroup} for the {@link org.drasyl.DrasylNode}'s {@link
     * io.netty.channel.ServerChannel}. By default the group has {@link #CHILD_DEFAULT_THREADS}
     * threads. This number can  be changed by using the java system property {@code
     * org.drasyl.event-loop.child}.
     *
     * @return a {@link NioEventLoopGroup} for child channels
     */
    public static NioEventLoopGroup getChildGroup() {
        return LazyChildEventLoopGroupHolder.INSTANCE;
    }

    /**
     * Shutdown the two schedulers.
     *
     * <p>
     * <b>This operation cannot be undone. After performing this operation, no new task can
     * be submitted!</b>
     * </p>
     */
    public static CompletableFuture<Void> shutdown() {
        final FutureCombiner combiner = FutureCombiner.getInstance();

        if (childEventLoopGroupCreated) {
            combiner.add(FutureUtil.toFuture(LazyChildEventLoopGroupHolder.INSTANCE.shutdownGracefully()));
        }

        if (parentEventLoopGroupCreated) {
            combiner.add(FutureUtil.toFuture(LazyParentEventLoopGroupHolder.INSTANCE.shutdownGracefully()));
        }

        return combiner.combine(new CompletableFuture<>());
    }

    private static final class LazyParentEventLoopGroupHolder {
        static final int SIZE;

        static {
            SIZE = SystemPropertyUtil.getInt("org.drasyl.event-loop.parent", PARENT_DEFAULT_THREADS);
            LOG.debug("Parent event loop group size: {}", SIZE);
        }

        static final NioEventLoopGroup INSTANCE = new NioEventLoopGroup(SIZE);
        @SuppressWarnings("unused")
        static final boolean LOCK = parentEventLoopGroupCreated = true;
    }

    private static final class LazyChildEventLoopGroupHolder {
        static final int SIZE;

        static {
            SIZE = SystemPropertyUtil.getInt("org.drasyl.event-loop.child", CHILD_DEFAULT_THREADS);
            LOG.debug("Child event loop group size: {}", SIZE);
        }

        static final NioEventLoopGroup INSTANCE = new NioEventLoopGroup(SIZE);
        @SuppressWarnings("unused")
        static final boolean LOCK = childEventLoopGroupCreated = true;
    }
}
