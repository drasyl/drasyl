package org.drasyl.peer.connection.direct;

import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class RequestPeerInformationCacheTest {
    private RequestPeerInformationCache underTest;

    @Nested
    class Add {
        @Mock
        private CompressedPublicKey publicKey;

        @Test
        void shouldReturnTrueForNewKeys() {
            underTest = new RequestPeerInformationCache(10, ofSeconds(10));

            assertTrue(underTest.add(publicKey));
        }

        @Test
        void shouldReturnFalseForDuplicateKeys() {
            underTest = new RequestPeerInformationCache(10, ofSeconds(10));
            underTest.add(publicKey);

            assertFalse(underTest.add(publicKey));
        }

        @Test
        void shouldExpireKeysAfterSomeTime() {
            underTest = new RequestPeerInformationCache(10, ofSeconds(1));
            assertTrue(underTest.add(publicKey));

            await().untilAsserted(() -> assertTrue(underTest.add(publicKey)));
        }

        @Test
        void shouldEvictEntriesWhenLimitIsExceeded(@Mock CompressedPublicKey publicKey1) {
            underTest = new RequestPeerInformationCache(1, ofSeconds(1));
            underTest.add(publicKey);
            underTest.add(publicKey1);

            assertTrue(underTest.add(publicKey));
        }
    }
}