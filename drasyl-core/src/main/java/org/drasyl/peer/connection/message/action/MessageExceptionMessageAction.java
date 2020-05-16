package org.drasyl.peer.connection.message.action;

import org.drasyl.peer.connection.message.MessageExceptionMessage;

public class MessageExceptionMessageAction extends AbstractMessageAction<MessageExceptionMessage> implements MessageAction<MessageExceptionMessage> {
    public MessageExceptionMessageAction(MessageExceptionMessage message) {
        super(message);
    }
}
