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
package org.drasyl.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.util.logging.LogLevel;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * This handler measures the execution time of
 * {@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object)} for every
 * {@link ChannelInboundHandler}.
 */
public class SlowReadAwareHandler extends ChannelInboundHandlerAdapter {
    public static final float THRESHOLD = Float.parseFloat(SystemPropertyUtil.get("org.drasyl.channel.handler.slowReadThreshold", "0.0"));
    private static final Logger LOG = LoggerFactory.getLogger(SlowReadAwareHandler.class);
    private final LogLevel level;
    private final List<Class<? extends ChannelInboundHandler>> ignores;

    /**
     * @param ignores list of {@link ChannelInboundHandler}s to ignore
     */
    @SuppressWarnings("unchecked")
    public SlowReadAwareHandler(final LogLevel level,
                                final Class<? extends ChannelInboundHandler>... ignores) {
        this.level = requireNonNull(level);
        this.ignores = Arrays.asList(ignores);
    }

    public SlowReadAwareHandler() {
        this(LogLevel.DEBUG);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
        addHandlers(ctx);
    }

    private void addHandlers(final ChannelHandlerContext ctx) {
        if (THRESHOLD > 0.0) {
            final ChannelPipeline p = ctx.pipeline();
            final List<String> handlers = p.names();
            for (final String handler : handlers) {
                final ChannelHandlerContext context = p.context(handler);
                if (context != null && !(context.handler() instanceof SlowReadAwareHandler) && context.handler() instanceof ChannelInboundHandler && !ignores.contains(context.handler().getClass())) {
                    final SlowReadAwareBeforeHandler beforeHandler = new SlowReadAwareBeforeHandler();
                    p.addBefore(handler, null, beforeHandler);
                    p.addAfter(handler, null, new SlowReadAwareAfterHandler(handler, beforeHandler, level));
                }
            }
        }

        ctx.pipeline().remove(ctx.name());
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            addHandlers(ctx);
        }
    }

    @SuppressWarnings("java:S1258")
    private static class SlowReadAwareBeforeHandler extends ChannelInboundHandlerAdapter {
        private long readStartTime;
        private Object readStartMsg;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            readStartTime = System.nanoTime();
            readStartMsg = msg;
            ctx.fireChannelRead(msg);
        }
    }

    private static class SlowReadAwareAfterHandler extends ChannelInboundHandlerAdapter {
        private final String handler;
        private final SlowReadAwareBeforeHandler beforeHandler;
        private final LogLevel level;

        public SlowReadAwareAfterHandler(final String handler,
                                         final SlowReadAwareBeforeHandler beforeHandler,
                                         final LogLevel level) {
            this.handler = requireNonNull(handler);
            this.beforeHandler = requireNonNull(beforeHandler);
            this.level = requireNonNull(level);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (beforeHandler.readStartMsg != null) {
                final long readEndTime = System.nanoTime();
                final double readTime = (readEndTime - beforeHandler.readStartTime) / 1_000_000.0;
                if (readTime > THRESHOLD) {
                    LOG.log(level, "SLOW READ: {} took {}ms", handler, readTime);
                }
                beforeHandler.readStartTime = -1;
                beforeHandler.readStartMsg = null;
            }
            ctx.fireChannelRead(msg);
        }
    }
}
