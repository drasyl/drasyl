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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;

import static org.drasyl.util.InetSocketAddressUtil.socketAddressFromString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class InetSocketAddressUtilTest {
    @Nested
    class SocketAddressFromString {
        @Test
        void shouldReturnCorrectSocketAddress() {
            assertEquals(new InetSocketAddress("example.com", 22527), socketAddressFromString("example.com:22527"));
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void shouldThrowNullPointerExceptionForNullString() {
            assertThrows(NullPointerException.class, () -> socketAddressFromString(null));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForStringWithoutHostname() {
            assertThrows(IllegalArgumentException.class, () -> socketAddressFromString("123"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForStringWithoutPort() {
            assertThrows(IllegalArgumentException.class, () -> socketAddressFromString("example.com"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForStringWithInvalidPort() {
            assertThrows(IllegalArgumentException.class, () -> socketAddressFromString("example.com:999999"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForStringWithInvalidPortFormat() {
            assertThrows(IllegalArgumentException.class, () -> socketAddressFromString("example.com:baz"));
        }
    }
}
