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

import static org.drasyl.event.EventType.EVENT_MESSAGE;
import static org.drasyl.event.EventType.EVENT_NODE_DOWN;
import static org.drasyl.event.EventType.EVENT_NODE_OFFLINE;
import static org.drasyl.event.EventType.EVENT_NODE_UP;
import static org.drasyl.event.EventType.EVENT_PEER_DIRECT;
import static org.drasyl.event.EventType.EVENT_PEER_RELAY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTypeTest {
    @Nested
    class IsNodeEvent {
        @Test
        void shouldReturnCorrectValue() {
            assertTrue(EVENT_NODE_UP.isNodeEvent());
            assertFalse(EVENT_PEER_DIRECT.isNodeEvent());
        }
    }

    @Nested
    class IsPeerEvent {
        @Test
        void shouldReturnCorrectValue() {
            assertTrue(EVENT_PEER_RELAY.isPeerEvent());
            assertFalse(EVENT_NODE_DOWN.isPeerEvent());
        }
    }

    @Nested
    class IsMessageEvent {
        @Test
        void shouldReturnCorrectValue() {
            assertTrue(EVENT_MESSAGE.isMessageEvent());
            assertFalse(EVENT_NODE_OFFLINE.isMessageEvent());
        }
    }
}