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
package org.drasyl.handler.plugin.groups.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.handler.plugin.groups.client.message.GroupsServerMessage;

import java.io.OutputStream;
import java.util.List;

import static org.drasyl.util.JSONUtil.JACKSON_WRITER;

/**
 * Encodes {@link GroupsServerMessage}s to {@link ByteBuf}s with magic number {@link
 * #MAGIC_NUMBER}.
 */
public class GroupsServerMessageEncoder extends MessageToMessageEncoder<AddressedMessage<GroupsServerMessage, ?>> {
    public static final int MAGIC_NUMBER = 578_611_197;

    @Override
    public boolean acceptOutboundMessage(final Object msg) {
        return msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof GroupsServerMessage;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final AddressedMessage<GroupsServerMessage, ?> msg,
                          final List<Object> out) throws Exception {
        final ByteBuf byteBuf = ctx.alloc().ioBuffer();
        byteBuf.writeInt(MAGIC_NUMBER);
        try (final OutputStream outputStream = new ByteBufOutputStream(byteBuf)) {
            JACKSON_WRITER.writeValue(outputStream, msg.message());
        }
        out.add(msg.replace(byteBuf));
    }
}
