package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.PongMessage;

public class PongMessageAction extends AbstractMessageAction<PongMessage> {
    public PongMessageAction(PongMessage message) {
        super(message);
    }
}
