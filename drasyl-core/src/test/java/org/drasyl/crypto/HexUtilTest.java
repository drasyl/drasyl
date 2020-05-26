/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.crypto;

import org.bouncycastle.util.encoders.Hex;
import org.drasyl.Benchmark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({ "java:S1607" })
class HexUtilTest {
    private byte[] byteArray;

    @BeforeEach
    void setUp() {
        byteArray = new byte[]{ 0x4f, 0x00, 0x10, 0x0d };
    }

    @Test
    void toStringAndFromStringShouldBeInverse() {
        String byteAsString = HexUtil.toString(byteArray);

        assertEquals(Hex.toHexString(byteArray), byteAsString);
        assertArrayEquals(byteArray, HexUtil.fromString(byteAsString));
        assertArrayEquals(byteArray, HexUtil.fromString(byteAsString.toLowerCase()));
        assertEquals("4f00100d", byteAsString);
    }

    @Test
    void shouldThrowExceptionOnNotConformStringRepresentations() {
        assertThrows(IllegalArgumentException.class, () -> HexUtil.fromString("A"));
        assertThrows(IllegalArgumentException.class, () -> HexUtil.fromString("0Z"));
    }

    @Disabled
    @Test
    @Benchmark
    void ownByteToString() {
        for (int i = 0; i < 10000000; i++) {
            HexUtil.toString(byteArray);
        }

        assertTrue(true);
    }

    @Disabled
    @Test
    @Benchmark
    void bouncycastleByteToString() {
        for (int i = 0; i < 10000000; i++) {
            Hex.toHexString(byteArray);
        }

        assertTrue(true);
    }
}