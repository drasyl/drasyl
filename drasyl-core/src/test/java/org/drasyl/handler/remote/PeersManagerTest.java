/*
 * Copyright (c) 2020-2024.
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
package org.drasyl.handler.remote;

import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;

@ExtendWith(MockitoExtension.class)
class PeersManagerTest {
    @Test
    void addPath(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peer,
                 @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
        final PeersManager peersManager = new PeersManager();

        assertTrue(peersManager.addPath(peer, Object.class, endpoint, (short) 5));
        assertTrue(peersManager.addPath(peer, Integer.class, endpoint, (short) 7));
        assertTrue(peersManager.addPath(peer, String.class, endpoint, (short) 6));
        assertFalse(peersManager.addPath(peer, String.class, endpoint, (short) 6));
    }

    @Test
    void removePath(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peer,
                    @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
        final PeersManager peersManager = new PeersManager();
        peersManager.addPath(peer, Object.class, endpoint, (short) 5);
        peersManager.addPath(peer, String.class, endpoint, (short) 6);
        peersManager.addPath(peer, Integer.class, endpoint, (short) 7);

        assertTrue(peersManager.removePath(peer, Object.class));
        assertFalse(peersManager.removePath(peer, Object.class));
        assertTrue(peersManager.removePath(peer, Integer.class));
        assertTrue(peersManager.removePath(peer, String.class));
        assertFalse(peersManager.removePath(peer, Object.class));
    }
}
