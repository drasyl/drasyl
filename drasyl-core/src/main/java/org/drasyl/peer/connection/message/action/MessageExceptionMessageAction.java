package org.drasyl.peer.connection.message.action;

import org.drasyl.peer.connection.message.MessageExceptionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageExceptionMessageAction extends AbstractMessageAction<MessageExceptionMessage> implements MessageAction<MessageExceptionMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(MessageExceptionMessageAction.class);

    public MessageExceptionMessageAction(MessageExceptionMessage message) {
        super(message);
    }
}
