package org.drasyl.peer.connection.direct;

import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DirectConnectionDemandsCacheTest {
    private DirectConnectionDemandsCache underTest;

    @Nested
    class Add {
        @Mock
        private CompressedPublicKey publicKey;

        @Test
        void shouldAddKey() {
            underTest = new DirectConnectionDemandsCache(10, ofSeconds(1));
            underTest.add(publicKey);

            assertTrue(underTest.contains(publicKey));
        }
    }

    @Test
    void shouldExpireNonPersistentDemandsAfterSomeTime(@Mock final CompressedPublicKey publicKey) {
        underTest = new DirectConnectionDemandsCache(10, ofSeconds(1));
        underTest.add(publicKey);

        await().untilAsserted(() -> assertFalse(underTest.contains(publicKey)));
    }

    @Test
    void shouldKeepPersistentDemandsAfterSomeTime(@Mock final CompressedPublicKey publicKey) throws InterruptedException {
        underTest = new DirectConnectionDemandsCache(10, ofMillis(100));
        for (int i = 0; i < 10; i++) {
            underTest.add(publicKey);
            Thread.sleep(11); // NOSONAR
        }

        assertTrue(underTest.contains(publicKey));
    }

    @Test
    void shouldEvictEntriesWhenLimitIsExceeded(@Mock final CompressedPublicKey publicKey1,
                                               @Mock final CompressedPublicKey publicKey2) {
        underTest = new DirectConnectionDemandsCache(1, ofSeconds(1));
        underTest.add(publicKey1);
        underTest.add(publicKey2);

        assertFalse(underTest.contains(publicKey1));
    }
}