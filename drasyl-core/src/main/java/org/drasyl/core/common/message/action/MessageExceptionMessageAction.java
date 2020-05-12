package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.MessageExceptionMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageExceptionMessageAction extends AbstractMessageAction<MessageExceptionMessage> implements ServerMessageAction<MessageExceptionMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(MessageExceptionMessageAction.class);

    public MessageExceptionMessageAction(MessageExceptionMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(ClientConnection session,
                                NodeServer nodeServer) {
        LOG.error("Received exception message: {}", message.getException());
        session.setResponse(message);
    }
}
