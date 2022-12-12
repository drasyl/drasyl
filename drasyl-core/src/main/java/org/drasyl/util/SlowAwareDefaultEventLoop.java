package org.drasyl.util;

import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * A {@link DefaultEventLoop} that is aware of slow task executions.
 */
@SuppressWarnings("java:S110")
public class SlowAwareDefaultEventLoop extends DefaultEventLoop {
    private static final Logger LOG = LoggerFactory.getLogger(SlowAwareDefaultEventLoop.class);
    public static final float THRESHOLD = Float.parseFloat(SystemPropertyUtil.get("org.drasyl.eventLoop.slowThreshold", "0.0"));

    public SlowAwareDefaultEventLoop() {
        super();
    }

    public SlowAwareDefaultEventLoop(ThreadFactory threadFactory) {
        super(threadFactory);
    }

    public SlowAwareDefaultEventLoop(Executor executor) {
        super(executor);
    }

    public SlowAwareDefaultEventLoop(EventLoopGroup parent) {
        super(parent);
    }

    public SlowAwareDefaultEventLoop(EventLoopGroup parent, ThreadFactory threadFactory) {
        super(parent, threadFactory);
    }

    public SlowAwareDefaultEventLoop(EventLoopGroup parent, Executor executor) {
        super(parent, executor);
    }

    @Override
    public void execute(Runnable task) {
        if (THRESHOLD == 0.0) {
            super.execute(task);
        }
        else {
            super.execute(new SlowAwareTask(task));
        }
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        if (THRESHOLD == 0.0) {
            return super.schedule(command, delay, unit);
        }
        else {
            return super.schedule(new SlowAwareTask(command), delay, unit);
        }
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                                  long initialDelay,
                                                  long period,
                                                  TimeUnit unit) {
        if (THRESHOLD == 0.0) {
            return super.scheduleAtFixedRate(command, initialDelay, period, unit);
        }
        else {
            return super.scheduleAtFixedRate(new SlowAwareTask(command), initialDelay, period, unit);
        }
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                                     long initialDelay,
                                                     long delay,
                                                     TimeUnit unit) {
        if (THRESHOLD == 0.0) {
            return super.scheduleWithFixedDelay(command, initialDelay, delay, unit);
        }
        else {
            return super.scheduleWithFixedDelay(new SlowAwareTask(command), initialDelay, delay, unit);
        }
    }

    private void report(SlowAwareTask task) {
        if (task.executionTime >= THRESHOLD) {
            LOG.warn("SLOW TASK: {}", task.toString());
        }
    }

    private class SlowAwareTask extends Throwable implements Runnable {
        private final String hint;
        private Runnable task;
        private double executionTime = -1;

        public SlowAwareTask(Runnable task) {
            final StringBuilder buf = new StringBuilder(2048);
            // task type / task class
            buf.append(StringUtil.simpleClassName(task));
            buf.append(" ");
            // task caller
            StackTraceElement[] array = getStackTrace();
            if (array.length > 1) {
                StackTraceElement element = array[1];
                buf.append(element.toString());
            }
            hint = buf.toString();
            this.task = requireNonNull(task);
        }

        @Override
        public void run() {
            long startTime = System.nanoTime();
            try {
                task.run();
            }
            finally {
                long endTime = System.nanoTime();
                executionTime = (endTime - startTime) / 1_000_000.0;
                task = null;
                SlowAwareDefaultEventLoop.this.report(this);
            }
        }

        @Override
        public String toString() {
            return hint + String.format(" %.3fms", executionTime);
        }
    }
}
