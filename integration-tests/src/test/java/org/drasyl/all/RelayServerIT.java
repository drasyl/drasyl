/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayServerIT {
    @BeforeEach
    void setUp() {
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
    }

    @Test
    public void shouldOpenAndCloseGracefully() throws DrasylException {
        Drasyl drasyl = new Drasyl();
        drasyl.open();
        drasyl.awaitOpen();
        drasyl.close();
        drasyl.awaitClose();

        assertTrue(drasyl.getStoppedFuture().isDone());
    }

    @Test
    public void startFailed() throws DrasylException {
        Config config = ConfigFactory.parseString("relay.port = 72522").withFallback(ConfigFactory.load());
        Drasyl drasyl = new Drasyl(config);
        drasyl.open();
        assertThrows(DrasylException.class, drasyl::awaitOpen);
    }
}