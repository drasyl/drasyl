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
package org.drasyl.remote.protocol;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class UserAgentTest {
    public static final UserAgent USER_AGENT_1 = UserAgent.generate();
    public static final UserAgent USER_AGENT_2 = new UserAgent(1);

    @Nested
    class ToString {
        @Test
        void shouldReturnUserAgent() {
            assertEquals("UserAgent{version=1}", USER_AGENT_2.toString());
        }
    }

    @Nested
    class GetVersion {
        @Test
        void shouldReturnVersionForFullUserAgent() {
            assertEquals(UserAgent.generate().getVersion(), USER_AGENT_1.getVersion());
        }

        @Test
        void shouldReturnVersionForNoCommentsUserAgent() {
            assertEquals(1, USER_AGENT_2.getVersion().getValue());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldRecognizeEqualPairs() {
            final UserAgent userAgentA = new UserAgent(1);
            final UserAgent userAgentB = new UserAgent(1);
            final UserAgent userAgentC = new UserAgent(2);

            assertEquals(userAgentA, userAgentA);
            assertEquals(userAgentA, userAgentB);
            assertEquals(userAgentB, userAgentA);
            assertNotEquals(null, userAgentA);
            assertNotEquals(userAgentA, userAgentC);
            assertNotEquals(userAgentC, userAgentA);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldRecognizeEqualPairs() {
            final UserAgent userAgentA = new UserAgent(1);
            final UserAgent userAgentB = new UserAgent(1);
            final UserAgent userAgentC = new UserAgent(2);

            assertEquals(userAgentA.hashCode(), userAgentB.hashCode());
            assertNotEquals(userAgentA.hashCode(), userAgentC.hashCode());
            assertNotEquals(userAgentB.hashCode(), userAgentC.hashCode());
        }
    }
}