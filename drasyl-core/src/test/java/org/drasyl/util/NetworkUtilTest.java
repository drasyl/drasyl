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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

import static org.drasyl.util.NetworkUtil.createInetAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkUtilTest {
    @Nested
    class GetExternalIPAddress {
        @Test
        void shouldReturnCorrectValue() throws IOException {
            assertNotNull(NetworkUtil.getExternalIPAddress());
            assertNotEquals("", NetworkUtil.getExternalIPAddress());
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
}
