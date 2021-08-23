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
package org.drasyl.plugin.groups.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.plugin.groups.client.message.GroupsClientMessage;

import java.io.InputStream;
import java.util.List;

import static org.drasyl.plugin.groups.client.GroupsClientMessageEncoder.MAGIC_NUMBER;
import static org.drasyl.util.JSONUtil.JACKSON_READER;

/**
 * Decodes {@link ByteBuf}s with magic number {@link GroupsClientMessageEncoder#MAGIC_NUMBER} to
 * {@link GroupsClientMessage}s.
 */
public class GroupsClientMessageDecoder extends MessageToMessageDecoder<AddressedMessage<?, ?>> {
    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final AddressedMessage<?, ?> msg,
                          final List<Object> out) throws Exception {
        if (msg.message() instanceof ByteBuf) {
            final ByteBuf byteBuf = (ByteBuf) msg.message();
            byteBuf.markReaderIndex();
            final int magicNumber = byteBuf.readInt();
            if (magicNumber == MAGIC_NUMBER) {
                try (final InputStream inputStream = new ByteBufInputStream(byteBuf)) {
                    out.add(new AddressedMessage<>(JACKSON_READER.readValue(inputStream, GroupsClientMessage.class), msg.address()));
                }
            }
            else {
                byteBuf.resetReaderIndex();
                out.add(msg.retain());
            }
        }
        else {
            msg.retain();
        }
    }
}
