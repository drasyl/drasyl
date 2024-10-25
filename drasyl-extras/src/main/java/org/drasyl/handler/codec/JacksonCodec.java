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
package org.drasyl.handler.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.handler.dht.chord.LocalChordNode.DrasylAddressMixin;
import org.drasyl.handler.dht.chord.LocalChordNode.IdentityPublicKeyMixin;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.ThrowingBiConsumer;
import org.drasyl.util.ThrowingBiFunction;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * This codec converts with <a href="https://github.com/FasterXML/jackson">Jackson</a> messages of
 * type {@code T} to {@link ByteBuf}s and vice versa.
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
public class JacksonCodec<T> extends MessageToMessageCodec<ByteBuf, T> {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static {
        OBJECT_MAPPER.addMixIn(IdentityPublicKey.class, IdentityPublicKeyMixin.class);
        OBJECT_MAPPER.addMixIn(DrasylAddress.class, DrasylAddressMixin.class);
    }
    private static final Logger LOG = LoggerFactory.getLogger(JacksonCodec.class);
    public static final int MAGIC_NUMBER = 286331153;
    private final ThrowingBiConsumer<OutputStream, Object, IOException> jacksonWriter;
    private final ThrowingBiFunction<InputStream, Class<T>, T, IOException> jacksonReader;
    private final Class<T> clazz;

    JacksonCodec(final ThrowingBiConsumer<OutputStream, Object, IOException> jacksonWriter,
                 final ThrowingBiFunction<InputStream, Class<T>, T, IOException> jacksonReader,
                 final Class<T> clazz) {
        super(ByteBuf.class, clazz);
        this.jacksonWriter = requireNonNull(jacksonWriter);
        this.jacksonReader = requireNonNull(jacksonReader);
        this.clazz = requireNonNull(clazz);
    }

    @UnstableApi
    public JacksonCodec(final ObjectMapper mapper, final Class<T> clazz) {
        this(mapper::writeValue, mapper::readValue, clazz);
    }

    public JacksonCodec(final Class<T> clazz) {
        this(OBJECT_MAPPER, clazz);
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx, final T msg, final List<Object> out) throws Exception {
        LOG.trace("Send to `{}`: {}", ctx.channel().remoteAddress(), msg);
        final ByteBuf byteBuf = ctx.alloc().buffer();
        byteBuf.writeInt(MAGIC_NUMBER);
        try (final OutputStream outputStream = new ByteBufOutputStream(byteBuf)) {
            jacksonWriter.accept(outputStream, msg);
        }
        out.add(byteBuf);
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) throws IOException {
        if (in.readableBytes() >= Integer.BYTES) {
            in.markReaderIndex();
            if (in.readInt() != MAGIC_NUMBER) {
                in.resetReaderIndex();
                out.add(in.retain());
                return;
            }

            try (final InputStream inputStream = new ByteBufInputStream(in)) {
                final T msg = jacksonReader.apply(inputStream, clazz);
                LOG.trace("Received from `{}`: {}", ctx.channel().remoteAddress(), msg);
                out.add(msg);
            }
        }
        else {
            out.add(in.retain());
        }
    }
}
