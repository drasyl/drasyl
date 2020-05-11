package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.ResponseMessage;
import org.drasyl.core.common.message.StatusMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StatusMessageActionTest {
    private StatusMessage message;
    private ClientConnection clientConnection;
    private NodeServer server;
    private String correspondingId;
    private String id;

    @BeforeEach
    void setUp() {
        message = mock(StatusMessage.class);
        clientConnection = mock(ClientConnection.class);
        server = mock(NodeServer.class);
        correspondingId = "correspondingId";
        id = "id";

        when(message.getId()).thenReturn(id);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void onMessageServerShouldSentStatusNotImplemented() {
        StatusMessageAction action = new StatusMessageAction(message);

        action.onMessageServer(clientConnection, server);

        verify(clientConnection).send(eq(new ResponseMessage<>(StatusMessage.NOT_IMPLEMENTED, message.getId())));
        verify(clientConnection, never()).setResponse(any());
    }

    @Test
    void onResponseServerShouldSetResponse() {
        StatusMessageAction action = new StatusMessageAction(message);

        action.onResponseServer(correspondingId, clientConnection, server);

        verify(clientConnection).setResponse(new ResponseMessage<>(message, correspondingId));
        verify(clientConnection, never()).send(any());
    }
}