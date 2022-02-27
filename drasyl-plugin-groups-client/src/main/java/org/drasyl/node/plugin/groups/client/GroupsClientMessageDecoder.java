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
package org.drasyl.node.plugin.groups.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.node.plugin.groups.client.message.GroupJoinMessage;
import org.drasyl.node.plugin.groups.client.message.GroupLeaveMessage;
import org.drasyl.node.plugin.groups.client.message.GroupsClientMessage;

import java.util.List;

import static org.drasyl.node.plugin.groups.client.GroupsClientMessageEncoder.MAGIC_NUMBER_JOIN;
import static org.drasyl.node.plugin.groups.client.GroupsClientMessageEncoder.MAGIC_NUMBER_LEAVE;

/**
 * Decodes {@link ByteBuf}s to {@link GroupsClientMessage}s.
 */
public class GroupsClientMessageDecoder extends MessageToMessageDecoder<OverlayAddressedMessage<ByteBuf>> {
    @Override
    public boolean acceptInboundMessage(final Object msg) {
        return msg instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) msg).content() instanceof ByteBuf;
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final OverlayAddressedMessage<ByteBuf> msg,
                          final List<Object> out) {
        final ByteBuf byteBuf = msg.content();
        byteBuf.markReaderIndex();
        final int magicNumber = byteBuf.readInt();

        switch (magicNumber) {
            case MAGIC_NUMBER_JOIN:
                out.add(msg.replace(GroupJoinMessage.of(byteBuf)));
                break;
            case MAGIC_NUMBER_LEAVE:
                out.add(msg.replace(GroupLeaveMessage.of(byteBuf)));
                break;
            default:
                byteBuf.resetReaderIndex();
                out.add(msg.retain());
        }
    }
}
