package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.StatusMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;

public class StatusMessageAction extends AbstractMessageAction<StatusMessage> implements ServerMessageAction<StatusMessage> {
    public StatusMessageAction(StatusMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(ClientConnection session,
                                NodeServer nodeServer) {
        session.setResponse(message);
    }
}
