/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
            final PeerDirectEvent event = PeerDirectEvent.of(peer);

            assertEquals(peer, event.getPeer());
        }
    }

    @Nested
    class Equals {
        @Mock
        private Peer peer2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            final PeerDirectEvent event1 = PeerDirectEvent.of(peer);
            final PeerDirectEvent event2 = PeerDirectEvent.of(peer);
            final PeerDirectEvent event3 = PeerDirectEvent.of(peer2);

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
            final PeerDirectEvent event1 = PeerDirectEvent.of(peer);
            final PeerDirectEvent event2 = PeerDirectEvent.of(peer);
            final PeerDirectEvent event3 = PeerDirectEvent.of(peer2);

            assertEquals(event1.hashCode(), event2.hashCode());
            assertNotEquals(event1.hashCode(), event3.hashCode());
        }
    }
}
