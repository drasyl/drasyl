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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class InetAddressUtilTest {
    // valid and invalid address values/scopes from https://github.com/google/guava/blob/master/android/guava-tests/test/com/google/common/net/InetAddressesTest.java
    public static final Set<String> INVALID_ADDRESSES = Set.of(
            "",
            "016.016.016.016",
            "016.016.016",
            "016.016",
            "016",
            "000.000.000.000",
            "000",
            "0x0a.0x0a.0x0a.0x0a",
            "0x0a.0x0a.0x0a",
            "0x0a.0x0a",
            "0x0a",
            "42.42.42.42.42",
            "42.42.42",
            "42.42",
            "42",
            "42..42.42",
            "42..42.42.42",
            "42.42.42.42.",
            "42.42.42.42...",
            ".42.42.42.42",
            ".42.42.42",
            "...42.42.42.42",
            "42.42.42.-0",
            "42.42.42.+0",
            ".",
            "...",
            "bogus",
            "bogus.com",
            "192.168.0.1.com",
            "12345.67899.-54321.-98765",
            "257.0.0.0",
            "42.42.42.-42",
            "42.42.42.ab",
            "3ffe::1.net",
            "3ffe::1::1",
            "1::2::3::4:5",
            "::7:6:5:4:3:2:", // should end with ":0"
            ":6:5:4:3:2:1::", // should begin with "0:"
            "2001::db:::1",
            "FEDC:9878",
            "+1.+2.+3.4",
            "1.2.3.4e0",
            "6:5:4:3:2:1:0", // too few parts
            "::7:6:5:4:3:2:1:0", // too many parts
            "7:6:5:4:3:2:1:0::", // too many parts
            "9:8:7:6:5:4:3::2:1", // too many parts
            "0:1:2:3::4:5:6:7", // :: must remove at least one 0.
            "3ffe:0:0:0:0:0:0:0:1", // too many parts (9 instead of 8)
            "3ffe::10000", // hextet exceeds 16 bits
            "3ffe::goog",
            "3ffe::-0",
            "3ffe::+0",
            "3ffe::-1",
            ":",
            ":::",
            "::1.2.3",
            "::1.2.3.4.5",
            "::1.2.3.4:",
            "1.2.3.4::",
            "2001:db8::1:",
            ":2001:db8::1",
            ":1:2:3:4:5:6:7",
            "1:2:3:4:5:6:7:",
            ":1:2:3:4:5:6:"
    );
    public static final Set<String> VALID_IP4_ADDRESSES = Set.of(
            "192.168.0.1"
//            "૧૯૨.૧૬૮.૦.૧" // 192.168.0.1 in Gujarati digits
    );
    public static final Set<String> VALID_IP6_ADDRESSES = Set.of(
            "3ffe::1",
            "::7:6:5:4:3:2:1",
            "::7:6:5:4:3:2:0",
            "7:6:5:4:3:2:1::",
            "0:6:5:4:3:2:1::"
//            "7::0.128.0.127",
//            "7::0.128.0.128",
//            "7::128.128.0.127",
//            "7::0.128.128.127",
//            "૩ffe::૧" // 3ffe::1 with Gujarati digits for 3 and 1
    );
    public static final Set<String> VALID_ADDRESSES = SetUtil.merge(VALID_IP4_ADDRESSES, VALID_IP6_ADDRESSES);
    public static final Set<String> VALID_SCOPES = Set.of(
            "eno1",
            "en1",
            "eth0",
            "X",
            "1",
            "2",
            "14",
            "20"
    );

    @Nested
    class IsInetAddress {
        @Test
        void shouldReturnTrueForValidAddresses() {
            for (final String address : VALID_ADDRESSES) {
                assertTrue(InetAddressUtil.isInetAddress(address), address);
            }
        }

        @Test
        void shouldReturnFalseForInvalidAddresses() {
            for (final String address : INVALID_ADDRESSES) {
                assertFalse(InetAddressUtil.isInetAddress(address), address);
            }
        }

        @Test
        void shouldReturnFalseForValidIPv4AddressesWithScope() {
            final Set<String> addresses = SetUtil.cartesianProduct(VALID_IP4_ADDRESSES, VALID_SCOPES).stream().map(p -> p.first() + "%" + p.second()).collect(Collectors.toSet());
            for (final String address : addresses) {
                assertFalse(InetAddressUtil.isInetAddress(address), address);
            }
        }

        @Test
        void shouldReturnTrueForValidIPv6AddressesWithScope() {
            final Set<String> addresses = SetUtil.cartesianProduct(VALID_IP6_ADDRESSES, VALID_SCOPES).stream().map(p -> p.first() + "%" + p.second()).collect(Collectors.toSet());
            for (final String address : addresses) {
                assertTrue(InetAddressUtil.isInetAddress(address), address);
            }
        }
    }
}
