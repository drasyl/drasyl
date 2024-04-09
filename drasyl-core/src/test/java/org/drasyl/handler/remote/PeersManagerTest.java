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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;

@ExtendWith(MockitoExtension.class)
class PeersManagerTest {
    public static final Class<?> PATH_ID_1 = Object.class;
    public static final Class<?> PATH_ID_2 = Integer.class;
    public static final Class<?> PATH_ID_3 = String.class;

    @Nested
    class AddPath {
        @Test
        void shouldReturnTrueIfPathIsNew(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peer,
                                         @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint1,
                                         @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint2,
                                         @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint3) {
            final PeersManager peersManager = new PeersManager();

            assertTrue(peersManager.addPath(peer, PATH_ID_1, endpoint1, (short) 5));
            assertTrue(peersManager.addPath(peer, PATH_ID_2, endpoint2, (short) 7));
            assertTrue(peersManager.addPath(peer, PATH_ID_3, endpoint3, (short) 6));
            assertFalse(peersManager.addPath(peer, PATH_ID_3, endpoint3, (short) 6));
        }

        @Test
        void shouldUpdateEndpoint(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peer,
                                  @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint1,
                                  @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint2) {
            final PeersManager peersManager = new PeersManager();

            peersManager.addPath(peer, PATH_ID_1, endpoint1, (short) 5);
            peersManager.addPath(peer, PATH_ID_1, endpoint2, (short) 5);

            assertEquals(endpoint2, peersManager.getEndpoint(peer, PATH_ID_1));
        }
    }

    @Nested
    class RemovePath {
        @Test
        void shouldReturnTrueIfPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peer,
                                                  @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
            final PeersManager peersManager = new PeersManager();
            peersManager.addPath(peer, PATH_ID_1, endpoint, (short) 5);
            peersManager.addPath(peer, PATH_ID_3, endpoint, (short) 6);
            peersManager.addPath(peer, PATH_ID_2, endpoint, (short) 7);

            assertTrue(peersManager.removePath(peer, PATH_ID_1));
            assertFalse(peersManager.removePath(peer, PATH_ID_1));
            assertTrue(peersManager.removePath(peer, PATH_ID_2));
            assertTrue(peersManager.removePath(peer, PATH_ID_3));
            assertFalse(peersManager.removePath(peer, PATH_ID_1));
        }
    }

    @Nested
    class GetEndpoints {
        @Test
        void shouldOrderEndpointsByPriority(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peer,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint1,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint2,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint3) {
            final PeersManager peersManager = new PeersManager();

            peersManager.addPath(peer, PATH_ID_1, endpoint1, (short) 5);
            peersManager.addPath(peer, PATH_ID_2, endpoint2, (short) 7);
            peersManager.addPath(peer, PATH_ID_3, endpoint3, (short) 6);

            assertEquals(List.of(endpoint1, endpoint3, endpoint2), peersManager.getEndpoints(peer));
        }
    }
}
