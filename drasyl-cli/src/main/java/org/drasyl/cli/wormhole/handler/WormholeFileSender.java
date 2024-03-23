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
package org.drasyl.cli.wormhole.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.drasyl.cli.handler.ChunkedInputProgressBarHandler;
import org.drasyl.cli.wormhole.message.FileMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.File;
import java.io.PrintStream;

import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.wormhole.handler.AbstractWormholeSender.State.COMPLETED;
import static org.drasyl.cli.wormhole.handler.AbstractWormholeSender.State.ERRORED;
import static org.drasyl.cli.wormhole.handler.AbstractWormholeSender.State.TRANSFERRING;
import static org.drasyl.util.NumberUtil.numberToHumanData;

public class WormholeFileSender extends AbstractWormholeSender {
    private static final Logger LOG = LoggerFactory.getLogger(WormholeFileSender.class);
    public static final int IDLE_TIMEOUT = 10;
    public static final int PROGRESS_BAR_INTERVAL = 250;
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

        ctx.pipeline().addBefore(ctx.name(), null, new WriteTimeoutHandler(IDLE_TIMEOUT));
        ctx.pipeline().addBefore(ctx.name(), null, new ChunkedWriteHandler());
        ctx.pipeline().addBefore(ctx.name(), null, new ChunkedInputProgressBarHandler(PROGRESS_BAR_INTERVAL));

        ctx.writeAndFlush(new FileMessage(file.getName(), file.length())).addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                final ChunkedFile chunkedFile = new ChunkedFile(file, CHUNK_SIZE);

                ctx.writeAndFlush(chunkedFile).addListener((ChannelFutureListener) f2 -> {
                    if (f2.isSuccess()) {
                        state = COMPLETED;
                        out.println("file sent");
                        f2.channel().close();
                    }
                    else if (state == TRANSFERRING) {
                        state = ERRORED;
                        f2.channel().pipeline().fireExceptionCaught(f2.cause());
                    }
                });
            }
            else if (state == TRANSFERRING) {
                state = ERRORED;
                f.channel().pipeline().fireExceptionCaught(f.cause());
            }
        });
    }

    @Override
    protected void abortTransfer(final ChannelHandlerContext ctx) {
        if (ctx.pipeline().get(ChunkedInputProgressBarHandler.class) != null) {
            ctx.pipeline().remove(ChunkedInputProgressBarHandler.class);
        }

        super.abortTransfer(ctx);
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
