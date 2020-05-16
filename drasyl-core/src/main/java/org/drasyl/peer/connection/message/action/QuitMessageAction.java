package org.drasyl.peer.connection.message.action;

import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerClientConnection;

import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;

public class QuitMessageAction extends AbstractMessageAction<QuitMessage> implements ServerMessageAction<QuitMessage> {
    public QuitMessageAction(QuitMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(NodeServerClientConnection session,
                                NodeServer nodeServer) {
        session.send(new StatusMessage(STATUS_OK, message.getId()));
        nodeServer.getMessenger().getConnectionsManager().closeConnection(session, message.getReason());
    }
}
