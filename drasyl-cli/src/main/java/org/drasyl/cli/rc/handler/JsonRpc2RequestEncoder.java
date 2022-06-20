/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.rc.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.drasyl.cli.node.message.JsonRpc2Request;

import java.io.OutputStream;

import static java.util.Objects.requireNonNull;

/**
 * Encodes {@link JsonRpc2Request}s to bytes.
 */
public class JsonRpc2RequestEncoder extends MessageToByteEncoder<JsonRpc2Request> {
    private final ObjectWriter writer;

    public JsonRpc2RequestEncoder(final ObjectWriter writer) {
        this.writer = requireNonNull(writer);
    }

    public JsonRpc2RequestEncoder(final ObjectMapper mapper) {
        this(mapper.writer());
    }

    public JsonRpc2RequestEncoder() {
        this(new ObjectMapper());
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final JsonRpc2Request msg,
                          final ByteBuf out) throws Exception {
        try (final OutputStream outputStream = new ByteBufOutputStream(out)) {
            writer.writeValue(outputStream, msg);
        }
    }
}
