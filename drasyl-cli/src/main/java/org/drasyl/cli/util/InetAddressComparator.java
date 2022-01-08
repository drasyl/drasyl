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
package org.drasyl.cli.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Comparator;

/**
 * This {@link Comparator} sort IPv4 addreses before IPv6 addresses and will compare addresses of
 * the same IP version bitwise lexicographically.
 */
public class InetAddressComparator implements Comparator<InetAddress> {
    @Override
    public int compare(final InetAddress o1, final InetAddress o2) {
        final boolean o1Inet4 = o1 instanceof Inet4Address;
        final boolean o2Inet4 = o2 instanceof Inet4Address;

        if (o1Inet4 && !o2Inet4) {
            // o1 ipv4, o2 ipv6
            return -1;
        }
        else if (!o1Inet4 && o2Inet4) {
            // o1 ipv6, o2 ipv4
            return 1;
        }
        else {
            // both ipv4 or ipv6
            return Arrays.compareUnsigned(o1.getAddress(), o2.getAddress());
        }
    }
}
