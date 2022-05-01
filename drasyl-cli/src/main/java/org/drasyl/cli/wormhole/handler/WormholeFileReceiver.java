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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.drasyl.cli.handler.InboundByteBufsProgressBarHandler;
import org.drasyl.cli.wormhole.message.FileMessage;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.wormhole.handler.WormholeFileSender.IDLE_TIMEOUT;
import static org.drasyl.cli.wormhole.handler.WormholeFileSender.PROGRESS_BAR_INTERVAL;
import static org.drasyl.util.NumberUtil.numberToHumanData;
import static org.drasyl.util.Preconditions.requirePositive;

public class WormholeFileReceiver extends SimpleChannelInboundHandler<ByteBuf> {
    private final PrintStream out;
    private final File file;
    private final long length;
    private RandomAccessFile randomAccessFile;

    public WormholeFileReceiver(final PrintStream out,
                                final File file,
                                final long length) {
        this.out = requireNonNull(out);
        this.file = requireNonNull(file);
        this.length = requirePositive(length);
    }

    public WormholeFileReceiver(final PrintStream out,
                                final FileMessage fileMessage) {
        this(out, new File(fileMessage.getName()), fileMessage.getLength());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        out.println("Receiving file (" + numberToHumanData(length) + ") into: " + file.getName());

        if (file.exists()) {
            ctx.fireExceptionCaught(new FileExistException(file.getName()));
            return;
        }

        try {
            file.createNewFile(); // NOSONAR
            randomAccessFile = new RandomAccessFile(file, "rw");
        }
        catch (final IOException e) {
            ctx.fireExceptionCaught(e);
            return;
        }

        ctx.pipeline().addBefore(ctx.name(), null, new InboundByteBufsProgressBarHandler(length, PROGRESS_BAR_INTERVAL));
        ctx.pipeline().addBefore(ctx.name(), null, new ReadTimeoutHandler(IDLE_TIMEOUT));
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        if (randomAccessFile != null) {
            try {
                randomAccessFile.close();
            }
            catch (final IOException e) {
                // ignore
            }
        }

        ctx.fireChannelInactive();
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final ByteBuf msg) throws Exception {
        final long currentFileLength = file.length();
        final int readableBytes = msg.readableBytes();
        final ByteBuffer byteBuffer = msg.nioBuffer();
        while (byteBuffer.hasRemaining()) {
            randomAccessFile.getChannel().position(currentFileLength);
            randomAccessFile.getChannel().write(byteBuffer);
        }

        if (currentFileLength + readableBytes == length) {
            out.println("Received file written to " + file.getName());

            ctx.pipeline().close();
        }
    }

    public static class FileExistException extends Exception {
        public FileExistException(final String name) {
            super("Refusing to overwrite existing '" + name + "'");
        }
    }
}
