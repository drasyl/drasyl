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
import io.netty.channel.SimpleChannelInboundHandler;

import static org.drasyl.util.Preconditions.requirePositive;

public class MagicNumberBasedFrameDecoder extends SimpleChannelInboundHandler<ByteBuf> {
    private final int magicNumberLength;
    private final long magicNumber;

    public MagicNumberBasedFrameDecoder(final int magicNumberLength, final long magicNumber) {
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
    protected void channelRead0(final ChannelHandlerContext ctx, final ByteBuf buf) throws Exception {
        if (buf.readableBytes() >= magicNumberLength) {
            buf.markReaderIndex();
            final long magicNumber;
            switch (magicNumberLength) {
                case 1:
                    magicNumber = buf.readByte();
                    break;
                case 2:
                    magicNumber = buf.readShort();
                    break;
                case 3:
                    magicNumber = buf.readMedium();
                    break;
                case 4:
                    magicNumber = buf.readInt();
                    break;
                case 8:
                    magicNumber = buf.readLong();
                    break;
                default:
                    throw new Error("should not reach here");
            }

            if (this.magicNumber == magicNumber) {
                ctx.fireChannelRead(buf);
            }
            else {
                // unexpected magic number
                buf.resetReaderIndex();
                ctx.fireChannelRead(buf); // SKIP next handler
            }
        }
        else {
            // message too short
            ctx.fireChannelRead(buf); // SKIP next handler
        }

    }
}
