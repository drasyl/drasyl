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
