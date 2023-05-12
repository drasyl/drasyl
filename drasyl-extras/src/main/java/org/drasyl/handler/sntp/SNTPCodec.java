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
package org.drasyl.handler.sntp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.List;

public class SNTPCodec extends MessageToMessageCodec<DatagramPacket, SNTPMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(SNTPCodec.class);

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final SNTPMessage msg,
                          final List<Object> out) throws Exception {
        final ByteBuf buf = ctx.alloc().buffer(SNTPMessage.SIZE, SNTPMessage.SIZE);

        buf.writeByte(msg.getMode() | (msg.getVersionNumber() << 3));
        buf.writerIndex(SNTPMessage.TRANSMIT_TIMESTAMP_OFFSET);
        buf.writeLong(SNTPMessage.toNTPTime(msg.getTransmitTimestamp()));

        out.add(buf);
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final DatagramPacket msg,
                          final List<Object> out) throws Exception {
        final ByteBuf buf = msg.content();

        if (buf.readableBytes() >= SNTPMessage.SIZE) {
            // read first byte with LE, VN and mode
            final byte fByte = buf.readByte();
            final int li = (fByte >> 3) & 0x3;
            final int vn = (fByte >> 3) & 0x7;
            final int mode = (fByte) & 0x7;

            // We do not want this kind of NTP servers
            if (li == SNTPMessage.LI_NOT_SYNC) {
                return;
            }

            final int stratum = buf.readByte();
            final int poll = buf.readByte();
            final int precision = buf.readByte();
            final float rootDelay = buf.readFloat();
            final float rootDispersion = buf.readFloat();
            final int referenceIdentifier = buf.readInt();
            final long referenceTimestamp = buf.readLong();
            final long originateTimestamp = buf.readLong();
            final long receiveTimestamp = buf.readLong();
            final long transmitTimestamp = buf.readLong();

            out.add(SNTPMessage.of(li, vn, mode, stratum, poll, precision, rootDelay, rootDispersion, referenceIdentifier, referenceTimestamp, originateTimestamp, receiveTimestamp, transmitTimestamp));
        }
    }
}
