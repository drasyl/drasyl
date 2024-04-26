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
import org.drasyl.node.plugin.groups.client.message.GroupJoinFailedMessage;
import org.drasyl.node.plugin.groups.client.message.GroupWelcomeMessage;
import org.drasyl.node.plugin.groups.client.message.GroupsServerMessage;
import org.drasyl.node.plugin.groups.client.message.MemberJoinedMessage;
import org.drasyl.node.plugin.groups.client.message.MemberLeftMessage;

import java.util.List;

import static org.drasyl.node.plugin.groups.client.GroupsServerMessageEncoder.MAGIC_NUMBER_FAILED;
import static org.drasyl.node.plugin.groups.client.GroupsServerMessageEncoder.MAGIC_NUMBER_JOINED;
import static org.drasyl.node.plugin.groups.client.GroupsServerMessageEncoder.MAGIC_NUMBER_LEFT;
import static org.drasyl.node.plugin.groups.client.GroupsServerMessageEncoder.MAGIC_NUMBER_WELCOME;

/**
 * Decodes {@link ByteBuf}s  to {@link GroupsServerMessage}s.
 */
class GroupsServerMessageDecoder extends MessageToMessageDecoder<ByteBuf> {
    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf byteBuf,
                          final List<Object> out) {
        byteBuf.markReaderIndex();
        final int magicNumber = byteBuf.readInt();

        switch (magicNumber) {
            case MAGIC_NUMBER_JOINED:
                out.add(MemberJoinedMessage.of(byteBuf));
                break;
            case MAGIC_NUMBER_LEFT:
                out.add(MemberLeftMessage.of(byteBuf));
                break;
            case MAGIC_NUMBER_WELCOME:
                out.add(GroupWelcomeMessage.of(byteBuf));
                break;
            case MAGIC_NUMBER_FAILED:
                out.add(GroupJoinFailedMessage.of(byteBuf));
                break;
            default:
                byteBuf.resetReaderIndex();
                out.add(byteBuf.retain());
        }
    }
}
