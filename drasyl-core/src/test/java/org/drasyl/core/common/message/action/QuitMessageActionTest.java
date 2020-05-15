package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.QuitMessage;
import org.drasyl.core.node.connections.PeerConnection.CloseReason;
import org.drasyl.core.common.message.StatusMessage;
import org.drasyl.core.node.ConnectionsManager;
import org.drasyl.core.node.Messenger;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.drasyl.core.node.connections.PeerConnection.CloseReason.REASON_SHUTTING_DOWN;
import static org.drasyl.core.common.message.StatusMessage.Code.STATUS_OK;
import static org.mockito.Mockito.*;

class QuitMessageActionTest {
    private QuitMessage message;
    private ClientConnection clientConnection;
    private NodeServer server;
    private String id;
    private Messenger messenger;
    private ConnectionsManager connectionsManager;
    private CloseReason reason;

    @BeforeEach
    void setUp() {
        message = mock(QuitMessage.class);
        clientConnection = mock(ClientConnection.class);
        server = mock(NodeServer.class);
        id = "id";
        messenger = mock(Messenger.class);
        connectionsManager = mock(ConnectionsManager.class);
        reason = REASON_SHUTTING_DOWN;

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