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

package org.drasyl.core.common.tools;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NetworkToolTest {
    @Test
    public void getIPTest() throws IOException {
        assertNotEquals(NetworkTool.getExternalIPAddress(), null);
        assertNotEquals(NetworkTool.getExternalIPAddress(), "");
    }

    @Test
    public void availablePortTest() {
        try (ServerSocket socket = new ServerSocket(5555)) {
            Assert.assertFalse(NetworkTool.available(5555));
        } catch (IOException e) {
        }

        Assert.assertTrue(NetworkTool.available(4444));
    }

    @Test
    public void invalidPortTest() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            NetworkTool.available(NetworkTool.MIN_PORT_NUMBER - 1);
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            NetworkTool.available(NetworkTool.MAX_PORT_NUMBER + 1);
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            NetworkTool.alive("127.0.0.1", NetworkTool.MIN_PORT_NUMBER - 1);
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            NetworkTool.alive("127.0.0.1", NetworkTool.MAX_PORT_NUMBER + 1);
        });
    }

    @Test
    public void aliveTest() {
        try (ServerSocket socket = new ServerSocket(2222)) {
            Assert.assertTrue(NetworkTool.alive("127.0.0.1", 2222));
        } catch (IOException e) {
        }

        Assert.assertFalse(NetworkTool.alive("127.0.0.1", 3333));
    }
}
