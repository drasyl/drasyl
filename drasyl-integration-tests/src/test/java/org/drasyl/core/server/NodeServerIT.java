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

package org.drasyl.core.server;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.Messenger;
import org.drasyl.core.node.PeersManager;
import org.drasyl.core.node.identity.IdentityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class NodeServerIT {
    private IdentityManager identityManager;
    private PeersManager peersManager;
    private Messenger messenger;

    @BeforeEach
    void setUp() {
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        identityManager = mock(IdentityManager.class);
        peersManager = mock(PeersManager.class);
        messenger = mock(Messenger.class);
    }

    @Test
    public void shouldOpenAndCloseGracefully() throws DrasylException {
        NodeServer server = new NodeServer(identityManager, peersManager, messenger);
        server.open();
        server.awaitOpen();
        server.close();
        server.awaitClose();

        assertTrue(server.getStoppedFuture().isDone());
    }

    @Test
    public void startShouldFailIfInvalidPortIsGiven() throws DrasylException {
        Config config =
                ConfigFactory.parseString("drasyl.server.bind-port = 72522").withFallback(ConfigFactory.load());
        NodeServer server = new NodeServer(identityManager, messenger, peersManager, config);
        server.open();
        assertThrows(NodeServerException.class, server::awaitOpen);
    }
}