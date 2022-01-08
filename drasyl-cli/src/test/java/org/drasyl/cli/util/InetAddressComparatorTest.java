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

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;

class InetAddressComparatorTest {
    @Test
    void shouldSortAddresses() throws UnknownHostException {
        final List<InetAddress> list = new ArrayList<>();
        list.add(InetAddress.getByName("200.1.1.0"));
        list.add(InetAddress.getByName("e389:4072:f718:5680:e259:413b:9093:116a"));
        list.add(InetAddress.getByName("66d9:470d:ae75:600f:0e87:dc9a:6e34:669a"));
        list.add(InetAddress.getByName("66:470d:ae75:600f:0e87:dc9a:6e34:669a"));
        list.add(InetAddress.getByName("21.1.1.0"));
        list.add(InetAddress.getByName("1.1.1.2"));
        list.add(InetAddress.getByName("1.1.1.1"));

        list.sort(new InetAddressComparator());

        assertThat(list, containsInRelativeOrder(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.1.1.2"),
                InetAddress.getByName("21.1.1.0"),
                InetAddress.getByName("200.1.1.0"),
                InetAddress.getByName("66:470d:ae75:600f:0e87:dc9a:6e34:669a"),
                InetAddress.getByName("66d9:470d:ae75:600f:0e87:dc9a:6e34:669a"),
                InetAddress.getByName("e389:4072:f718:5680:e259:413b:9093:116a")
        ));
    }
}
