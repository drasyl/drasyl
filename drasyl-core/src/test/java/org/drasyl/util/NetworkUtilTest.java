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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkUtilTest {
    @Test
    void getIPTest() throws IOException {
        assertNotEquals(NetworkUtil.getExternalIPAddress(), null);
        assertNotEquals(NetworkUtil.getExternalIPAddress(), "");
    }

    @Test
    void availablePortTest() {
        try (ServerSocket socket = new ServerSocket(5555)) {
            assertFalse(NetworkUtil.available(5555));
        }
        catch (IOException e) {
        }

        assertTrue(NetworkUtil.available(4444));
    }

    @Test
    void invalidPortTest() {
        assertThrows(IllegalArgumentException.class, () -> NetworkUtil.available(NetworkUtil.MIN_PORT_NUMBER - 1));

        assertThrows(IllegalArgumentException.class, () -> NetworkUtil.available(NetworkUtil.MAX_PORT_NUMBER + 1));

        assertThrows(IllegalArgumentException.class, () -> NetworkUtil.alive("127.0.0.1", NetworkUtil.MIN_PORT_NUMBER - 1));

        assertThrows(IllegalArgumentException.class, () -> NetworkUtil.alive("127.0.0.1", NetworkUtil.MAX_PORT_NUMBER + 1));
    }

    @Test
    void aliveTest() {
        try (ServerSocket socket = new ServerSocket(2222)) {
            assertTrue(NetworkUtil.alive("127.0.0.1", 2222));
        }
        catch (IOException e) {
        }

        assertFalse(NetworkUtil.alive("127.0.0.1", 3333));
    }
}
