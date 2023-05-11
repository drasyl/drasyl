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
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.SocketAddress;
import java.util.List;

public class SNTPCodec extends MessageToMessageCodec<AddressedEnvelope<ByteBuf, SocketAddress>, AddressedEnvelope<SNTPMessage, SocketAddress>> {
    private static final Logger LOG = LoggerFactory.getLogger(SNTPCodec.class);

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final AddressedEnvelope<SNTPMessage, SocketAddress> msg,
                          final List<Object> out) throws Exception {
        final ByteBuf buf = ctx.alloc().buffer(SNTPMessage.SIZE, SNTPMessage.SIZE);
        final SNTPMessage sntpMessage = msg.content();

        buf.writeByte(sntpMessage.getMode() | (sntpMessage.getVersionNumber() << 3));
        buf.writerIndex(SNTPMessage.TRANSMIT_TIMESTAMP_OFFSET);
        buf.writeLong(SNTPMessage.toNTPTime(sntpMessage.getTransmitTimestamp()));

        out.add(new DefaultAddressedEnvelope<>(buf, msg.sender()));
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final AddressedEnvelope<ByteBuf, SocketAddress> msg,
                          final List<Object> out) throws Exception {
        if (msg.content().readableBytes() >= SNTPMessage.TRANSMIT_TIMESTAMP_OFFSET) {

            LOG.debug("{}", msg.content()); // TODO: Remove me!
        }
    }
}
