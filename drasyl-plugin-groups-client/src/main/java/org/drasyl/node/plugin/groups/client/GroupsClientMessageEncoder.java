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
import io.netty.handler.codec.MessageToMessageEncoder;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.node.plugin.groups.client.message.GroupJoinMessage;
import org.drasyl.node.plugin.groups.client.message.GroupLeaveMessage;
import org.drasyl.node.plugin.groups.client.message.GroupsClientMessage;

import java.util.List;

/**
 * Encodes {@link GroupsClientMessage}s to {@link ByteBuf}s.
 */
public class GroupsClientMessageEncoder extends MessageToMessageEncoder<OverlayAddressedMessage<GroupsClientMessage>> {
    public static final int MAGIC_NUMBER_JOIN = -578_611_198;
    public static final int MAGIC_NUMBER_LEAVE = -578_611_199;

    @Override
    public boolean acceptOutboundMessage(final Object msg) {
        return msg instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) msg).content() instanceof GroupsClientMessage;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final OverlayAddressedMessage<GroupsClientMessage> msg,
                          final List<Object> out) {
        final ByteBuf byteBuf = ctx.alloc().ioBuffer();
        if (msg.content() instanceof GroupJoinMessage) {
            byteBuf.writeInt(MAGIC_NUMBER_JOIN);
        }
        else if (msg.content() instanceof GroupLeaveMessage) {
            byteBuf.writeInt(MAGIC_NUMBER_LEAVE);
        }
        else {
            throw new IllegalArgumentException("Unknown message");
        }

        msg.content().writeTo(byteBuf);
        out.add(msg.replace(byteBuf));
    }
}
