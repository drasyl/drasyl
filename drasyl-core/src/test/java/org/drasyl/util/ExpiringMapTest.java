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

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiringMapTest {
    @Nested
    class MaximumSize {
        @Test
        void shouldEvictFirstEntriesBasedOnWriteExpirationPolicyWhenSizeIsExceeding(@Mock final LongSupplier currentTimeProvider) {
            when(currentTimeProvider.getAsLong()).thenReturn(100L, 101L, 102L, 103L, 104L, 105L, 106L);

            final Map<Object, Object> map = new ExpiringMap<>(currentTimeProvider, 2, 10, -1, new HashMap<>(), new TreeSet<>()); // t=100
            map.put("Hallo", 1); // t=101,102
            map.put("Hello", 1); // t=103,104
            map.put("Bonjour", 1); // t=105,106

            assertEquals(2, map.size());
            assertFalse(map.containsKey("Hallo"));
        }

        @Test
        void shouldEvictFirstEntriesBasedOnReadExpirationPolicyWhenSizeIsExceeding(@Mock final LongSupplier currentTimeProvider) {
            when(currentTimeProvider.getAsLong()).thenReturn(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L);

            final Map<Object, Object> map = new ExpiringMap<>(currentTimeProvider, 2, -1, 10, new HashMap<>(), new TreeSet<>()); // t=100
            map.put("Hallo", 1); // t=101,102
            map.put("Hello", 1); // t=103,104
            assertEquals(1, map.get("Hallo")); // t=105,106
            map.put("Bonjour", 1); // t=107,108

            assertEquals(2, map.size());
            assertFalse(map.containsKey("Hello"));
        }
    }

    @Nested
    class ExpireAfterWrite {
        @Test
        void shouldExpireEntriesBasedOnExpirationPolicy(@Mock final LongSupplier currentTimeProvider) {
            when(currentTimeProvider.getAsLong()).thenReturn(100L, 101L, 102L, 103L, 104L, 111L, 200L, 201L, 205L, 206L, 214L);

            final Map<Object, Object> map = new ExpiringMap<>(currentTimeProvider, -1, 10, -1, new HashMap<>(), new TreeSet<>()); // t=100

            // accessing the entry should not affect expiration
            map.put("Foo", "Bar"); // t=101,102
            assertTrue(map.containsKey("Foo")); // t=103
            assertEquals("Bar", map.get("Foo")); // t=104
            assertFalse(map.containsKey("Foo")); // t=111

            // writing the entry should affect expiration
            map.put("Baz", "Bar"); // t=200,201
            map.put("Baz", "Bar"); // t=205,206
            assertTrue(map.containsKey("Baz")); // 214
        }
    }

    @Nested
    class ExpireAfterAccess {
        @Test
        void shouldExpireEntriesBasedOnExpirationPolicy(@Mock final LongSupplier currentTimeProvider) {
            when(currentTimeProvider.getAsLong()).thenReturn(100L, 101L, 102L, 103L, 104L, 105L, 113L, 200L, 201L, 202L, 203L, 211L);

            final Map<Object, Object> map = new ExpiringMap<>(currentTimeProvider, -1, -1, 10, new HashMap<>(), new TreeSet<>()); // t=100

            // accessing the entry should affect expiration
            map.put("Foo", "Bar"); // t=101,102
            assertTrue(map.containsKey("Foo")); // t=103
            map.get("Foo"); // t=104,105
            assertTrue(map.containsKey("Foo")); // t=113

            // writing the entry should not affect expiration
            map.put("Baz", "Bar"); // t=200,201
            map.put("Baz", "Bar"); // t=202,203
            assertFalse(map.containsKey("Baz")); // t=211
        }
    }
}
