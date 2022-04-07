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
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvictingQueueTest {
    @Nested
    class Size {
        @Test
        void test(@Mock final Queue<String> delegate) {
            when(delegate.size()).thenReturn(123);

            final Queue<String> queue = new EvictingQueue<>(delegate, 3);

            assertEquals(123, queue.size());
        }
    }

    @Nested
    class IsEmpty {
        @Test
        void test(@Mock final Queue<String> delegate) {
            when(delegate.isEmpty()).thenReturn(true);

            final Queue<String> queue = new EvictingQueue<>(delegate, 3);

            assertTrue(queue.isEmpty());
            verify(delegate).isEmpty();
        }
    }

    @Nested
    class Contains {
        @Test
        void test(@Mock final Queue<String> delegate) {
            when(delegate.contains("Hi")).thenReturn(true);

            final Queue<String> queue = new EvictingQueue<>(delegate, 3);

            assertTrue(queue.contains("Hi"));
            verify(delegate).contains("Hi");
        }
    }

    @Nested
    class Iterator {
        @Test
        void test(@Mock final Queue<String> delegate) {
            final Queue<String> queue = new EvictingQueue<>(delegate, 3);

            assertEquals(delegate.iterator(), queue.iterator());
        }
    }

    @Nested
    class ToArray {
        @Test
        void test(@Mock final Queue<String> delegate) {
            final Queue<String> queue = new EvictingQueue<>(delegate, 3);

            assertEquals(delegate.toArray(), queue.toArray());
        }

        @Test
        void test2(@Mock final Queue<String> delegate) {
            final Queue<String> queue = new EvictingQueue<>(delegate, 3);

            assertEquals(delegate.toArray(new String[3]), queue.toArray(new String[3]));
        }
    }

    @Nested
    class Add {
        @Test
        void test() {
            final Queue<String> queue = new EvictingQueue<>(3);

            queue.add("foo");
            queue.add("bar");
            queue.add("foobar");
            queue.add("baz");

            assertArrayEquals(new String[]{ "bar", "foobar", "baz" }, queue.toArray());
        }
    }

    @Nested
    class Remove {
        @Test
        void test(@Mock final Queue<String> delegate) {
            when(delegate.remove("Hi")).thenReturn(true);

            final Queue<String> queue = new EvictingQueue<>(delegate, 3);

            assertTrue(queue.remove("Hi"));

            verify(delegate).remove("Hi");
        }

        @Test
        void test2(@Mock final Queue<String> delegate) {
            when(delegate.remove()).thenReturn("Hi");

            final Queue<String> queue = new EvictingQueue<>(delegate, 3);

            assertEquals("Hi", queue.remove());
            verify(delegate).remove();
        }
    }

    @Nested
    class ContainsAll {
        @Test
        void test(@Mock final Queue<String> delegate, @Mock final Collection<?> collection) {
            when(delegate.containsAll(collection)).thenReturn(true);

            final Queue<String> queue = new EvictingQueue<>(delegate, 3);

            assertTrue(queue.containsAll(collection));

            verify(delegate).containsAll(collection);
        }
    }

    @Nested
    class AddAll {
        @Test
        void test() {
            final Queue<String> queue = new EvictingQueue<>(3);

            queue.add("foo");
            queue.addAll(List.of("bar", "foobar", "baz", "qux"));

            assertArrayEquals(new String[]{ "foobar", "baz", "qux" }, queue.toArray());
        }

        @Test
        void test2() {
            final Queue<String> queue = new EvictingQueue<>(3);

            queue.add("foo");
            queue.add("bar");
            queue.addAll(List.of("foobar", "baz"));

            assertArrayEquals(new String[]{ "bar", "foobar", "baz" }, queue.toArray());
        }
    }

    @Nested
    class RemoveAll {
        @Test
        void test(@Mock final Queue<String> delegate, @Mock final Collection<?> collection) {
            when(delegate.removeAll(collection)).thenReturn(true);

            final Queue<String> queue = new EvictingQueue<>(delegate, 3);

            assertTrue(queue.removeAll(collection));

            verify(delegate).removeAll(collection);
        }
    }

    @Nested
    class RetainAll {
        @Test
        void test(@Mock final Queue<String> delegate, @Mock final Collection<?> collection) {
            when(delegate.retainAll(collection)).thenReturn(true);

            final Queue<String> queue = new EvictingQueue<>(delegate, 3);

            assertTrue(queue.retainAll(collection));

            verify(delegate).retainAll(collection);
        }
    }

    @Nested
    class Clear {
        @Test
        void test(@Mock final Queue<String> delegate) {
            final Queue<String> queue = new EvictingQueue<>(delegate, 3);

            queue.clear();

            verify(delegate).clear();
        }
    }

    @Nested
    class Offer {
        @Test
        void test() {
            final Queue<String> queue = new EvictingQueue<>(3);

            queue.offer("foo");
            queue.offer("bar");
            queue.offer("foobar");
            queue.offer("baz");

            assertArrayEquals(new String[]{ "bar", "foobar", "baz" }, queue.toArray());
        }
    }

    @Nested
    class Poll {
        @Test
        void test(@Mock final Queue<String> delegate) {
            when(delegate.poll()).thenReturn("Hi");

            final Queue<String> queue = new EvictingQueue<>(delegate, 3);

            assertEquals("Hi", queue.poll());
            verify(delegate).poll();
        }
    }

    @Nested
    class Element {
        @Test
        void test(@Mock final Queue<String> delegate) {
            when(delegate.element()).thenReturn("Hi");

            final Queue<String> queue = new EvictingQueue<>(delegate, 3);

            assertEquals("Hi", queue.element());
            verify(delegate).element();
        }
    }

    @Nested
    class Peek {
        @Test
        void test(@Mock final Queue<String> delegate) {
            when(delegate.peek()).thenReturn("Hi");

            final Queue<String> queue = new EvictingQueue<>(delegate, 3);

            assertEquals("Hi", queue.peek());
            verify(delegate).peek();
        }
    }
}
