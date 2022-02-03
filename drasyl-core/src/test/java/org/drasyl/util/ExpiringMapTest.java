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

import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpiringMapTest {
    @Nested
    class MaximumSize {
        @Test
        void shouldEvictFirstEntriesBasedOnExpirationPolicyWhenSizeIsExceeding() throws InterruptedException {
            // expire after write
            final Map<Object, Object> writeMap = new ExpiringMap<>(2, 10, -1);
            writeMap.put("Hallo", 1);
            writeMap.put("Hello", 1);
            writeMap.put("Bonjour", 1);

            assertEquals(2, writeMap.size());
            assertFalse(writeMap.containsKey("Hallo"));

            // expire after access
            final Map<Object, Object> accessMap = new ExpiringMap<>(2, -1, 10);
            accessMap.put("Hallo", 1);
            accessMap.put("Hello", 1);
            accessMap.get("Hallo");
            accessMap.put("Bonjour", 1);

            assertEquals(2, accessMap.size());
            assertFalse(accessMap.containsKey("Hello"));
        }
    }

    @Nested
    class ExpireAfterWrite {
        @Test
        void shouldExpireEntriesBasedOnExpirationPolicy() throws InterruptedException {
            final Map<Object, Object> map = new ExpiringMap<>(-1, 10, -1);

            // accessing the entry should not affect expiration
            map.put("Foo", "Bar");
            assertTrue(map.containsKey("Foo"));
            await().untilAsserted(() -> {
                map.get("Foo");
                assertFalse(map.containsKey("Foo"));
            });

            // writing the entry should affect expiration
            for (int i = 0; i < 10; i++) {
                map.put("Baz", "Bar");
                assertTrue(map.containsKey("Baz"));
                Thread.sleep(5);
            }
        }
    }

    @Nested
    class ExpireAfterAccess {
        @Test
        void shouldExpireEntriesBasedOnExpirationPolicy() throws InterruptedException {
            final Map<Object, Object> map = new ExpiringMap<>(-1, -1, 10);

            // accessing the entry should affect expiration
            map.put("Foo", "Bar");
            assertTrue(map.containsKey("Foo"));
            for (int i = 0; i < 10; i++) {
                map.get("Foo");
                assertTrue(map.containsKey("Foo"));
                Thread.sleep(5);
            }

            // writing the entry should not affect expiration
            await().untilAsserted(() -> {
                map.put("Baz", "Bar");
                assertFalse(map.containsKey("Foo"));
            });
        }
    }
}
