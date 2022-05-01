package org.drasyl.cli.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;

import java.text.DecimalFormat;

import static java.util.Objects.requireNonNull;

/**
 * Once added to the pipeline, this handler shows a progress bar keeping track of the number of
 * received readable bytes.
 */
public class InboundByteBufsProgressBarHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final DecimalFormat PROGRESS_BAR_SPEED_FORMAT = new DecimalFormat("0.00");
    private final ProgressBarBuilder progressBarBuilder;
    private ProgressBar progressBar;

    public InboundByteBufsProgressBarHandler(final ProgressBarBuilder progressBarBuilder) {
        super(false);
        this.progressBarBuilder = requireNonNull(progressBarBuilder);
    }

    public InboundByteBufsProgressBarHandler(final long totalLength, final int refreshInterval) {
        this(
                new ProgressBarBuilder()
                        .setInitialMax(totalLength)
                        .setUnit("MB", 1_000_000)
                        .setStyle(ProgressBarStyle.ASCII)
                        .setUpdateIntervalMillis(refreshInterval)
                        .showSpeed(PROGRESS_BAR_SPEED_FORMAT)
        );
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        if (progressBar != null) {
            progressBar.close();
        }
        ctx.fireChannelInactive();
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final ByteBuf msg) {
        // create progress bar
        if (progressBar == null) {
            progressBar = progressBarBuilder.build();
        }
        progressBar.stepBy(msg.readableBytes());
        if (progressBar.getCurrent() == progressBar.getMax()) {
            progressBar.close();
            ctx.pipeline().remove(this);
        }
        ctx.fireChannelRead(msg);
    }
}
