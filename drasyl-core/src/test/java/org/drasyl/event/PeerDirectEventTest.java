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