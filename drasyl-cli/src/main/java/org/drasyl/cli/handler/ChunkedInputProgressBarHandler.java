package org.drasyl.cli.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.stream.ChunkedFile;
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
 * This handler shows a progress bar once a {@link ChunkedInput} is written to the channel.
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
            if (chunkedInput instanceof ChunkedFile) {
                // it's a file. use filesize as unit
                progressBarBuilder.setUnit("MB", 1_000_000);
            }
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
