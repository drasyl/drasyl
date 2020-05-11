package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.ClientsStocktakingMessage;
import org.drasyl.core.common.message.RequestClientsStocktakingMessage;
import org.drasyl.core.common.message.ResponseMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;

public class RequestClientsStocktakingMessageAction extends AbstractMessageAction<RequestClientsStocktakingMessage> implements ServerMessageAction<RequestClientsStocktakingMessage> {
    public RequestClientsStocktakingMessageAction(RequestClientsStocktakingMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(ClientConnection session,
                                NodeServer nodeServer) {
        session.send(new ResponseMessage<>(new ClientsStocktakingMessage(nodeServer.getPeersManager().getChildren()),
                message.getId()));
    }
}
