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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EventCodeTest {
    @Test
    void isNodeEvent() {
        Assertions.assertTrue(EventCode.EVENT_NODE_UP.isNodeEvent());
        Assertions.assertFalse(EventCode.EVENT_PEER_DIRECT.isNodeEvent());
    }

    @Test
    void isPeerEvent() {
        Assertions.assertTrue(EventCode.EVENT_PEER_RELAY.isPeerEvent());
        Assertions.assertFalse(EventCode.EVENT_NODE_DOWN.isPeerEvent());
    }

    @Test
    void isMessageEvent() {
        Assertions.assertTrue(EventCode.EVENT_MESSAGE.isMessageEvent());
        Assertions.assertFalse(EventCode.EVENT_NODE_OFFLINE.isMessageEvent());
    }
}