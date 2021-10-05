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
package org.drasyl.handler.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import org.drasyl.util.ThrowingBiConsumer;
import org.drasyl.util.ThrowingBiFunction;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * This codec converts with <a href="https://github.com/FasterXML/jackson">Jackson</a> messages of
 * type {@code T} to {@link ByteBuf}s and vice vera.
 * <p>
 * Make sure to declare the following dependency in your software when using this handler:
 * <blockquote>
 * <pre>
 * &lt;dependency&gt;
 *     &lt;groupId&gt;com.fasterxml.jackson.core&lt;/groupId&gt;
 *     &lt;artifactId&gt;jackson-databind&lt;/artifactId&gt;
 * &lt;/dependency&gt;
 * </pre>
 * </blockquote>
 */
public class JacksonCodec<T> extends ByteToMessageCodec<T> {
    private final ThrowingBiConsumer<OutputStream, Object, IOException> jacksonWriter;
    private final ThrowingBiFunction<InputStream, Class<T>, T, IOException> jacksonReader;
    private final Class<T> clazz;

    JacksonCodec(final ThrowingBiConsumer<OutputStream, Object, IOException> jacksonWriter,
                 final ThrowingBiFunction<InputStream, Class<T>, T, IOException> jacksonReader,
                 final Class<T> clazz) {
        super(clazz);
        this.jacksonWriter = requireNonNull(jacksonWriter);
        this.jacksonReader = requireNonNull(jacksonReader);
        this.clazz = requireNonNull(clazz);
    }

    public JacksonCodec(final ObjectMapper mapper, final Class<T> clazz) {
        this(mapper::writeValue, mapper::readValue, clazz);
    }

    public JacksonCodec(final Class<T> clazz) {
        this(new ObjectMapper(), clazz);
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final T msg,
                          final ByteBuf out) throws IOException {
        try (final OutputStream outputStream = new ByteBufOutputStream(out)) {
            jacksonWriter.accept(outputStream, msg);
        }
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) throws IOException {
        try (final InputStream inputStream = new ByteBufInputStream(in)) {
            out.add(jacksonReader.apply(inputStream, clazz));
        }
    }
}
