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
package org.drasyl.handler.arq.gbn;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.internal.StringUtil;
import org.drasyl.util.UnsignedInteger;

import java.util.List;

/**
 * Encodes {@link GoBackNArqMessage}s to {@link ByteBuf}s and vice versa.
 */
public class GoBackNArqCodec extends MessageToMessageCodec<ByteBuf, GoBackNArqMessage> {
    public static final int MAGIC_NUMBER_DATA = 360_023_952;
    public static final int MAGIC_NUMBER_FIRST_DATA = 360_023_953;
    public static final int MAGIC_NUMBER_RST = 360_023_954;
    public static final int MAGIC_NUMBER_ACK = 360_023_955;
    public static final int MAGIC_NUMBER_LAST_DATA = 360_023_956;
    // magic number: 4 bytes
    // sequence number: 4 bytes
    public static final int MIN_MESSAGE_LENGTH = 8;

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final GoBackNArqMessage msg,
                          final List<Object> out) throws Exception {
        if (msg instanceof GoBackNArqLastData) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_LAST_DATA);
            buf.writeBytes(msg.sequenceNo().toBytes());
            buf.writeBytes(((GoBackNArqLastData) msg).content());
            out.add(buf);
        }
        else if (msg instanceof GoBackNArqData) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_DATA);
            buf.writeBytes(msg.sequenceNo().toBytes());
            buf.writeBytes(((GoBackNArqData) msg).content());
            out.add(buf);
        }
        else if (msg instanceof GoBackNArqAck) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_ACK);
            buf.writeBytes(msg.sequenceNo().toBytes());
            out.add(buf);
        }
        else if (msg instanceof GoBackNArqFirstData) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_FIRST_DATA);
            buf.writeBytes(msg.sequenceNo().toBytes());
            buf.writeBytes(((GoBackNArqFirstData) msg).content());
            out.add(buf);
        }
        else if (msg instanceof GoBackNArqRst) {
            final ByteBuf buf = ctx.alloc().buffer();
            buf.writeInt(MAGIC_NUMBER_RST);
            buf.writeBytes(msg.sequenceNo().toBytes());
            out.add(buf);
        }
        else {
            throw new EncoderException("Unknown GoBackNArqMessage type: " + StringUtil.simpleClassName(msg));
        }
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) throws Exception {
        if (in.readableBytes() >= MIN_MESSAGE_LENGTH) {
            in.markReaderIndex();
            final int magicNumber = in.readInt();
            final UnsignedInteger sequenceNo = UnsignedInteger.of(in.readUnsignedInt());
            switch (magicNumber) {
                case MAGIC_NUMBER_DATA: {
                    out.add(new GoBackNArqData(sequenceNo, in.retain()));
                    break;
                }
                case MAGIC_NUMBER_ACK: {
                    out.add(new GoBackNArqAck(sequenceNo));
                    break;
                }
                case MAGIC_NUMBER_LAST_DATA: {
                    out.add(new GoBackNArqLastData(sequenceNo, in.retain()));
                    break;
                }
                case MAGIC_NUMBER_FIRST_DATA: {
                    out.add(new GoBackNArqFirstData(in.retain()));
                    break;
                }
                case MAGIC_NUMBER_RST: {
                    out.add(new GoBackNArqRst());
                    break;
                }
                default: {
                    // wrong magic number -> pass through message
                    in.resetReaderIndex();
                    out.add(in.retain());
                    break;
                }
            }
        }
        else {
            // too short -> pass through message
            out.add(in.retain());
        }
    }
}
