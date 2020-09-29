package org.drasyl.peer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PeerInformationTest {
    @Nested
    class Equals {
        @Test
        void notSameBecauseOfText() {
            PeerInformation information1 = PeerInformation.of(Set.of(Endpoint.of("ws://example.com")));
            PeerInformation information2 = PeerInformation.of(Set.of(Endpoint.of("ws://example.com")));
            PeerInformation information3 = PeerInformation.of(Set.of(Endpoint.of("ws://example.de")));

            assertEquals(information1, information2);
            assertNotEquals(information2, information3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfText() {
            PeerInformation information1 = PeerInformation.of(Set.of(Endpoint.of("ws://example.com")));
            PeerInformation information2 = PeerInformation.of(Set.of(Endpoint.of("ws://example.com")));
            PeerInformation information3 = PeerInformation.of(Set.of(Endpoint.of("ws://example.de")));

            assertEquals(information1.hashCode(), information2.hashCode());
            assertNotEquals(information2.hashCode(), information3.hashCode());
        }
    }
}