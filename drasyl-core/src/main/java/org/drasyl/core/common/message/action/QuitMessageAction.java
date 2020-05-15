package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.QuitMessage;
import org.drasyl.core.common.message.StatusMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;

import static org.drasyl.core.common.message.StatusMessage.Code.STATUS_OK;

public class QuitMessageAction extends AbstractMessageAction<QuitMessage> implements ServerMessageAction<QuitMessage> {
    public QuitMessageAction(QuitMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(ClientConnection session,
                                NodeServer nodeServer) {
        session.send(new StatusMessage(STATUS_OK, message.getId()));
        nodeServer.getMessenger().getConnectionsManager().closeConnection(session, message.getReason());
    }
}
