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
package org.drasyl.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

import static org.drasyl.util.Preconditions.requirePositive;

public class MagicNumberPrepender extends MessageToMessageEncoder<ByteBuf> {
    private final int magicNumberLength;
    private final long magicNumber;

    public MagicNumberPrepender(final int magicNumberLength, final long magicNumber) {
        this.magicNumberLength = requirePositive(magicNumberLength);
        this.magicNumber = magicNumber;
        switch (magicNumberLength) {
            case 1:
                if (magicNumber < -128 || magicNumber > 127) {
                    throw new IllegalArgumentException("magic number does not fit into a byte: " + magicNumber);
                }
                break;
            case 2:
                if (magicNumber < -32_768 || magicNumber > 32_767) {
                    throw new IllegalArgumentException("magic number does not fit into a byte: " + magicNumber);
                }
                break;
            case 3:
                if (magicNumber < -8_388_608 || magicNumber > 8_388_607) {
                    throw new IllegalArgumentException("magic number does not fit into a byte: " + magicNumber);
                }
                break;
            case 4:
            case 8:
                break;
            default:
                throw new IllegalArgumentException("magicNumberLength must be either 1, 2, 3, 4, or 8: " + magicNumberLength);
        }
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final ByteBuf msg,
                          final List<Object> out) throws Exception {
        switch (magicNumberLength) {
            case 1:
                out.add(ctx.alloc().buffer(1).writeByte((byte) magicNumber));
                break;
            case 2:
                out.add(ctx.alloc().buffer(2).writeShort((short) magicNumber));
                break;
            case 3:
                out.add(ctx.alloc().buffer(3).writeMedium((int) magicNumber));
                break;
            case 4:
                out.add(ctx.alloc().buffer(4).writeInt((int) magicNumber));
                break;
            case 8:
                out.add(ctx.alloc().buffer(8).writeLong(magicNumber));
                break;
            default:
                throw new Error("should not reach here");
        }
        out.add(msg.retain());
    }
}
