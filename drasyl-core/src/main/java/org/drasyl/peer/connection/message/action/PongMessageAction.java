package org.drasyl.peer.connection.message.action;

import org.drasyl.peer.connection.message.PongMessage;

public class PongMessageAction extends AbstractMessageAction<PongMessage> {
    public PongMessageAction(PongMessage message) {
        super(message);
    }
}
