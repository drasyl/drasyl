package org.drasyl.peer.connection.message.action;

import org.drasyl.messenger.Messenger;
import org.drasyl.peer.connection.ConnectionsManager;
import org.drasyl.peer.connection.PeerConnection;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerClientConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.mockito.Mockito.*;

class QuitMessageActionTest {
    private QuitMessage message;
    private NodeServerClientConnection clientConnection;
    private NodeServer server;
    private String id;
    private Messenger messenger;
    private ConnectionsManager connectionsManager;
    private PeerConnection.CloseReason reason;

    @BeforeEach
    void setUp() {
        message = mock(QuitMessage.class);
        clientConnection = mock(NodeServerClientConnection.class);
        server = mock(NodeServer.class);
        id = "id";
        messenger = mock(Messenger.class);
        connectionsManager = mock(ConnectionsManager.class);
        reason = PeerConnection.CloseReason.REASON_SHUTTING_DOWN;

        when(message.getId()).thenReturn(id);
        when(server.getMessenger()).thenReturn(messenger);
        when(messenger.getConnectionsManager()).thenReturn(connectionsManager);
        when(message.getReason()).thenReturn(reason);
    }

    @Test
    void onMessageServerShouldSendStatusOkAndCloseConnection() {
        QuitMessageAction action = new QuitMessageAction(message);

        action.onMessageServer(clientConnection, server);

        verify(clientConnection).send(new StatusMessage(STATUS_OK, message.getId()));
        verify(connectionsManager).closeConnection(clientConnection, reason);
    }
}