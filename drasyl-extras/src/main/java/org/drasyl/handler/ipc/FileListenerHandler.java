/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.ipc;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * This handler listens to files in a specific path and will then fire specific channel event.
 */
public class FileListenerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(FileListenerHandler.class);
    private final Path dir;

    public FileListenerHandler(final Path dir) {
        this.dir = requireNonNull(dir);
    }

    public FileListenerHandler() {
        this(Path.of("drasyl-ipc/"));
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        final Path path = dir.resolve("all.passChannelActive");

        if (Files.exists(path)) {
            LOG.info("File `{}` exist. Start Node.", path);
            ctx.fireChannelActive();

            checkCloseFile(ctx);
        }
        else {
            LOG.info("File does not `{}` exist. Wait...", path);

            ctx.executor().schedule(() -> channelActive(ctx), 1000, MILLISECONDS);
        }
    }

    private void checkCloseFile(ChannelHandlerContext ctx) {
        ctx.executor().schedule(() -> {
            final Path path = dir.resolve("all.passClose");

            if (Files.exists(path)) {
                LOG.info("File `{}` exist. Stop Node.", path);
                ctx.channel().close();
            }
            else {
                checkCloseFile(ctx);
            }
        }, 1000, MILLISECONDS);
    }
}
