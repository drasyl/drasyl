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
package org.drasyl.channel.chunk;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Special {@link List} implementation which is used within our {@link
 * org.drasyl.channel.chunk.MessageChunksBuffer}.
 * <p>
 * Beware: Only {@link #size()}, {@link #isEmpty()}, {@link #iterator()}, {@link #toArray()}, {@link
 * #clear()}, {@link #get(int)}, and {@link #set(int, Object)} are implemented.
 *
 * @param <T> the type of the elements in this list
 */
public class MessageChunksBufferInputList implements List<MessageChunk> {
    private MessageChunk[] array;
    private int size;

    public MessageChunksBufferInputList(final int capacity) {
        this.array = new MessageChunk[requirePositive(capacity)];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<MessageChunk> iterator() {
        return Arrays.stream(array).filter(Objects::nonNull).iterator();
    }

    @SuppressWarnings("java:S881")
    @Override
    public Object[] toArray() {
        Object[] elements = new Object[size];
        int j = 0;
        for (int i = 0; i < size; i++) {
            if (array[i] != null) {
                elements[j++] = array[i];
            }
        }

        return elements;
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(MessageChunk e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends MessageChunk> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, Collection<? extends MessageChunk> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        array = new MessageChunk[array.length];
        size = 0;
    }

    @Override
    public MessageChunk get(int index) {
        return array[index];
    }

    @Override
    public MessageChunk set(int index, MessageChunk element) {
        final MessageChunk prevElement = array[index];
        if (prevElement == null) {
            size++;
        }
        array[index] = element;
        return prevElement;
    }

    @Override
    public void add(int index, MessageChunk element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MessageChunk remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<MessageChunk> listIterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<MessageChunk> listIterator(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<MessageChunk> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }
}
