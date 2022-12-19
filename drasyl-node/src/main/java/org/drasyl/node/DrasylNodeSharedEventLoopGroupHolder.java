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
package org.drasyl.node;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseCombiner;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static io.netty.util.concurrent.ImmediateEventExecutor.INSTANCE;

/**
 * Holds parent and child {@link io.netty.channel.EventLoop}s that are shared across all
 * {@link DrasylNode}s.
 * <p>
 * <a
 * href="https://github.com/netty/netty/issues/639#issuecomment-9263566">https://github.com/netty/netty/issues/639#issuecomment-9263566</a>
 */
public final class DrasylNodeSharedEventLoopGroupHolder {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylNodeSharedEventLoopGroupHolder.class);
    // pool should have at least all available processors minus two threads
    public static final int PARENT_DEFAULT_THREADS = Math.max(2, (int) Math.ceil(Runtime.getRuntime().availableProcessors() * 0.1));
    // pool should have at least 2 and max 10% of available processors
    public static final int CHILD_DEFAULT_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
    // pool should have at least 2 and max 10% of available processors
    public static final int NETWORK_DEFAULT_THREADS = Math.max(2, (int) Math.ceil(Runtime.getRuntime().availableProcessors() * 0.1));
    static volatile boolean parentEventLoopGroupCreated;
    static volatile boolean childEventLoopGroupCreated;
    static volatile boolean networkEventLoopGroupCreated;

    private DrasylNodeSharedEventLoopGroupHolder() {
        // util class
    }

    /**
     * Use this {@link EventLoopGroup} for the {@link DrasylNode}'s
     * {@link io.netty.channel.ServerChannel}. By default the group has
     * {@link #PARENT_DEFAULT_THREADS} threads. This number can be changed by using the java system
     * property {@code org.drasyl.event-loop.parent}.
     *
     * @return a {@link EventLoopGroup} for parent channels
     */
    public static EventLoopGroup getParentGroup() {
        return LazyParentHolder.INSTANCE;
    }

    /**
     * Use this {@link EventLoopGroup} for the {@link DrasylNode}'s
     * {@link io.netty.channel.ServerChannel}. By default the group has
     * {@link #CHILD_DEFAULT_THREADS} threads. This number can  be changed by using the java system
     * property {@code org.drasyl.event-loop.child}.
     *
     * @return a {@link EventLoopGroup} for child channels
     */
    public static EventLoopGroup getChildGroup() {
        return LazyChildHolder.INSTANCE;
    }

    /**
     * Use this {@link EventLoopGroup} for the {@link DrasylNode}'s network based
     * {@link io.netty.channel.Channel}s (udp server, tcp client, etc.). By default, the group has
     * {@link #NETWORK_DEFAULT_THREADS} threads. This number can  be changed by using the java
     * system property {@code org.drasyl.event-loop.network}.
     *
     * @return a {@link NioEventLoopGroup} for child channels
     */
    public static EventLoopGroup getNetworkGroup() {
        return LazyNetworkHolder.INSTANCE;
    }

    /**
     * Shutdown the two schedulers.
     *
     * <p>
     * <b>This operation cannot be undone. After performing this operation, no new task can
     * be submitted!</b>
     * </p>
     */
    public static Future<Void> shutdown() {
        final PromiseCombiner combiner = new PromiseCombiner(INSTANCE);

        if (childEventLoopGroupCreated) {
            combiner.add(LazyChildHolder.INSTANCE.shutdownGracefully());
        }

        if (parentEventLoopGroupCreated) {
            combiner.add(LazyParentHolder.INSTANCE.shutdownGracefully());
        }

        if (networkEventLoopGroupCreated) {
            combiner.add(LazyNetworkHolder.INSTANCE.shutdownGracefully());
        }

        final Promise<Void> aggregatePromise = new DefaultPromise<>(INSTANCE);
        combiner.finish(aggregatePromise);
        return aggregatePromise;
    }

    private static final class LazyParentHolder {
        static final int SIZE;

        static {
            SIZE = SystemPropertyUtil.getInt("org.drasyl.node.event-loop.parent", PARENT_DEFAULT_THREADS);
            LOG.debug("Parent event loop group size: {}", SIZE);
        }

        static final EventLoopGroup INSTANCE = EventLoopGroupUtil.getBestEventLoopGroup(SIZE, new DefaultThreadFactory(DrasylNodeSharedEventLoopGroupHolder.class.getSimpleName() + "-parent", true));

        @SuppressWarnings("unused")
        static final boolean LOCK = parentEventLoopGroupCreated = true;
    }

    private static final class LazyChildHolder {
        static final int SIZE;

        static {
            SIZE = SystemPropertyUtil.getInt("org.drasyl.node.event-loop.child", CHILD_DEFAULT_THREADS);
            LOG.debug("Child event loop group size: {}", SIZE);
        }

        static final EventLoopGroup INSTANCE = EventLoopGroupUtil.getBestEventLoopGroup(SIZE, new DefaultThreadFactory(DrasylNodeSharedEventLoopGroupHolder.class.getSimpleName() + "-child", true));
        @SuppressWarnings("unused")
        static final boolean LOCK = childEventLoopGroupCreated = true;
    }

    private static final class LazyNetworkHolder {
        static final int SIZE;

        static {
            SIZE = SystemPropertyUtil.getInt("org.drasyl.node.event-loop.network", NETWORK_DEFAULT_THREADS);
            LOG.debug("Network event loop group size: {}", SIZE);
        }

        static final EventLoopGroup INSTANCE = EventLoopGroupUtil.getBestEventLoopGroup(SIZE, new DefaultThreadFactory(DrasylNodeSharedEventLoopGroupHolder.class.getSimpleName() + "-network", true));
        @SuppressWarnings("unused")
        static final boolean LOCK = networkEventLoopGroupCreated = true;
    }
}
