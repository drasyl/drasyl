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
package org.drasyl.handler.arq.stopandwait;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.internal.StringUtil;

import java.util.List;

/**
 * Encodes {@link StopAndWaitArqMessage}s to {@link ByteBuf}s and vice versa.
 */
public final class StopAndWaitArqCodec extends MessageToMessageCodec<ByteBuf, StopAndWaitArqMessage> {
    public static final int MAGIC_NUMBER_DATA = 523_370_708;
    public static final int MAGIC_NUMBER_ACK = 523_370_709;
    // magic number: 4 bytes
    // sequence number: 1 byte
    public static final int MIN_MESSAGE_LENGTH = 5;

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final StopAndWaitArqMessage msg,
                          final List<Object> out) throws EncoderException {
        if (msg instanceof StopAndWaitArqData) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_DATA);
            buf.writeBoolean(msg.sequenceNo());
            buf.writeBytes(((StopAndWaitArqData) msg).content());
            out.add(buf);
        }
        else if (msg instanceof StopAndWaitArqAck) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_ACK);
            buf.writeBoolean(msg.sequenceNo());
            out.add(buf);
        }
        else {
            throw new EncoderException("Unknown StopAndWaitMessage type: " + StringUtil.simpleClassName(msg));
        }
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) throws Exception {
        if (in.readableBytes() >= MIN_MESSAGE_LENGTH) {
            in.markReaderIndex();
            final int magicNumber = in.readInt();
            final boolean sequenceNo = in.readBoolean();
            if (MAGIC_NUMBER_DATA == magicNumber) {
                out.add(new StopAndWaitArqData(sequenceNo, in.retain()));
            }
            else if (MAGIC_NUMBER_ACK == magicNumber) {
                out.add(sequenceNo ? StopAndWaitArqAck.STOP_AND_WAIT_ACK_1 : StopAndWaitArqAck.STOP_AND_WAIT_ACK_0);
            }
            else {
                // wrong magic number -> pass through message
                in.resetReaderIndex();
                out.add(in.retain());
            }
        }
        else {
            // too short -> pass through message
            out.add(in.retain());
        }
    }
}
