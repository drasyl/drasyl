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
package org.drasyl.handler.crypto;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.handler.remote.protocol.InvalidMessageFormatException;
import org.drasyl.handler.remote.protocol.Nonce;

import java.util.List;

@ChannelHandler.Sharable
public class ArmeHeaderCodec extends MessageToMessageCodec<ByteBuf, ArmHeader> {
    public static final ArmeHeaderCodec INSTANCE = new ArmeHeaderCodec();

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final ArmHeader msg,
                          final List<Object> out) throws Exception {
        final ByteBuf byteBuf = ctx.alloc().ioBuffer();
        byteBuf.writeBytes(msg.getAgreementId().toBytes())
                .writeBytes(msg.getNonce().toByteArray());

        out.add(Unpooled.wrappedBuffer(byteBuf, msg.content().retain()));
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf msg,
                          final List<Object> out) throws Exception {
        if (msg.readableBytes() < ArmHeader.MIN_LENGTH) {
            throw new InvalidMessageFormatException("ArmHeader requires " + ArmHeader.MIN_LENGTH + " readable bytes. Only " + msg.readableBytes() + " left.");
        }

        final AgreementId agreementId;
        final Nonce nonce;

        final byte[] agreementIdBuffer = new byte[AgreementId.ID_LENGTH];
        msg.readBytes(agreementIdBuffer);
        agreementId = AgreementId.of(agreementIdBuffer);

        final byte[] nonceBuffer = new byte[Nonce.NONCE_LENGTH];
        msg.readBytes(nonceBuffer);
        nonce = Nonce.of(nonceBuffer);

        out.add(ArmHeader.of(agreementId, nonce, msg.retain()));
    }
}
