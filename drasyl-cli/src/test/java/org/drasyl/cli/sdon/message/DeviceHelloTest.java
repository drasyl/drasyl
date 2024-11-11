/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.sdon.message;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.handler.codec.JacksonCodec;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.drasyl.cli.sdon.SdonCommand.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DeviceHelloTest {
    @Nested
    class Serialization {
        @Test
        void shouldBeSerializable() {
            final JacksonCodec<SdonMessage> handler = new JacksonCodec<>(OBJECT_MAPPER, SdonMessage.class);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final DeviceHello msg = new DeviceHello(Map.of("sdon.tags", List.of("foo", "bar", "baz")));

            // serialize
            channel.writeOutbound(msg);
            final Object serializedMsg = channel.readOutbound();
            assertInstanceOf(ByteBuf.class, serializedMsg);

            // deserialize
            channel.writeInbound(serializedMsg);
            final Object deserializedMsg = channel.readInbound();
            assertEquals(msg, deserializedMsg);

            channel.checkException();
            channel.close();
        }
    }
}
