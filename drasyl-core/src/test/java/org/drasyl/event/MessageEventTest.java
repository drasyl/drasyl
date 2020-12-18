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

package org.drasyl.event;

import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class MessageEventTest {
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private Object message;

    @Nested
    class GetMessage {
        @Test
        void shouldReturnMessage() {
            final MessageEvent event = new MessageEvent(sender, message);

            assertEquals(message, event.getPayload());
        }
    }

    @Nested
    class Equals {
        @Mock
        private Object message2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            final MessageEvent event1 = new MessageEvent(sender, message);
            final MessageEvent event2 = new MessageEvent(sender, message);
            final MessageEvent event3 = new MessageEvent(sender, message2);

            assertEquals(event1, event2);
            assertNotEquals(event1, event3);
        }

        @Test
        void sameBecauseOfEqualPayload() {
            final MessageEvent event1 = new MessageEvent(sender, "Hallo Welt".getBytes());
            final MessageEvent event2 = new MessageEvent(sender, "Hallo Welt".getBytes());

            assertEquals(event1, event2);
        }
    }

    @Nested
    class HashCode {
        @Mock
        private Object message2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            final MessageEvent event1 = new MessageEvent(sender, message);
            final MessageEvent event2 = new MessageEvent(sender, message);
            final MessageEvent event3 = new MessageEvent(sender, message2);

            assertEquals(event1.hashCode(), event2.hashCode());
            assertNotEquals(event1.hashCode(), event3.hashCode());
        }
    }
}