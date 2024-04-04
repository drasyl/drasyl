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
import io.netty.util.internal.StringUtil;
import org.drasyl.handler.remote.UdpServer.UdpServerBound;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.Objects.requireNonNull;

/**
 * This handler creates files in a specific path when specific channel events occur, so that other
 * processes that monitor this path can be informed.
 */
public class FileNotifierHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(FileNotifierHandler.class);
    private final Path dir;

    public FileNotifierHandler(final Path dir) {
        this.dir = requireNonNull(dir);
    }

    public FileNotifierHandler() {
        this(Path.of("drasyl-ipc/"));
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws IOException {
        cleanupFiles(ctx);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) throws IOException {
        cleanupFiles(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws IOException {
        cleanupFiles(ctx);

        ctx.fireChannelInactive();
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) throws IOException {
        if (evt instanceof UdpServerBound) {
            createFile(ctx, evt);
        }

        ctx.fireUserEventTriggered(evt);
    }

    private void createFile(final ChannelHandlerContext ctx, final Object evt) throws IOException {
        final SocketAddress localAddress = ctx.channel().localAddress();
        if (localAddress != null) {
            final Path filePath = dir.resolve(localAddress + "." + StringUtil.simpleClassName(evt));
            final File file = filePath.toFile();
            if (file.createNewFile()) {
                LOG.debug("Created `{}`.", file.getAbsolutePath());
            }
            else {
                LOG.error("Unable to create `{}`.", file.getAbsolutePath());
            }
        }
    }

    private void cleanupFiles(final ChannelHandlerContext ctx) throws IOException {
        final SocketAddress localAddress = ctx.channel().localAddress();
        if (localAddress != null) {
            try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, localAddress + ".*")) {
                for (final Path entry : stream) {
                    if (Files.isRegularFile(entry, NOFOLLOW_LINKS)) {
                        Files.delete(entry);
                        LOG.debug("Deleted `{}`.", entry);
                    }
                }
            }
        }
    }
}
