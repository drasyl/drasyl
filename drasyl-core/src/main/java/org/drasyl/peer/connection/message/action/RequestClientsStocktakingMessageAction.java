package org.drasyl.peer.connection.message.action;

import org.drasyl.peer.connection.message.ClientsStocktakingMessage;
import org.drasyl.peer.connection.message.RequestClientsStocktakingMessage;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerClientConnection;

public class RequestClientsStocktakingMessageAction extends AbstractMessageAction<RequestClientsStocktakingMessage> implements ServerMessageAction<RequestClientsStocktakingMessage> {
    public RequestClientsStocktakingMessageAction(RequestClientsStocktakingMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(NodeServerClientConnection session,
                                NodeServer nodeServer) {
        session.send(new ClientsStocktakingMessage(nodeServer.getPeersManager().getChildren(), message.getId()));
    }
}
