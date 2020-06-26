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
            PeerDirectEvent event = new PeerDirectEvent(peer);

            assertEquals(peer, event.getPeer());
        }
    }

    @Nested
    class Equals {
        @Mock
        private Peer peer2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            PeerDirectEvent event1 = new PeerDirectEvent(peer);
            PeerDirectEvent event2 = new PeerDirectEvent(peer);
            PeerDirectEvent event3 = new PeerDirectEvent(peer2);

            assertEquals(event1, event1);
            assertEquals(event1, event2);
            assertNotEquals(event1, event3);
            assertNotEquals(event1, null);
        }
    }

    @Nested
    class HashCode {
        @Mock
        private Peer peer2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            PeerDirectEvent event1 = new PeerDirectEvent(peer);
            PeerDirectEvent event2 = new PeerDirectEvent(peer);
            PeerDirectEvent event3 = new PeerDirectEvent(peer2);

            assertEquals(event1.hashCode(), event2.hashCode());
            assertNotEquals(event1.hashCode(), event3.hashCode());
        }
    }
}