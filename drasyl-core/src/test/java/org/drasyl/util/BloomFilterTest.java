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

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloomFilterTest {
    @Test
    void testToString() {
        final BloomFilter<String> filter = new BloomFilter<>(10_000_000, 0.01, String::getBytes);

        assertEquals("BloomFilter{n=10000000, p=0.01, m=95850584, k=6}", filter.toString());
    }

    @Test
    void isEmpty() {
        final BloomFilter<String> filter = new BloomFilter<>(10_000_000, 0.01, String::getBytes);

        assertTrue(filter.isEmpty());

        filter.add("Hello");
        assertFalse(filter.isEmpty());
    }

    @Test
    void contains() {
        final BloomFilter<String> filter = new BloomFilter<>(10_000_000, 0.01, String::getBytes);

        assertFalse(filter.contains("Hello"));

        filter.add("Hello");
        assertTrue(filter.contains("Hello"));

        assertFalse(filter.contains(123));
    }

    @Test
    void add() {
        final BloomFilter<String> filter = new BloomFilter<>(10_000_000, 0.01, String::getBytes);

        assertTrue(filter.add("Hello"));
        assertFalse(filter.add("Hello"));
    }

    @Test
    void containsAll() {
        final BloomFilter<String> filter = new BloomFilter<>(10_000_000, 0.01, String::getBytes);

        assertTrue(filter.add("Hello"));
        assertTrue(filter.add("World"));
        assertTrue(filter.containsAll(Set.of("World", "Hello")));
        assertFalse(filter.containsAll(Set.of("World", "Hello", "Bye")));
    }

    @Test
    void addAll() {
        final BloomFilter<String> filter = new BloomFilter<>(10_000_000, 0.01, String::getBytes);

        assertTrue(filter.addAll(Set.of("Hello", "World")));
        assertFalse(filter.addAll(Set.of("Hello", "World")));
        assertTrue(filter.containsAll(Set.of("World", "Hello")));
    }

    @Test
    void clear() {
        final BloomFilter<String> filter = new BloomFilter<>(10_000_000, 0.01, String::getBytes);

        filter.add("Hello");
        filter.clear();

        assertTrue(filter.isEmpty());
    }

    @Test
    void unsupported() {
        final BloomFilter<String> filter = new BloomFilter<>(10_000_000, 0.01, String::getBytes);

        assertThrows(UnsupportedOperationException.class, filter::size);
        assertThrows(UnsupportedOperationException.class, filter::iterator);
        assertThrows(UnsupportedOperationException.class, filter::toArray);
        assertThrows(UnsupportedOperationException.class, () -> filter.toArray(new Object[5]));
        assertThrows(UnsupportedOperationException.class, () -> filter.remove("huhu"));
        assertThrows(UnsupportedOperationException.class, () -> filter.retainAll(Set.of("huhu")));
        assertThrows(UnsupportedOperationException.class, () -> filter.removeAll(Set.of("huhu")));
    }

    @Test
    void getter() {
        final BloomFilter<String> filter = new BloomFilter<>(10_000_000, 0.01, String::getBytes);

        assertEquals(10_000_000, filter.n());
        assertEquals(0.01, filter.p());
        assertEquals(95850584, filter.m());
        assertEquals(6, filter.k());
        assertNotNull(filter.bitset());
    }

    @Test
    void merge() {
        final BloomFilter<String> filter = new BloomFilter<>(10_000_000, 0.01, String::getBytes);
        filter.add("Hello");
        final BloomFilter<String> other = new BloomFilter<>(10_000_000, 0.01, String::getBytes);
        other.add("World");

        filter.merge(other);

        assertTrue(filter.containsAll(Set.of("Hello", "World")));
    }
}
