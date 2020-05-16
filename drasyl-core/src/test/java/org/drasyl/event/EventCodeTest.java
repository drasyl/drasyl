package org.drasyl.event;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EventCodeTest {
    @Test
    void isNodeEvent() {
        Assertions.assertTrue(EventCode.EVENT_NODE_UP.isNodeEvent());
        Assertions.assertFalse(EventCode.EVENT_PEER_P2P.isNodeEvent());
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