package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.StatusMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.drasyl.core.common.message.StatusMessage.Code.STATUS_NOT_IMPLEMENTED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StatusMessageActionTest {
    private StatusMessage message;
    private ClientConnection clientConnection;
    private NodeServer server;
    private String id;

    @BeforeEach
    void setUp() {
        message = mock(StatusMessage.class);
        clientConnection = mock(ClientConnection.class);
        server = mock(NodeServer.class);
        id = "id";

        when(message.getId()).thenReturn(id);
    }

    @Test
    void onMessageServerShouldSetResponse() {
        StatusMessageAction action = new StatusMessageAction(message);

        action.onMessageServer(clientConnection, server);

        verify(clientConnection).setResponse(message);
        verify(clientConnection, never()).send(any());
    }
}