/*
 * Copyright (c) 2020.
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