package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.ResponseMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseMessageAction extends AbstractMessageAction<ResponseMessage> implements ServerMessageAction<ResponseMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(ResponseMessageAction.class);

    public ResponseMessageAction(ResponseMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(ClientConnection session,
                                NodeServer nodeServer) {
        MessageAction action = message.getAction();
        if (action instanceof RespondableServerMessageAction) {
            ((RespondableServerMessageAction) action).onResponseServer(message.getCorrespondingId(), session, nodeServer);
        }
        else {
            LOG.warn("Unable to response to message: {}", message);
        }
    }
}
