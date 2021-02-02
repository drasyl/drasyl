/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.event;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class PeerDirectEventTest {
    @Mock
    private Peer peer;

    @Nested
    class GetMessage {
        @Test
        void shouldReturnMessage() {
            final PeerDirectEvent event = new PeerDirectEvent(peer);

            assertEquals(peer, event.getPeer());
        }
    }

    @Nested
    class Equals {
        @Mock
        private Peer peer2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            final PeerDirectEvent event1 = new PeerDirectEvent(peer);
            final PeerDirectEvent event2 = new PeerDirectEvent(peer);
            final PeerDirectEvent event3 = new PeerDirectEvent(peer2);

            assertEquals(event1, event2);
            assertNotEquals(event1, event3);
        }
    }

    @Nested
    class HashCode {
        @Mock
        private Peer peer2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            final PeerDirectEvent event1 = new PeerDirectEvent(peer);
            final PeerDirectEvent event2 = new PeerDirectEvent(peer);
            final PeerDirectEvent event3 = new PeerDirectEvent(peer2);

            assertEquals(event1.hashCode(), event2.hashCode());
            assertNotEquals(event1.hashCode(), event3.hashCode());
        }
    }
}
