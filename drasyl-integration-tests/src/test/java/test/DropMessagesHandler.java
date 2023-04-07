/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package test;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.util.RandomUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requirePositive;

public class DropMessagesHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DropMessagesHandler.class);
    private final Predicate<Object> dropOutbound;
    private final Predicate<Object> dropInbound;

    public DropMessagesHandler(final Predicate<Object> dropOutbound,
                               final Predicate<Object> dropInbound) {
        this.dropOutbound = requireNonNull(dropOutbound);
        this.dropInbound = requireNonNull(dropInbound);
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (dropOutbound.test(msg)) {
            LOG.info("Drop outbound: {}", msg);
            ReferenceCountUtil.safeRelease(msg);
        }
        else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (dropInbound.test(msg)) {
            LOG.info("Drop inbound: {}", msg);
            ReferenceCountUtil.safeRelease(msg);
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    @SuppressWarnings("unused")
    public static class DropRandomMessages implements Predicate<Object> {
        private final float lossRate;
        private long maxDrops;

        public DropRandomMessages(final float lossRate, final int maxDrops) {
            this.lossRate = lossRate;
            this.maxDrops = requirePositive(maxDrops);
        }

        @Override
        public boolean test(final Object o) {
            if (maxDrops > 0 && RandomUtil.randomInt(1, 100) <= lossRate * 100) {
                maxDrops--;
                return true;
            }
            else {
                return false;
            }
        }
    }

    @SuppressWarnings("unused")
    public static class DropEveryNthMessage implements Predicate<Object> {
        private final long n;
        private long i;

        public DropEveryNthMessage(final long n) {
            this.n = requirePositive(n);
        }

        @Override
        public boolean test(final Object o) {
            return ++i % n == 0;
        }
    }

    @SuppressWarnings("unused")
    public static class DropNthMessage implements Predicate<Object> {
        private final long n;
        private long i;

        public DropNthMessage(final long n) {
            this.n = requirePositive(n);
        }

        @Override
        public boolean test(final Object o) {
            return ++i == n;
        }
    }

    @SuppressWarnings("unused")
    public static class DropMessageRange implements Predicate<Object> {
        private final long from;
        private final long to;
        private long i;

        public DropMessageRange(final long from, final long to) {
            this.from = requirePositive(from);
            this.to = requirePositive(to);
        }

        @Override
        public boolean test(final Object o) {
            i++;
            return Math.min(from, to) <= i && i <= Math.max(from, to);
        }
    }
}
