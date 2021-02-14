/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.cli.command.perf.message;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class SessionRequestTest {
    @Nested
    class Constructor {
        @Test
        void shouldRejectInvalidTestDuration() {
            assertThrows(IllegalArgumentException.class, () -> new SessionRequest(0, 1, 1, false));
        }

        @Test
        void shouldRejectInvalidMessagesPerSecond() {
            assertThrows(IllegalArgumentException.class, () -> new SessionRequest(1, 0, 1, false));
        }

        @Test
        void shouldRejectInvalidMessageSize() {
            assertThrows(IllegalArgumentException.class, () -> new SessionRequest(1, 1, 0, false));
        }
    }

    @Nested
    class Getter {
        @Test
        void shouldReturnCorrectValues() {
            final SessionRequest request = new SessionRequest(1, 2, 3, true);

            assertEquals(1, request.getTime());
            assertEquals(2, request.getMps());
            assertEquals(3, request.getSize());
            assertTrue(request.isReverse());
        }
    }
}
