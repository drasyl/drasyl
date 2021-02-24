/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.pipeline.serialization;

import org.drasyl.serialization.NullSerializer;
import org.drasyl.serialization.Serializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

            assertEquals(serializer, serialization.findSerializerFor("Hallo Welt"));
        }

        @Test
        void shouldReturnSerializerIfSerializerForSuperClassExist(@Mock final Serializer serializer) {
            final Serialization serialization = new Serialization(Map.of("my-serializer", serializer), Map.of(Map.class, "my-serializer"));

            assertEquals(serializer, serialization.findSerializerFor(new HashMap<>()));
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

            assertEquals(serializer, serialization.findSerializerFor(true));
            assertEquals(serializer, serialization.findSerializerFor((char) 0));
            assertEquals(serializer, serialization.findSerializerFor((byte) 0));
            assertEquals(serializer, serialization.findSerializerFor(0f));
            assertEquals(serializer, serialization.findSerializerFor(0));
            assertEquals(serializer, serialization.findSerializerFor(0L));
            assertEquals(serializer, serialization.findSerializerFor((short) 0));
        }

        @Test
        void shouldReturnNullIfNoSerializerExist() {
            final Serialization serialization = new Serialization(Map.of(), Map.of());

            assertNull(serialization.findSerializerFor(new HashMap<>()));
        }

        @Test
        void shouldReturnNullSerializerForNullObject() {
            final Serialization serialization = new Serialization(Map.of(), Map.of());

            assertThat(serialization.findSerializerFor((String) null), instanceOf(NullSerializer.class));
        }
    }

    @Nested
    class AddSerializer {
        @Test
        void shouldAddSerializer() {
            final Map<String, Serializer> serializers = new HashMap<>();
            final Map<Class<?>, String> bindings = new HashMap<>();
            final Map<Class<?>, Optional<Serializer>> mapping = new HashMap<>();
            final Serializer serializer = new MySerializer();
            final Serialization serialization = new Serialization(lock, serializers, bindings, mapping);

            serialization.addSerializer(HashMap.class, serializer);

            assertThat(serializers, hasEntry(MySerializer.class.getName(), serializer));
            assertThat(bindings, hasEntry(HashMap.class, MySerializer.class.getName()));
        }
    }

    @Nested
    class RemoteSerializer {
        @Test
        void shouldRemoveSerializer() {
            final Serializer serializer = new MySerializer();
            final Map<String, Serializer> serializers = new HashMap<>(Map.of(MySerializer.class.getName(), serializer));
            final Map<Class<?>, String> bindings = new HashMap<>(Map.of(HashMap.class, MySerializer.class.getName(), String.class, MySerializer.class.getName()));
            final Map<Class<?>, Optional<Serializer>> mapping = new HashMap<>();
            final Serialization serialization = new Serialization(lock, serializers, bindings, mapping);

            serialization.removeSerializer(serializer);

            assertTrue(serializers.isEmpty());
            assertTrue(bindings.isEmpty());
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
