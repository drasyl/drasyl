package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.ResponseMessage;
import org.drasyl.core.common.message.StatusMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;

public class StatusMessageAction extends AbstractMessageAction<StatusMessage> implements RespondableServerMessageAction<StatusMessage> {
    public StatusMessageAction(StatusMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(ClientConnection session,
                                NodeServer nodeServer) {
        session.send(new ResponseMessage<>(StatusMessage.NOT_IMPLEMENTED, message.getId()));
    }

    @Override
    public void onResponseServer(String responseMsgID,
                                 ClientConnection session,
                                 NodeServer nodeServer) {
        session.setResponse(new ResponseMessage<>(message, responseMsgID));
    }
}
