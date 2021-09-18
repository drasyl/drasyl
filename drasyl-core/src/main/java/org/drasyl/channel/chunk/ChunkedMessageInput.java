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
package org.drasyl.channel.chunk;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.stream.ChunkedInput;
import org.drasyl.util.RandomUtil;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * Wraps each chunk of the specified {@link ChunkedInput<ByteBuf>} into a series of sortable {@link
 * MessageChunk}s, allowing the receiver to reassemble the {@link ByteBuf} in correct order. Useful
 * for protocols that does provide message ordering (like {@code UDP} or drasyl).
 * <blockquote>
 * <pre>
 *  {@link ChannelPipeline} p = ...;
 *  ...
 *  p.addLast("encoder", <b>{@link MessageChunkEncoder#INSTANCE}()</b>);
 *  p.addLast("chunked_write", <b>new {@link io.netty.handler.stream.ChunkedWriteHandler}</b>);
 *  p.addLast("decoder", <b>{@link MessageChunkDecoder#INSTANCE}()</b>);
 *  p.addLast("buffer", <b>new {@link MessageChunksBuffer}(65536, 5000)</b>);
 *  p.addLast("aggregator", <b>new {@link ChunkedMessageAggregator}(65536)</b>);
 *  ...
 *  p.addLast("handler", new MyReassembledMessageHandler());
 *  </pre>
 * </blockquote>
 */
public class ChunkedMessageInput implements ChunkedInput<MessageChunk> {
    private final ChunkedInput<ByteBuf> input;
    private final byte id;
    private byte chunkNo;

    ChunkedMessageInput(final ChunkedInput<ByteBuf> input,
                        final byte id,
                        final byte chunkNo) {
        this.input = requireNonNull(input);
        this.id = id;
        this.chunkNo = chunkNo;
    }

    public ChunkedMessageInput(final ChunkedInput<ByteBuf> input) {
        this(input, RandomUtil.randomByte(), (byte) 0);
    }

    @Override
    public boolean isEndOfInput() throws Exception {
        return input.isEndOfInput();
    }

    @Override
    public void close() throws Exception {
        input.close();
    }

    @Override
    public MessageChunk readChunk(final ChannelHandlerContext ctx) throws Exception {
        return readChunk(ctx.alloc());
    }

    @SuppressWarnings({ "java:S881", "java:S1142" })
    @Override
    public MessageChunk readChunk(final ByteBufAllocator allocator) throws Exception {
        if (input.isEndOfInput()) {
            return null;
        }
        else {
            final ByteBuf buf = input.readChunk(allocator);
            if (buf == null) {
                return null;
            }

            if (!input.isEndOfInput()) {
                if (chunkNo == -1) {
                    throw new IOException("chunkNo overflow (256 chunks maximum are allowed).");
                }

                return new MessageChunk(id, chunkNo++, buf);
            }
            else {
                return new LastMessageChunk(id, chunkNo, buf);
            }
        }
    }

    @Override
    public long length() {
        return input.length();
    }

    @Override
    public long progress() {
        return input.progress();
    }
}
