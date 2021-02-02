/*
 * Copyright (c) 2020-2021.
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings({ "java:S1607" })
class HexUtilTest {
    private final byte[] byteArray = new byte[]{ 0x4f, 0x00, 0x10, 0x0d };
    private final String byteString = "4f00100d";

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectString() {
            assertEquals(Hex.toHexString(byteArray), byteString);
        }
    }

    @Nested
    class FromString {
        @Test
        void shouldReturnCorrectByteArray() {
            assertArrayEquals(byteArray, HexUtil.fromString(byteString));
            assertArrayEquals(byteArray, HexUtil.fromString(byteString.toLowerCase()));
        }

        @Test
        void shouldThrowExceptionOnNotConformStringRepresentations() {
            assertThrows(IllegalArgumentException.class, () -> HexUtil.fromString("A"));
            assertThrows(IllegalArgumentException.class, () -> HexUtil.fromString("0Z"));
        }
    }
}
