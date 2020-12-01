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
            final PeerInformation information1 = PeerInformation.of(Set.of(Endpoint.of("udp://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb")));
            final PeerInformation information2 = PeerInformation.of(Set.of(Endpoint.of("udp://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb")));
            final PeerInformation information3 = PeerInformation.of(Set.of(Endpoint.of("udp://example.de#033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55")));

            assertEquals(information1, information2);
            assertNotEquals(information2, information3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfText() {
            final PeerInformation information1 = PeerInformation.of(Set.of(Endpoint.of("udp://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb")));
            final PeerInformation information2 = PeerInformation.of(Set.of(Endpoint.of("udp://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb")));
            final PeerInformation information3 = PeerInformation.of(Set.of(Endpoint.of("udp://example.de#033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55")));

            assertEquals(information1.hashCode(), information2.hashCode());
            assertNotEquals(information2.hashCode(), information3.hashCode());
        }
    }
}