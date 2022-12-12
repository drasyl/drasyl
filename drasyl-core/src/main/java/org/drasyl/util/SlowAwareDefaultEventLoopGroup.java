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

    public SlowAwareDefaultEventLoopGroup(final int nThreads) {
        super(nThreads);
    }

    public SlowAwareDefaultEventLoopGroup(final ThreadFactory threadFactory) {
        super(threadFactory);
    }

    public SlowAwareDefaultEventLoopGroup(final int nThreads, final ThreadFactory threadFactory) {
        super(nThreads, threadFactory);
    }

    public SlowAwareDefaultEventLoopGroup(final int nThreads, final Executor executor) {
        super(nThreads, executor);
    }

    @Override
    protected EventLoop newChild(final Executor executor, final Object... args) throws Exception {
        return new SlowAwareDefaultEventLoop(this, executor);
    }
}
