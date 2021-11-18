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
import static org.drasyl.util.InetSocketAddressUtil.socketAddressToString;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class InetSocketAddressUtilTest {
    @Nested
    class SocketAddressFromString {
        @Test
        void shouldReturnCorrectSocketAddress() {
            InetSocketAddress address = socketAddressFromString("127.0.0.1");
            assertEquals("127.0.0.1", address.getAddress().getHostAddress());

            final InetSocketAddress ip6 = socketAddressFromString("2001:db8:85a3:8d3:1319:8a2e:370:7348");
            assertEquals("2001:db8:85a3:8d3:1319:8a2e:370:7348", ip6.getAddress().getHostAddress());

            final InetSocketAddress ip6port = socketAddressFromString("[2001:db8:85a3:8d3:1319:8a2e:370:7348]:443");
            assertEquals("2001:db8:85a3:8d3:1319:8a2e:370:7348", ip6port.getAddress().getHostAddress());
            assertEquals(443, ip6port.getPort());

            final String host = "example.com";
            address = socketAddressFromString(host);
            assertEquals(host, address.getHostName());

            final String hostAndPort = host + ":80";
            address = socketAddressFromString(hostAndPort);
            assertEquals(host, address.getHostName());
            assertEquals(80, address.getPort());
        }
    }

    @Nested
    class SocketAddressToString {
        @Test
        void shouldReturnCorrectString() {
            assertEquals("127.0.0.1:8080", socketAddressToString(new InetSocketAddress("127.0.0.1", 8080)));
            assertEquals("example.com:6667", socketAddressToString(new InetSocketAddress("example.com", 6667)));
            assertEquals("[2001:db8:85a3:8d3:1319:8a2e:370:7348]:443", socketAddressToString(new InetSocketAddress("2001:db8:85a3:8d3:1319:8a2e:370:7348", 443)));
        }
    }
}
