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

import static org.drasyl.util.SerialNumberArithmetic.add;
import static org.drasyl.util.SerialNumberArithmetic.greaterThan;
import static org.drasyl.util.SerialNumberArithmetic.greaterThanOrEqualTo;
import static org.drasyl.util.SerialNumberArithmetic.lessThan;
import static org.drasyl.util.SerialNumberArithmetic.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerialNumberArithmeticTest {
    @Test
    void testAdd() {
        assertEquals(0, add(255, 1, 8));
        assertEquals(200, add(100, 100, 8));
        assertEquals(44, add(200, 100, 8));
    }

    @Test
    void testLessThan() {
        assertTrue(lessThan(0, 1, 8));
        assertTrue(lessThan(0, 44, 8));
        assertTrue(lessThan(0, 100, 8));
        assertTrue(lessThan(44, 100, 8));
        assertTrue(lessThan(100, 200, 8));
        assertTrue(lessThan(200, 255, 8));
        assertTrue(lessThan(255, 0, 8));
        assertTrue(lessThan(255, 100, 8));
        assertTrue(lessThan(200, 0, 8));
        assertTrue(lessThan(200, 44, 8));
        assertTrue(lessThan(100, 100 + 100, 8));
        assertFalse(lessThan(100, 100 + 100 + 100, 8));
        assertFalse(lessThan(100, 100, 8));
    }

    @Test
    void testLessThanOrEqualTo() {
        assertTrue(lessThanOrEqualTo(0, 1, 8));
        assertTrue(lessThanOrEqualTo(0, 44, 8));
        assertTrue(lessThanOrEqualTo(0, 100, 8));
        assertTrue(lessThanOrEqualTo(44, 100, 8));
        assertTrue(lessThanOrEqualTo(100, 200, 8));
        assertTrue(lessThanOrEqualTo(200, 255, 8));
        assertTrue(lessThanOrEqualTo(255, 0, 8));
        assertTrue(lessThanOrEqualTo(255, 100, 8));
        assertTrue(lessThanOrEqualTo(200, 0, 8));
        assertTrue(lessThanOrEqualTo(200, 44, 8));
        assertTrue(lessThanOrEqualTo(100, 100 + 100, 8));
        assertFalse(lessThanOrEqualTo(100, 100 + 100 + 100, 8));
        assertTrue(lessThanOrEqualTo(100, 100, 8));
    }

    @Test
    void testGreaterThan() {
        assertTrue(greaterThan(1, 0, 8));
        assertTrue(greaterThan(44, 0, 8));
        assertTrue(greaterThan(100, 0, 8));
        assertTrue(greaterThan(100, 44, 8));
        assertTrue(greaterThan(200, 100, 8));
        assertTrue(greaterThan(255, 200, 8));
        assertTrue(greaterThan(0, 255, 8));
        assertTrue(greaterThan(100, 255, 8));
        assertTrue(greaterThan(0, 200, 8));
        assertTrue(greaterThan(44, 200, 8));
        assertTrue(greaterThan(100 + 100, 100, 8));
        assertFalse(greaterThan(100 + 100 + 100, 100, 8));
        assertFalse(greaterThan(100, 100, 8));
    }

    @Test
    void testGreaterThanOrEqualTo() {
        assertTrue(greaterThanOrEqualTo(1, 0, 8));
        assertTrue(greaterThanOrEqualTo(44, 0, 8));
        assertTrue(greaterThanOrEqualTo(100, 0, 8));
        assertTrue(greaterThanOrEqualTo(100, 44, 8));
        assertTrue(greaterThanOrEqualTo(200, 100, 8));
        assertTrue(greaterThanOrEqualTo(255, 200, 8));
        assertTrue(greaterThanOrEqualTo(0, 255, 8));
        assertTrue(greaterThanOrEqualTo(100, 255, 8));
        assertTrue(greaterThanOrEqualTo(0, 200, 8));
        assertTrue(greaterThanOrEqualTo(44, 200, 8));
        assertTrue(greaterThanOrEqualTo(100 + 100, 100, 8));
        assertFalse(greaterThanOrEqualTo(100 + 100 + 100, 100, 8));
        assertTrue(greaterThanOrEqualTo(100, 100, 8));
    }
}
