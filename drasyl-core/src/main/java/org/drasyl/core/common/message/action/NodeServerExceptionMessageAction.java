package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.NodeServerExceptionMessage;
import org.drasyl.core.common.message.ResponseMessage;
import org.drasyl.core.common.message.StatusMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeServerExceptionMessageAction extends AbstractMessageAction<NodeServerExceptionMessage> implements RespondableServerMessageAction<NodeServerExceptionMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(NodeServerExceptionMessageAction.class);

    public NodeServerExceptionMessageAction(NodeServerExceptionMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(ClientConnection session,
                                NodeServer nodeServer) {
        LOG.error("Received exception message: {}", message.getException());
        session.send(new ResponseMessage<>(StatusMessage.OK, message.getId()));
    }

    @Override
    public void onResponseServer(String responseMsgID,
                                 ClientConnection session,
                                 NodeServer nodeServer) {
        session.setResponse(new ResponseMessage<>(message, responseMsgID));
        LOG.error("Received response with exception message: {}", message.getException());
    }
}
