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
    }

    @Test
    void shouldExpireKeysAfterSomeTime(@Mock final CompressedPublicKey publicKey) {
        underTest = new RequestPeerInformationCache(10, ofMillis(100));
        assertTrue(underTest.add(publicKey));

        await().untilAsserted(() -> assertTrue(underTest.add(publicKey)));
    }

    @Test
    void shouldEvictEntriesWhenLimitIsExceeded(@Mock final CompressedPublicKey publicKey1,
                                               @Mock final CompressedPublicKey publicKey2) {
        underTest = new RequestPeerInformationCache(1, ofSeconds(1));
        underTest.add(publicKey1);
        underTest.add(publicKey2);

        assertTrue(underTest.add(publicKey1));
    }
}