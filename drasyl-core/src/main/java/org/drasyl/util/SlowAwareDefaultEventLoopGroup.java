package org.drasyl.util;

import io.netty.channel.DefaultEventLoop;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * A {@link DefaultEventLoopGroup} that is aware of slow task executions.
 */
public class SlowAwareDefaultEventLoopGroup extends DefaultEventLoopGroup {
    public SlowAwareDefaultEventLoopGroup() {
        super();
    }

    public SlowAwareDefaultEventLoopGroup(int nThreads) {
        super(nThreads);
    }

    public SlowAwareDefaultEventLoopGroup(ThreadFactory threadFactory) {
        super(threadFactory);
    }

    public SlowAwareDefaultEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory);
    }

    public SlowAwareDefaultEventLoopGroup(int nThreads, Executor executor) {
        super(nThreads, executor);
    }

    @Override
    protected EventLoop newChild(Executor executor, Object... args) throws Exception {
        return new SlowAwareDefaultEventLoop(this, executor);
    }
}
