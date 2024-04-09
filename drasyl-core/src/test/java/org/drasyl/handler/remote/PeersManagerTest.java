/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
