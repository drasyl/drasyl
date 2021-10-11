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
package org.drasyl.node.handler.serialization;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;

@ExtendWith(MockitoExtension.class)
class SerializationTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ReadWriteLock lock;

    @Nested
    class FindSerializerFor {
        @Test
        void shouldReturnSerializerIfSerializerForConcreteClassExist(@Mock final Serializer serializer) {
            final Serialization serialization = new Serialization(Map.of("my-serializer", serializer), Map.of(String.class, "my-serializer"));

            assertEquals(serializer, serialization.findSerializerFor("Hallo Welt".getClass().getName()));
        }

        @Test
        void shouldReturnSerializerIfSerializerForSuperClassExist(@Mock final Serializer serializer) {
            final Serialization serialization = new Serialization(Map.of("my-serializer", serializer), Map.of(Map.class, "my-serializer"));

            assertEquals(serializer, serialization.findSerializerFor(HashMap.class.getName()));
            assertEquals(serializer, serialization.findSerializerFor(Map.class.getName()));
        }

        @Test
        void shouldReturnSerializerIfSerializerForWrapperExist(@Mock final Serializer serializer) {
            final Serialization serialization = new Serialization(Map.of("my-serializer", serializer), Map.of(
                    Boolean.class, "my-serializer",
                    Character.class, "my-serializer",
                    Byte.class, "my-serializer",
                    Float.class, "my-serializer",
                    Integer.class, "my-serializer",
                    Long.class, "my-serializer",
                    Short.class, "my-serializer"
            ));

            assertEquals(serializer, serialization.findSerializerFor(Boolean.class.getName()));
            assertEquals(serializer, serialization.findSerializerFor(Character.class.getName()));
            assertEquals(serializer, serialization.findSerializerFor(Byte.class.getName()));
            assertEquals(serializer, serialization.findSerializerFor(Float.class.getName()));
            assertEquals(serializer, serialization.findSerializerFor(Integer.class.getName()));
            assertEquals(serializer, serialization.findSerializerFor(Long.class.getName()));
            assertEquals(serializer, serialization.findSerializerFor(Short.class.getName()));
        }

        @Test
        void shouldReturnNullIfNoSerializerExist() {
            final Serialization serialization = new Serialization(Map.of(), Map.of());

            assertNull(serialization.findSerializerFor(HashMap.class.getName()));
        }

        @Test
        void shouldReturnNullSerializerForNullObject() {
            final Serialization serialization = new Serialization(Map.of(), Map.of());

            assertThat(serialization.findSerializerFor(null), instanceOf(NullSerializer.class));
        }
    }

    @Nested
    class AddSerializer {
        @Test
        void shouldAddSerializer() {
            final Serializer serializer = new MySerializer();
            final Serialization serialization = new Serialization(new HashMap<>(), new HashMap<>());

            serialization.addSerializer(HashMap.class, serializer);

            assertEquals(serializer, serialization.findSerializerFor(HashMap.class.getName()));
            assertNull(serialization.findSerializerFor(Map.class.getName()));
        }
    }

    @Nested
    class RemoveSerializer {
        @Test
        void shouldRemoveClazz() {
            final Serialization serialization = new Serialization(lock, new HashMap<>(Map.of(HashMap.class.getName(), new MySerializer(), Map.class.getName(), new MySerializer())));

            serialization.removeSerializer(Map.class);

            assertNull(serialization.findSerializerFor(HashMap.class.getName()));
            assertNull(serialization.findSerializerFor(Map.class.getName()));
        }

        @Test
        void shouldRemoveSerializer() {
            final MySerializer serializer = new MySerializer();
            final Serialization serialization = new Serialization(lock, new HashMap<>(Map.of(HashMap.class.getName(), serializer, Map.class.getName(), serializer)));

            serialization.removeSerializer(serializer);

            assertNull(serialization.findSerializerFor(HashMap.class.getName()));
            assertNull(serialization.findSerializerFor(Map.class.getName()));
        }
    }

    private static class MySerializer implements Serializer {
        @Override
        public byte[] toByteArray(final Object o) throws IOException {
            return new byte[0];
        }

        @Override
        public <T> T fromByteArray(final byte[] bytes, final Class<T> type) throws IOException {
            return null;
        }
    }
}
