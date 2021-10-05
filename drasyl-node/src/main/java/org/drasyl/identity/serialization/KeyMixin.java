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
package org.drasyl.identity.serialization;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.drasyl.crypto.HexUtil;

import java.io.IOException;

/**
 * Provides hints for correct {@link org.drasyl.identity.Key} JSON (de)serialization.
 */
@SuppressWarnings("java:S118")
public interface KeyMixin {
    @JsonValue
    @JsonSerialize(using = BytesToHexStringSerializer.class)
    @JsonDeserialize(using = BytesToHexStringDeserializer.class)
    byte[] toByteArray();

    /**
     * Deserializes a given hexadecimal string to byte array.
     */
    @SuppressWarnings("java:S4926")
    final class BytesToHexStringDeserializer extends StdDeserializer<byte[]> {
        private static final long serialVersionUID = 3616936627408179992L;

        public BytesToHexStringDeserializer() {
            super(byte[].class);
        }

        public BytesToHexStringDeserializer(final Class<byte[]> t) {
            super(t);
        }

        @Override
        public byte[] deserialize(final JsonParser p,
                                  final DeserializationContext ctxt) throws IOException {
            return HexUtil.fromString(p.getText());
        }
    }

    /**
     * Serializes a given byte array as hexadecimal string.
     */
    @SuppressWarnings("java:S4926")
    final class BytesToHexStringSerializer extends StdSerializer<byte[]> {
        private static final long serialVersionUID = 4261135293288643562L;

        public BytesToHexStringSerializer() {
            super(byte[].class);
        }

        public BytesToHexStringSerializer(final Class<byte[]> t) {
            super(t);
        }

        @Override
        public void serialize(final byte[] value,
                              final JsonGenerator gen,
                              final SerializerProvider provider) throws IOException {
            gen.writeString(HexUtil.bytesToHex(value));
        }
    }
}
