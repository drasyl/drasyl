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
package org.drasyl.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

import static org.drasyl.util.NetworkUtil.createInetAddress;
import static org.drasyl.util.NetworkUtil.getAddresses;
import static org.drasyl.util.NetworkUtil.getNetworkPrefixLength;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkUtilTest {
    @Nested
    class GetExternalIPv4Address {
        @Test
        void shouldReturnExternalIPv4Address() {
            InetAddress address = NetworkUtil.getExternalIPv4Address();

            if (address != null) {
                assertThat(address, instanceOf(Inet4Address.class));
            }
        }
    }

    @Nested
    class GetExternalIPv6Address {
        @Test
        void shouldReturnExternalIPv6Address() {
            InetAddress address = NetworkUtil.getExternalIPv6Address();

            if (address != null) {
                assertThat(address, instanceOf(Inet6Address.class));
            }
        }
    }

    @Nested
    class Available {
        @Test
        void shouldReturnCorrectValue() {
            try (ServerSocket socket = new ServerSocket(5555)) {
                assertFalse(NetworkUtil.available(5555));
            }
            catch (IOException e) {
            }

            assertTrue(NetworkUtil.available(4444));
        }

        @Test
        void shouldRejectInvalidPort() {
            assertThrows(IllegalArgumentException.class, () -> NetworkUtil.available(NetworkUtil.MIN_PORT_NUMBER - 1));
            assertThrows(IllegalArgumentException.class, () -> NetworkUtil.available(NetworkUtil.MAX_PORT_NUMBER + 1));
        }
    }

    @Nested
    class Alive {
        @Test
        void shouldReturnCorrectValue() {
            try (ServerSocket socket = new ServerSocket(2222)) {
                assertTrue(NetworkUtil.alive("127.0.0.1", 2222));
            }
            catch (IOException e) {
            }

            assertFalse(NetworkUtil.alive("127.0.0.1", 3333));
        }

        @Test
        void shouldRejectInvalidPort() {
            assertThrows(IllegalArgumentException.class, () -> NetworkUtil.alive("127.0.0.1", NetworkUtil.MIN_PORT_NUMBER - 1));
            assertThrows(IllegalArgumentException.class, () -> NetworkUtil.alive("127.0.0.1", NetworkUtil.MAX_PORT_NUMBER + 1));
        }
    }

    @Nested
    class GetAddresses {
        @Test
        void shouldReturnNotNull() {
            assertNotNull(getAddresses());
        }
    }

    @Nested
    class GetLocalHostName {
        @Test
        void shouldReturnHostName() throws UnknownHostException {
            assertEquals(
                    InetAddress.getLocalHost().getHostName(),
                    NetworkUtil.getLocalHostName()
            );
        }
    }

    @Nested
    class CreateInetAddress {
        @Test
        void shouldReturnInetAddressForValidString() throws UnknownHostException {
            assertEquals(InetAddress.getByName("192.168.188.112"), createInetAddress("192.168.188.112"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForInvalidString() {
            assertThrows(IllegalArgumentException.class, () -> createInetAddress("foo.bar"));
        }
    }

    @Nested
    class GetNetworkPrefixLength {
        @Test
        void shouldNotThrowException() {
            for (InetAddress address : getAddresses()) {
                assertDoesNotThrow(() -> getNetworkPrefixLength(address));
            }
        }
    }

    @Nested
    class Cidr2Netmask {
        @Test
        void shouldConvertToCorrectNetmaskForIPv4() throws UnknownHostException {
            InetAddress mask = InetAddress.getByName("255.255.255.0");
            short cidr = 24;

            assertArrayEquals(mask.getAddress(), NetworkUtil.cidr2Netmask(cidr, 4));
        }

        @Test
        void shouldConvertToCorrectNetmaskForIPv6() throws UnknownHostException {
            InetAddress mask = InetAddress.getByName("ffff:ffff:ffff:ff80::");
            short cidr = 57;

            assertArrayEquals(mask.getAddress(), NetworkUtil.cidr2Netmask(cidr, 16));
        }

        @Test
        void shouldThrowExceptionOnInvalidInput() {
            assertThrows(IllegalArgumentException.class, () -> NetworkUtil.cidr2Netmask((short) 24, 3));
            assertThrows(IllegalArgumentException.class, () -> NetworkUtil.cidr2Netmask((short) 24, 17));
        }
    }

    @Nested
    class SameNetwork {
        @Test
        void shouldWorkForSameNetworkIPv4() throws UnknownHostException {
            InetAddress currentIP = InetAddress.getByName("192.168.1.129");
            InetAddress firstIP = InetAddress.getByName("192.168.1.0");
            InetAddress lastIP = InetAddress.getByName("192.168.1.255");
            InetAddress lastNotIncludedIP = InetAddress.getByName("192.168.0.255");
            InetAddress firstNotIncludedIP = InetAddress.getByName("192.168.2.0");
            short netmask = (short) 24;

            byte[] currentIPBytes = currentIP.getAddress();

            assertTrue(NetworkUtil.sameNetwork(firstIP, currentIP, netmask));
            assertTrue(NetworkUtil.sameNetwork(lastIP, currentIP, netmask));
            assertTrue(NetworkUtil.sameNetwork(currentIP, currentIP, netmask));
            assertTrue(NetworkUtil.sameNetwork(currentIPBytes, currentIPBytes, netmask));
            assertFalse(NetworkUtil.sameNetwork(lastNotIncludedIP, currentIP, netmask));
            assertFalse(NetworkUtil.sameNetwork(firstNotIncludedIP, currentIP, netmask));
            assertFalse(NetworkUtil.sameNetwork(currentIPBytes, new byte[]{}, netmask));
        }

        @Test
        void shouldReturnFalseOnNull() {
            assertFalse(NetworkUtil.sameNetwork(new byte[]{}, null, (short) 24));
            assertFalse(NetworkUtil.sameNetwork(null, new byte[]{}, (short) 24));
        }

        @Test
        void shouldThrowExceptionOnNull() throws UnknownHostException {
            InetAddress address = InetAddress.getByName("192.168.1.129");

            assertThrows(NullPointerException.class, () -> NetworkUtil.sameNetwork(address, null, (short) 24));
            assertThrows(NullPointerException.class, () -> NetworkUtil.sameNetwork(null, address, (short) 24));
        }

        @Test
        void shouldWorkForSameNetworkIPv6() throws UnknownHostException {
            InetAddress currentIP = InetAddress.getByName("2001:0db8:85a3:08d3:1319:8a2e:0370:7347");
            InetAddress firstIP = InetAddress.getByName("2001:0db8:85a3:0880:0000:0000:0000:0000");
            InetAddress lastIP = InetAddress.getByName("2001:0db8:85a3:08ff:ffff:ffff:ffff:ffff");
            InetAddress lastNotIncludedIP = InetAddress.getByName("2001:0db8:85a3:07ff:ffff:ffff:ffff:ffff");
            InetAddress firstNotIncludedIP = InetAddress.getByName("2001:0db8:85a3:0980:0000:0000:0000:0000");
            short netmask = (short) 57;

            byte[] currentIPBytes = currentIP.getAddress();

            assertTrue(NetworkUtil.sameNetwork(firstIP, currentIP, netmask));
            assertTrue(NetworkUtil.sameNetwork(lastIP, currentIP, netmask));
            assertTrue(NetworkUtil.sameNetwork(currentIP, currentIP, netmask));
            assertTrue(NetworkUtil.sameNetwork(currentIPBytes, currentIPBytes, netmask));
            assertFalse(NetworkUtil.sameNetwork(lastNotIncludedIP, currentIP, netmask));
            assertFalse(NetworkUtil.sameNetwork(firstNotIncludedIP, currentIP, netmask));
            assertFalse(NetworkUtil.sameNetwork(currentIPBytes, new byte[]{}, netmask));
        }
    }
}
