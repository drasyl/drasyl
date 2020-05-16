package org.drasyl.peer.connection.message.action;

import org.drasyl.DrasylException;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.connection.PeerConnection;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerClientConnection;
import org.drasyl.peer.connection.superpeer.SuperPeerClient;
import org.drasyl.peer.connection.superpeer.SuperPeerConnection;

import static java.util.Objects.requireNonNull;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_NOT_FOUND;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;

public class ApplicationMessageAction extends AbstractMessageAction<ApplicationMessage> implements ServerMessageAction<ApplicationMessage>, ClientMessageAction<ApplicationMessage> {
    public ApplicationMessageAction(ApplicationMessage message) {
        super(message);
    }

    @Override
    public void onMessageServer(NodeServerClientConnection connection,
                                NodeServer nodeServer) {
        requireNonNull(connection);
        requireNonNull(nodeServer);
        forwardMessage(connection, nodeServer.getMessenger());
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

    @Override
    public void onMessageClient(SuperPeerConnection connection,
                                SuperPeerClient superPeerClient) {
        requireNonNull(connection);
        requireNonNull(superPeerClient);
        forwardMessage(connection, superPeerClient.getMessenger());
    }
}
