package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.ConnectionExceptionMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionExceptionMessageAction extends AbstractMessageAction<ConnectionExceptionMessage> implements MessageAction<ConnectionExceptionMessage> {
    public ConnectionExceptionMessageAction(ConnectionExceptionMessage message) {
        super(message);
    }
}
