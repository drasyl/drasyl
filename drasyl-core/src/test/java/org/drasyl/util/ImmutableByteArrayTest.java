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

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

@ExtendWith(MockitoExtension.class)
class ImmutableByteArrayTest {
    @Test
    void shouldCreateImmutableArray() {
        final byte[] array = new byte[1];

        final ImmutableByteArray immutableArray = ImmutableByteArray.of(array);

        assertNotSame(array, immutableArray.getArray());
        assertNotSame(immutableArray.getArray(), immutableArray.getArray());

        assertArrayEquals(array, immutableArray.getArray());
        assertArrayEquals(immutableArray.getArray(), immutableArray.getArray());

        array[0] = 5;

        assertFalse(Arrays.equals(array, immutableArray.getArray()));
    }

    @Test
    void shouldReturnCorrectSize() {
        final byte[] array = new byte[1];

        final ImmutableByteArray immutableArray = ImmutableByteArray.of(array);

        assertEquals(array.length, immutableArray.size());
    }

    @Test
    void shouldBeEquals() {
        final byte[] array = new byte[1];

        final ImmutableByteArray immutableArray1 = ImmutableByteArray.of(array);
        final ImmutableByteArray immutableArray2 = ImmutableByteArray.of(array);

        assertEquals(immutableArray1, immutableArray2);
        assertEquals(immutableArray1.hashCode(), immutableArray2.hashCode());
        assertEquals(immutableArray1.toString(), immutableArray2.toString());
    }

    @Test
    void shouldNotBeEquals() {
        final byte[] array1 = new byte[1];
        final byte[] array2 = new byte[2];

        final ImmutableByteArray immutableArray1 = ImmutableByteArray.of(array1);
        final ImmutableByteArray immutableArray2 = ImmutableByteArray.of(array2);

        assertNotEquals(immutableArray1, immutableArray2);
        assertNotEquals(immutableArray1.hashCode(), immutableArray2.hashCode());
    }
}
