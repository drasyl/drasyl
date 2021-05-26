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
package org.drasyl.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.protobuf.ByteString;
import org.drasyl.crypto.HexUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;

/**
 * A serializer based on the <a href="https://github.com/FasterXML/jackson">Jackson Library</a> for
 * converting java objects to and from JSON.
 */
public class JacksonJsonSerializer implements Serializer {
    @Override
    public byte[] toByteArray(final Object o) throws IOException {
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            JACKSON_WRITER.writeValue(out, o);
            return out.toByteArray();
        }
    }

    @Override
    public <T> T fromByteArray(final byte[] bytes, final Class<T> type) throws IOException {
        try (final ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            return JACKSON_READER.readValue(in, type);
        }
    }

    /**
     * Serializes a given byte array as hexadecimal string.
     */
    public static final class BytesToHexStringSerializer extends StdSerializer<ByteString> {
        private static final long serialVersionUID = 4261135293288643562L;

        public BytesToHexStringSerializer() {
            super(ByteString.class);
        }

        public BytesToHexStringSerializer(final Class<ByteString> t) {
            super(t);
        }

        @Override
        public void serialize(final ByteString value,
                              final JsonGenerator gen,
                              final SerializerProvider provider) throws IOException {
            gen.writeString(HexUtil.bytesToHex(value.toByteArray()));
        }
    }

    /**
     * Deserializes a given hexadecimal string to byte array.
     */
    public static final class BytesToHexStringDeserializer extends StdDeserializer<ByteString> {
        private static final long serialVersionUID = 3616936627408179992L;

        public BytesToHexStringDeserializer() {
            super(byte[].class);
        }

        public BytesToHexStringDeserializer(final Class<ByteString> t) {
            super(t);
        }

        @Override
        public ByteString deserialize(final JsonParser p,
                                      final DeserializationContext ctxt) throws IOException {
            return ByteString.copyFrom(HexUtil.fromString(p.getText()));
        }
    }
}
