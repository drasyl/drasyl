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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static java.lang.Boolean.TRUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiringSetTest {
    @Nested
    class Size {
        @Test
        void shouldPerformCorrectCallToInternMap(@Mock final Map<Object, Boolean> map) {
            when(map.size()).thenReturn(123);

            final Set<Object> set = new ExpiringSet<>(map);

            assertEquals(123, set.size());

            verify(map).size();
        }
    }

    @Nested
    class IsEmpty {
        @Test
        void shouldPerformCorrectCallToInternMap(@Mock final Map<Object, Boolean> map) {
            when(map.isEmpty()).thenReturn(true);

            final Set<Object> set = new ExpiringSet<>(map);

            assertTrue(set.isEmpty());

            verify(map).isEmpty();
        }
    }

    @Nested
    class Contains {
        @Test
        void shouldPerformCorrectCallToInternMap(@Mock final Map<Object, Boolean> map,
                                                 @Mock final Object object) {
            when(map.containsKey(object)).thenReturn(true);

            final Set<Object> set = new ExpiringSet<>(map);

            assertTrue(set.contains(object));

            verify(map).containsKey(object);
        }
    }

    @Nested
    class IteratorTest {
        @Test
        void shouldPerformCorrectCallToInternMap(@Mock(answer = RETURNS_DEEP_STUBS) final Map<Object, Boolean> map,
                                                 @Mock final Iterator iterator) {
            when(map.keySet().iterator()).thenReturn(iterator);

            final Set<Object> set = new ExpiringSet<>(map);

            assertEquals(iterator, set.iterator());

            verify(map.keySet()).iterator();
        }
    }

    @Nested
    class ToArray {
        @Test
        void shouldPerformCorrectCallToInternMap(@Mock(answer = RETURNS_DEEP_STUBS) final Map<Object, Boolean> map) {
            final Object[] array = new Object[0];

            when(map.keySet().toArray(array)).thenReturn(array);

            final Set<Object> set = new ExpiringSet<>(map);

            assertEquals(array, set.toArray(array));

            verify(map.keySet()).toArray(array);
        }
    }

    @Nested
    class Add {
        @Test
        void shouldPerformCorrectCallToInternMap(@Mock final Map<Object, Boolean> map,
                                                 @Mock final Object element) {
            when(map.put(element, TRUE)).thenReturn(null);

            final Set<Object> set = new ExpiringSet<>(map);

            assertTrue(set.add(element));

            verify(map).put(element, TRUE);
        }
    }

    @Nested
    class Remove {
        @Test
        void shouldPerformCorrectCallToInternMap(@Mock final Map<Object, Boolean> map,
                                                 @Mock final Object element) {
            when(map.remove(element)).thenReturn(true);

            final Set<Object> set = new ExpiringSet<>(map);

            assertTrue(set.remove(element));

            verify(map).remove(element);
        }
    }

    @Nested
    class ContainsAll {
        @Test
        void shouldThrowException(@Mock final Map<Object, Boolean> map,
                                  @Mock final Collection collection) {
            final Set<Object> set = new ExpiringSet<>(map);

            assertThrows(UnsupportedOperationException.class, () -> set.containsAll(collection));
        }
    }

    @Nested
    class AddAll {
        @Test
        void shouldThrowException(@Mock final Map<Object, Boolean> map,
                                  @Mock final Collection collection) {
            final Set<Object> set = new ExpiringSet<>(map);

            assertThrows(UnsupportedOperationException.class, () -> set.addAll(collection));
        }
    }

    @Nested
    class RetainAll {
        @Test
        void shouldThrowException(@Mock final Map<Object, Boolean> map,
                                  @Mock final Collection collection) {
            final Set<Object> set = new ExpiringSet<>(map);

            assertThrows(UnsupportedOperationException.class, () -> set.retainAll(collection));
        }
    }

    @Nested
    class RemoveAll {
        @Test
        void shouldThrowException(@Mock final Map<Object, Boolean> map,
                                  @Mock final Collection collection) {
            final Set<Object> set = new ExpiringSet<>(map);

            assertThrows(UnsupportedOperationException.class, () -> set.removeAll(collection));
        }
    }

    @Nested
    class Clear {
        @Test
        void shouldPerformCorrectCallToInternMap(@Mock final Map<Object, Boolean> map) {
            final Set<Object> set = new ExpiringSet<>(map);

            set.clear();

            verify(map).clear();
        }
    }
}
