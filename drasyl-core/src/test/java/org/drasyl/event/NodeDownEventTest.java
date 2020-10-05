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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class NodeDownEventTest {
    @Mock
    private Node node;

    @Nested
    class GetMessage {
        @Test
        void shouldReturnMessage() {
            final NodeDownEvent event = new NodeDownEvent(node);

            assertEquals(node, event.getNode());
        }
    }

    @Nested
    class Equals {
        @Mock
        private Node node2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            final NodeDownEvent event1 = new NodeDownEvent(node);
            final NodeDownEvent event2 = new NodeDownEvent(node);
            final NodeDownEvent event3 = new NodeDownEvent(node2);

            assertEquals(event1, event2);
            assertNotEquals(event1, event3);
        }
    }

    @Nested
    class HashCode {
        @Mock
        private Node node2;

        @Test
        void notSameBecauseOfDifferentMessage() {
            final NodeDownEvent event1 = new NodeDownEvent(node);
            final NodeDownEvent event2 = new NodeDownEvent(node);
            final NodeDownEvent event3 = new NodeDownEvent(node2);

            assertEquals(event1.hashCode(), event2.hashCode());
            assertNotEquals(event1.hashCode(), event3.hashCode());
        }
    }
}