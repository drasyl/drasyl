package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.LeaveMessage;
import org.drasyl.core.common.message.ResponseMessage;
import org.drasyl.core.common.message.StatusMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LeaveMessageActionTest {
    private LeaveMessage message;
    private ClientConnection clientConnection;
    private NodeServer server;
    private String id;

    @BeforeEach
    void setUp() {
        message = mock(LeaveMessage.class);
        clientConnection = mock(ClientConnection.class);
        server = mock(NodeServer.class);
        id = "id";

        when(message.getId()).thenReturn(id);
    }

    @Test
    void onMessageServerShouldSendResponseOkAndCloseConnection() {
        LeaveMessageAction action = new LeaveMessageAction(message);

        action.onMessageServer(clientConnection, server);

        verify(clientConnection).send(new ResponseMessage<>(StatusMessage.OK, message.getId()));
        verify(clientConnection).close();
        verifyNoInteractions(server);
    }
}