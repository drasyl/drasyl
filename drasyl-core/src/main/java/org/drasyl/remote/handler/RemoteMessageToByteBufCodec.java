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
package org.drasyl.remote.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.remote.protocol.InvalidMessageFormatException;
import org.drasyl.remote.protocol.PartialReadMessage;
import org.drasyl.remote.protocol.RemoteMessage;

import java.util.List;

/**
 * This codec converts {@link RemoteMessage}s to {@link ByteBuf}s an vice vera.
 */
@SuppressWarnings("java:S110")
@Sharable
public final class RemoteMessageToByteBufCodec extends MessageToMessageCodec<AddressedMessage<?, ?>, AddressedMessage<?, ?>> {
    public static final RemoteMessageToByteBufCodec INSTANCE = new RemoteMessageToByteBufCodec();

    private RemoteMessageToByteBufCodec() {
        // singleton
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final AddressedMessage<?, ?> msg,
                          final List<Object> out) throws InvalidMessageFormatException {
        if (msg.message() instanceof RemoteMessage) {
            final RemoteMessage remoteMsg = (RemoteMessage) msg.message();

            final ByteBuf buffer = ctx.alloc().ioBuffer();
            try {
                remoteMsg.writeTo(buffer);
                out.add(new AddressedMessage<>(buffer, msg.address()));
            }
            catch (final InvalidMessageFormatException e) {
                buffer.release();
                throw e;
            }
        }
        else {
            // pass through message
            out.add(msg.retain());
        }
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final AddressedMessage<?, ?> msg,
                          final List<Object> out) throws InvalidMessageFormatException {
        if (msg.message() instanceof ByteBuf) {
            final ByteBuf byteBufMsg = (ByteBuf) msg.message();

            out.add(new AddressedMessage<>(PartialReadMessage.of(byteBufMsg.retain()), msg.address()));
        }
        else {
            // pass through message
            out.add(msg.retain());
        }
    }
}
