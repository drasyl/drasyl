/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.peer.connection.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import org.drasyl.peer.connection.message.Message;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;

public class JacksonCodec extends ByteToMessageCodec<Message> {
    public JacksonCodec() {
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
        ByteBufOutputStream byteBufOutputStream = new ByteBufOutputStream(out);
        JACKSON_WRITER.writeValue((OutputStream) byteBufOutputStream, msg);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx,
                          ByteBuf in,
                          List<Object> out) throws Exception {
        ByteBufInputStream byteBufInputStream = new ByteBufInputStream(in);
        out.add(JACKSON_READER.readValue((InputStream) byteBufInputStream, Message.class));
    }
}
