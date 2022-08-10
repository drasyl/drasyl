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
package org.drasyl.cli.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.stream.ChunkedInput;
import io.netty.util.concurrent.ScheduledFuture;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;

import java.text.DecimalFormat;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * This handler shows a progress bar for {@link ChunkedInput}s written to the channel.
 */
public class ChunkedInputProgressBarHandler extends ChannelDuplexHandler {
    private static final DecimalFormat PROGRESS_BAR_SPEED_FORMAT = new DecimalFormat("0.00");
    private final int refreshInterval;
    private final ProgressBarBuilder progressBarBuilder;
    private ScheduledFuture<?> refreshTask;
    private ProgressBar progressBar;

    public ChunkedInputProgressBarHandler(final ProgressBarBuilder progressBarBuilder,
                                          final int refreshInterval) {
        this.progressBarBuilder = requireNonNull(progressBarBuilder);
        this.refreshInterval = requirePositive(refreshInterval);
    }

    public ChunkedInputProgressBarHandler(final int refreshInterval) {
        this(
                new ProgressBarBuilder()
                        .setUnit("MB", 1_000_000)
                        .setStyle(ProgressBarStyle.ASCII)
                        .setUpdateIntervalMillis(refreshInterval)
                        .showSpeed(PROGRESS_BAR_SPEED_FORMAT),
                refreshInterval
        );
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }
        if (progressBar != null) {
            progressBar.close();
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof ChunkedInput) {
            writeChunkedInput(ctx, promise, (ChunkedInput<?>) msg);
        }
        ctx.write(msg, promise);
    }

    private void writeChunkedInput(final ChannelHandlerContext ctx,
                                   final ChannelPromise promise,
                                   final ChunkedInput<?> chunkedInput) {
        if (progressBar == null) {
            // create progress bar
            progressBarBuilder.setInitialMax(chunkedInput.length());
            progressBar = progressBarBuilder.build();

            // create task to refresh progress bar periodically
            refreshTask = ctx.executor().scheduleAtFixedRate(() -> progressBar.stepTo(chunkedInput.progress()), 0, refreshInterval, MILLISECONDS);

            // stop task and complete progress bar
            promise.addListener((ChannelFutureListener) future -> {
                refreshTask.cancel(false);
                progressBar.stepTo(chunkedInput.progress());
                progressBar.close();
                progressBar = null;
            });
        }
    }
}
