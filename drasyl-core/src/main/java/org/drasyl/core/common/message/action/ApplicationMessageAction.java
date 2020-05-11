package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.ApplicationMessage;
import org.drasyl.core.common.message.ResponseMessage;
import org.drasyl.core.common.message.StatusMessage;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;

import static java.util.Objects.requireNonNull;

public class ApplicationMessageAction extends AbstractMessageAction<ApplicationMessage> implements ServerMessageAction<ApplicationMessage> {
    public ApplicationMessageAction(ApplicationMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(ClientConnection session,
                                NodeServer nodeServer) {
        requireNonNull(session);
        requireNonNull(nodeServer);

        try {
            nodeServer.send(message);
            session.send(new ResponseMessage<>(StatusMessage.OK, message.getId()));
        }
        catch (DrasylException exception) {
            session.send(new ResponseMessage<>(StatusMessage.NOT_FOUND, message.getId()));
        }
    }
}
