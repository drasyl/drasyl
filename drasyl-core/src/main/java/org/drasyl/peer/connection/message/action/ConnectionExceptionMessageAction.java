package org.drasyl.peer.connection.message.action;

import org.drasyl.peer.connection.message.ConnectionExceptionMessage;

public class ConnectionExceptionMessageAction extends AbstractMessageAction<ConnectionExceptionMessage> implements MessageAction<ConnectionExceptionMessage> {
    public ConnectionExceptionMessageAction(ConnectionExceptionMessage message) {
        super(message);
    }
}
