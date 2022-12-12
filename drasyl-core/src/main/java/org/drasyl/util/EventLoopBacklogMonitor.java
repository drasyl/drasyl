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
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.*;

/**
 * This utility class can be used to monitor the number of pending tasks of {@link
 * io.netty.channel.EventLoop}s. If a given threshold is reached, the current number of pending
 * tasks is logged.
 */
public final class EventLoopBacklogMonitor {
    private static final Logger LOG = LoggerFactory.getLogger(EventLoopBacklogMonitor.class);
    public static final int THRESHOLD = Integer.parseInt(SystemPropertyUtil.get("org.drasyl.eventLoop.backlogThreshold", "10"));
    public static final int PERIOD = Integer.parseInt(SystemPropertyUtil.get("org.drasyl.eventLoop.backlogThresholdSamplingRate", "100"));
    private static Timer timer;
    private static final Set<EventLoopGroup> GROUPS = new HashSet<>();

    private EventLoopBacklogMonitor() {
        // util class
    }

    public static synchronized void monitorBacklog(EventLoopGroup... groups) {
        if (THRESHOLD < 1) {
            return;
        }

        if (timer == null) {
            timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    Iterator<EventLoopGroup> groupsIterator = GROUPS.iterator();
                    while (groupsIterator.hasNext()) {
                        EventLoopGroup group = groupsIterator.next();
                        if (group.isTerminated()) {
                            groupsIterator.remove();
                            continue;
                        }

                        Iterator<EventExecutor> loopsIterator = group.iterator();
                        while (loopsIterator.hasNext()) {
                            SingleThreadEventExecutor loop = (SingleThreadEventExecutor) loopsIterator.next();
                            int pendingTasks = loop.pendingTasks();
                            if (pendingTasks >= THRESHOLD) {
                                LOG.warn("BACKLOG: EventLoop `{}` has {} pending tasks.", loop.threadProperties().name(), pendingTasks);
                            }
                        }
                    }

                    if (GROUPS.isEmpty()) {
                        timer.cancel();
                        timer = null;
                    }
                }
            }, PERIOD, PERIOD);
        }

        GROUPS.addAll(Arrays.asList(groups));
    }
}
