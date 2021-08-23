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
package org.drasyl.channel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import org.drasyl.util.ThrowingBiConsumer;
import org.drasyl.util.ThrowingBiFunction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class JacksonCodecTest {
    @Nested
    class Encode {
        @Test
        void shouldSerializeObjectToJson() {
            final ChannelHandler handler = new JacksonCodec<>(MyObject.class);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            channel.writeAndFlush(new MyObject("Hello"));

            final ByteBuf expected = Unpooled.copiedBuffer("{\"attr\":\"Hello\"}", UTF_8);
            final ByteBuf actual = channel.readOutbound();
            assertEquals(expected, actual);

            expected.release();
            actual.release();
        }

        @Test
        void shouldThrowCodecExceptionWhenSerializationFail(@Mock final ThrowingBiConsumer<OutputStream, Object, IOException> jacksonWriter,
                                                            @Mock final ThrowingBiFunction<InputStream, Class<MyObject>, MyObject, IOException> jacksonReader) throws IOException {
            doThrow(IOException.class).when(jacksonWriter).accept(any(), any());

            final ChannelHandler handler = new JacksonCodec<>(jacksonWriter, jacksonReader, MyObject.class);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            final ChannelFuture channelFuture = channel.writeAndFlush(new MyObject("Hello")).awaitUninterruptibly();

            assertThat(channelFuture.cause(), instanceOf(EncoderException.class));
        }
    }

    @Nested
    class Decode {
        @Test
        void shouldDeserializeByteBuf() {
            final ChannelHandler handler = new JacksonCodec<>(MyObject.class);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            final ByteBuf msg = Unpooled.copiedBuffer("{\"attr\":\"Hello\"}", UTF_8);
            channel.pipeline().fireChannelRead(msg);

            assertEquals(new MyObject("Hello"), channel.readInbound());
        }

        @Test
        void shouldThrowCodecExceptionWhenDeserializationFail(@Mock final ThrowingBiConsumer<OutputStream, Object, IOException> jacksonWriter,
                                                              @Mock final ThrowingBiFunction<InputStream, Class<MyObject>, MyObject, IOException> jacksonReader) throws IOException {
            doThrow(IOException.class).when(jacksonReader).apply(any(), any());

            final ChannelHandler handler = new JacksonCodec<>(jacksonWriter, jacksonReader, MyObject.class);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            final ByteBuf msg = Unpooled.copiedBuffer("{\"attr\":\"Hello\"}", UTF_8);
            channel.pipeline().fireChannelRead(msg);

            assertThrows(DecoderException.class, channel::checkException);
        }
    }

    static class MyObject {
        private final String attr;

        @JsonCreator
        MyObject(@JsonProperty("attr") final String attr) {
            this.attr = attr;
        }

        public String getAttr() {
            return attr;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            return obj instanceof MyObject && Objects.equals(attr, ((MyObject) obj).attr);
        }

        @Override
        public String toString() {
            return "MyObject{" +
                    "attr='" + attr + '\'' +
                    '}';
        }
    }
}
