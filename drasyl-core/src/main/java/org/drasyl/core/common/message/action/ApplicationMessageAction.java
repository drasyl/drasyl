package org.drasyl.core.common.message.action;

import org.drasyl.core.client.SuperPeerClient;
import org.drasyl.core.common.message.ApplicationMessage;
import org.drasyl.core.common.message.StatusMessage;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.Messenger;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.node.connections.PeerConnection;
import org.drasyl.core.node.connections.SuperPeerConnection;
import org.drasyl.core.server.NodeServer;

import static java.util.Objects.requireNonNull;
import static org.drasyl.core.common.message.StatusMessage.Code.STATUS_NOT_FOUND;
import static org.drasyl.core.common.message.StatusMessage.Code.STATUS_OK;

public class ApplicationMessageAction extends AbstractMessageAction<ApplicationMessage> implements ServerMessageAction<ApplicationMessage>, ClientMessageAction<ApplicationMessage> {
    public ApplicationMessageAction(ApplicationMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(ClientConnection connection,
                                NodeServer nodeServer) {
        requireNonNull(connection);
        requireNonNull(nodeServer);
        forwardMessage(connection, nodeServer.getMessenger());
    }

    @Override
    public void onMessageClient(SuperPeerConnection connection,
                                SuperPeerClient superPeerClient) {
        requireNonNull(connection);
        requireNonNull(superPeerClient);
        forwardMessage(connection, superPeerClient.getMessenger());
    }

    private void forwardMessage(PeerConnection connection, Messenger messenger) {
        requireNonNull(connection);
        requireNonNull(messenger);

        try {
            messenger.send(message);
            connection.send(new StatusMessage(STATUS_OK, message.getId()));
        }
        catch (DrasylException exception) {
            connection.send(new StatusMessage(STATUS_NOT_FOUND, message.getId()));
        }
    }
}
