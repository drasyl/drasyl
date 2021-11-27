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
package org.drasyl.cli.tunnel;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TunnelServiceConverterTest {
    @Test
    void convert() throws Exception {
        final TunnelServiceConverter converter = new TunnelServiceConverter();

        assertEquals(new InetSocketAddress("127.0.0.1", 80), converter.convert("80"));
        assertEquals(new InetSocketAddress("192.168.1.2", 80), converter.convert("192.168.1.2:80"));
        assertEquals(new InetSocketAddress("2001:db8:85a3:8d3:1319:8a2e:370:7348", 443), converter.convert("[2001:db8:85a3:8d3:1319:8a2e:370:7348]:443"));
        assertThrows(IllegalArgumentException.class, () -> converter.convert("2001:db8:85a3:8d3:1319:8a2e:370"));
    }
}
