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
package org.drasyl.util.network;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubnetTest {
    @Test
    void shouldWorkIPv4() throws UnknownHostException {
        final Subnet subnet = new Subnet("10.225.27.2/16");
        assertEquals(InetAddress.getByName("10.225.27.2"), subnet.address());
        assertEquals(16, subnet.netmaskLength());
        assertEquals(InetAddress.getByName("255.255.0.0"), subnet.netmask());
        assertEquals(InetAddress.getByName("10.225.0.0"), subnet.network());
        assertEquals(256 * 256 - 2, subnet.networkSize());
        assertEquals(InetAddress.getByName("10.225.0.1"), subnet.first());
        assertEquals(InetAddress.getByName("10.225.255.254"), subnet.last());
        assertThrows(IllegalArgumentException.class, () -> subnet.nth(-1));
        assertEquals(subnet.first(), subnet.nth(0));
        assertEquals(subnet.last(), subnet.nth(subnet.networkSize() - 1));
        assertThrows(IllegalArgumentException.class, () -> subnet.nth(256 * 256 - 2));
        assertFalse(subnet.contains(InetAddress.getByName("10.225.0.0")));
        assertTrue(subnet.contains(InetAddress.getByName("10.225.0.1")));
        assertTrue(subnet.contains(InetAddress.getByName("10.225.27.2")));
        assertTrue(subnet.contains(InetAddress.getByName("10.225.255.254")));
        assertFalse(subnet.contains(InetAddress.getByName("10.225.255.255")));
        assertFalse(subnet.contains(InetAddress.getByName("11.1.2.3")));

        final Subnet subnet2 = new Subnet("192.168.178.55/8");
        assertEquals(InetAddress.getByName("192.168.178.55"), subnet2.address());
        assertEquals(8, subnet2.netmaskLength());
        assertEquals(InetAddress.getByName("255.0.0.0"), subnet2.netmask());
        assertEquals(InetAddress.getByName("192.0.0.0"), subnet2.network());
        assertEquals(256 * 256 * 256 - 2, subnet2.networkSize());
        assertEquals(InetAddress.getByName("192.0.0.1"), subnet2.first());
        assertEquals(InetAddress.getByName("192.255.255.254"), subnet2.last());
        assertThrows(IllegalArgumentException.class, () -> subnet2.nth(-1));
        assertEquals(subnet2.first(), subnet2.nth(0));
        assertEquals(subnet2.last(), subnet2.nth(subnet2.networkSize() - 1));
        assertThrows(IllegalArgumentException.class, () -> subnet2.nth(256 * 256 * 256 - 2));
    }
}
