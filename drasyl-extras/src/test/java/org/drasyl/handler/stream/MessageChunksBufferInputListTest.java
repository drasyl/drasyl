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
package org.drasyl.handler.stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MessageChunksBufferInputListTest {
    @Test
    void size(@Mock MessageChunk chunk) {
        final List<MessageChunk> list = new MessageChunksBufferInputList(3);
        list.set(0, chunk);
        list.set(1, chunk);
        list.set(1, chunk);

        assertEquals(2, list.size());
    }

    @Test
    void isEmpty(@Mock MessageChunk chunk) {
        final List<MessageChunk> list = new MessageChunksBufferInputList(3);

        assertTrue(list.isEmpty());

        list.set(0, chunk);
        assertFalse(list.isEmpty());
    }

    @Test
    void iterator(@Mock MessageChunk chunk) {
        final List<MessageChunk> list = new MessageChunksBufferInputList(3);
        list.set(0, chunk);
        list.set(1, chunk);
        list.set(1, chunk);

        final Iterator<MessageChunk> iterator = list.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(chunk, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(chunk, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    void toArray(@Mock MessageChunk chunk) {
        final List<MessageChunk> list = new MessageChunksBufferInputList(3);
        list.set(0, chunk);
        list.set(1, chunk);
        list.set(1, chunk);

        assertArrayEquals(new MessageChunk[]{ chunk, chunk }, list.toArray());
    }

    @Test
    void clear(@Mock MessageChunk chunk) {
        final List<MessageChunk> list = new MessageChunksBufferInputList(3);
        list.set(0, chunk);

        list.clear();

        assertTrue(list.isEmpty());
        assertNull(list.get(0));
    }

    @Test
    void get(@Mock MessageChunk chunk) {
        final List<MessageChunk> list = new MessageChunksBufferInputList(3);
        list.set(0, chunk);

        assertEquals(chunk, list.get(0));
    }

    @Test
    void set(@Mock MessageChunk chunk) {
        final List<MessageChunk> list = new MessageChunksBufferInputList(3);
        list.set(0, chunk);

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> {
            list.set(3, chunk);
        });
    }

    @Test
    void unsupportedOperations() {
        final List<MessageChunk> list = new MessageChunksBufferInputList(1);
        assertThrows(UnsupportedOperationException.class, () -> list.contains(null));
        assertThrows(UnsupportedOperationException.class, () -> list.toArray(new Object[0]));
        assertThrows(UnsupportedOperationException.class, () -> list.add(null));
        assertThrows(UnsupportedOperationException.class, () -> list.remove(null));
        assertThrows(UnsupportedOperationException.class, () -> list.containsAll(null));
        assertThrows(UnsupportedOperationException.class, () -> list.addAll(null));
        assertThrows(UnsupportedOperationException.class, () -> list.addAll(0, null));
        assertThrows(UnsupportedOperationException.class, () -> list.removeAll(null));
        assertThrows(UnsupportedOperationException.class, () -> list.retainAll(null));
        assertThrows(UnsupportedOperationException.class, () -> list.add(0, null));
        assertThrows(UnsupportedOperationException.class, () -> list.remove(0));
        assertThrows(UnsupportedOperationException.class, () -> list.indexOf(null));
        assertThrows(UnsupportedOperationException.class, () -> list.lastIndexOf(null));
        assertThrows(UnsupportedOperationException.class, () -> list.listIterator());
        assertThrows(UnsupportedOperationException.class, () -> list.listIterator(0));
        assertThrows(UnsupportedOperationException.class, () -> list.subList(0, 0));
    }
}
