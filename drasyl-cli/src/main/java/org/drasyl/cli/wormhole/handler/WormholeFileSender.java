/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.wormhole.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.concurrent.Future;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.drasyl.cli.wormhole.message.FileMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.File;
import java.io.PrintStream;
import java.text.DecimalFormat;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.NumberUtil.numberToHumanData;

public class WormholeFileSender extends AbstractWormholeSender {
    private static final Logger LOG = LoggerFactory.getLogger(WormholeFileSender.class);
    public static final int IDLE_TIMEOUT = 10;
    public static final int PROGRESS_BAR_INTERVAL = 250;
    public static final DecimalFormat PROGRESS_BAR_SPEED_FORMAT = new DecimalFormat("0.00");
    // mtu: 1432
    // protocol overhead: 185 bytes
    private static final int CHUNK_SIZE = 1432 - 185;
    private final File file;

    public WormholeFileSender(final PrintStream out,
                              final String password,
                              final File file) {
        super(out, password);
        this.file = requireNonNull(file);
    }

    @SuppressWarnings("java:S1905")
    @Override
    protected void transferPayload(final ChannelHandlerContext ctx) {
        out.println("Sending file (" + numberToHumanData(file.length()) + "): " + file.getName());

        ctx.writeAndFlush(new FileMessage(file.getName(), file.length())).addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                final ChunkedFile chunkedFile = new ChunkedFile(file, CHUNK_SIZE);

                final ProgressBar progressBar = new ProgressBarBuilder()
                        .setInitialMax(file.length())
                        .setUnit("MB", 1_000_000)
                        .setStyle(ProgressBarStyle.ASCII)
                        .setUpdateIntervalMillis(PROGRESS_BAR_INTERVAL)
                        .showSpeed(PROGRESS_BAR_SPEED_FORMAT)
                        .build();

                final Future<?> progressTask = ctx.executor().scheduleAtFixedRate(() -> progressBar.stepTo(chunkedFile.progress()), 0, PROGRESS_BAR_INTERVAL, MILLISECONDS);

                ctx.writeAndFlush(chunkedFile).addListener((ChannelFutureListener) f2 -> {
                    progressTask.cancel(false);
                    if (f2.isSuccess()) {
                        progressBar.stepTo(chunkedFile.progress());
                        progressBar.close();
                        out.println("file sent");
                        f2.channel().close();
                    }
                    else {
                        progressBar.close();
                        f2.channel().pipeline().fireExceptionCaught(f2.cause());
                    }
                });
            }
            else {
                f.channel().pipeline().fireExceptionCaught(f.cause());
            }
        });

        ctx.pipeline().addBefore(ctx.name(), null, new WriteTimeoutHandler(IDLE_TIMEOUT));
        ctx.pipeline().addBefore(ctx.name(), null, new ChunkedWriteHandler());
        ctx.pipeline().remove(ctx.name());
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
