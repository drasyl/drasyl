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
package org.drasyl.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(MockitoExtension.class)
class WeakPoolTest {
    @Test
    void shouldReturnEqualObjectIfPoolContainsEqualObject() {
        final MyObject a = new MyObject("Hello World");
        final MyObject b = new MyObject("Hello World");

        final WeakPool<Object> pool = new WeakPool<>();
        pool.put(a);

        assertSame(a, pool.get(b));
    }

    @Test
    void shouldReturnNullIfPoolDoesNotContainsEqualObject() {
        final MyObject a = new MyObject("Hello World");
        final MyObject b = new MyObject("Bye Moon");

        final WeakPool<MyObject> pool = new WeakPool<>();
        pool.put(a);

        assertNull(pool.get(b));
    }

    @Test
    void shouldRemoveUnreferencedObjectsOnGarbageCollection() {
        MyObject a = new MyObject("Hello World");
        final MyObject b = new MyObject("Hello World");

        final WeakPool<MyObject> pool = new WeakPool<>();
        pool.put(a);

        a = null;
        System.gc();

        assertNull(a);
        await().untilAsserted(() -> assertNull(pool.get(b)));
    }

    static class MyObject {
        private final String value;

        public MyObject(final String value) {
            this.value = value;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MyObject myObject = (MyObject) o;
            return Objects.equals(value, myObject.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
