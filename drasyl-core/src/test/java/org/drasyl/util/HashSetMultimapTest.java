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
package org.drasyl.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashSetMultimapTest {
    @Nested
    class Put {
        @Test
        void shouldReturnTrueIfValueIsNew() {
            final SetMultimap<Object, Object> multimap = new HashSetMultimap<>();

            assertTrue(multimap.put("Foo", "Bar"));
        }

        @Test
        void shouldReturnFalseIfValueIsNotNew() {
            final SetMultimap<Object, Object> multimap = new HashSetMultimap<>();
            multimap.put("Foo", "Bar");

            assertFalse(multimap.put("Foo", "Bar"));
        }
    }

    @Nested
    class PutAll {
        @Test
        void shouldReturnTrueIfAtLeastOneValueIsNew() {
            final SetMultimap<Object, Object> multimap = new HashSetMultimap<>();
            multimap.put("Foo", "Bar");

            assertTrue(multimap.putAll("Foo", "Bar", "Baz"));
        }

        @Test
        void shouldReturnFalseIfValueIsNotNew() {
            final SetMultimap<Object, Object> multimap = new HashSetMultimap<>();
            multimap.put("Foo", "Bar");

            assertFalse(multimap.put("Foo", "Bar"));
        }
    }

    @Nested
    class Remove {
        @Test
        void shouldReturnTrueIfValueDoestExist() {
            final SetMultimap<Object, Object> multimap = new HashSetMultimap<>();
            multimap.put("Foo", "Bar");

            assertTrue(multimap.remove("Foo", "Bar"));
        }

        @Test
        void shouldReturnFalseIfValueIsDoesNotExist() {
            final SetMultimap<Object, Object> multimap = new HashSetMultimap<>();

            assertFalse(multimap.remove("Foo", "Bar"));
        }
    }

    @Nested
    class Get {
        @Test
        void shouldReturnAllAssociatedValues() {
            final SetMultimap<Object, Object> multimap = new HashSetMultimap<>();
            multimap.put("Foo", "Bar");
            multimap.put("Foo", "Baz");
            multimap.put("Foo", "Bar");

            final Set<Object> foo = multimap.get("Foo");
            assertEquals(2, foo.size());
            assertTrue(foo.contains("Bar"));
            assertTrue(foo.contains("Baz"));
        }

        @Test
        void shouldReturnEmptySetForUnknownKey() {
            final SetMultimap<Object, Object> multimap = new HashSetMultimap<>();

            final Set<Object> foo = multimap.get("Foo");
            assertEquals(0, foo.size());
        }
    }

    @Nested
    class KeySet {
        @Test
        void shouldReturnSetWithAllKeys() {
            final SetMultimap<Object, Object> multimap = new HashSetMultimap<>();
            multimap.put("Foo", "Bar");
            multimap.put("Bar", "Baz");
            multimap.put("Baz", "Foo");

            assertEquals(Set.of("Foo", "Bar", "Baz"), multimap.keySet());
        }
    }

    @Nested
    class IsEmpty {
        @Test
        void shouldReturnIfMapIsEmpty() {
            final SetMultimap<Object, Object> multimap = new HashSetMultimap<>();
            multimap.put("Foo", "Bar");
            multimap.put("Bar", "Baz");
            multimap.put("Baz", "Foo");

            assertFalse(multimap.isEmpty());
            assertTrue(new HashSetMultimap<>().isEmpty());
        }
    }

    @Nested
    class Clear {
        @Test
        void shouldClearMap() {
            final SetMultimap<Object, Object> multimap = new HashSetMultimap<>();
            multimap.put("Foo", "Bar");
            multimap.put("Bar", "Baz");
            multimap.put("Baz", "Foo");

            assertFalse(multimap.isEmpty());
            multimap.clear();
            assertTrue(multimap.isEmpty());
        }
    }
}
