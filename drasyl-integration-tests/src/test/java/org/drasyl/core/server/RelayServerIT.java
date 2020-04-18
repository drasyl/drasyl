/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.server;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayServerIT {
    @BeforeEach
    void setUp() {
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
    }

    @Test
    public void shouldOpenAndCloseGracefully() throws RelayServerException, URISyntaxException {
        RelayServer server = new RelayServer();
        server.open();
        server.awaitOpen();
        server.close();
        server.awaitClose();

        assertTrue(server.getStoppedFuture().isDone());
    }

    @Test
    public void startFailed() throws RelayServerException, URISyntaxException {

        Config config =
                ConfigFactory.parseString("relay.entrypoint = \"ws://localhost:72522/\"").withFallback(ConfigFactory.load());
        RelayServer server = new RelayServer(config);
        server.open();
        assertThrows(RelayServerException.class, server::awaitOpen);
    }
}