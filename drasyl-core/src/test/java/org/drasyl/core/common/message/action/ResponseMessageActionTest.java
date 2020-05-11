package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.ResponseMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ResponseMessageActionTest {
    private ClientConnection clientConnection;
    private NodeServer relay;
    private String correspondingId;
    private ResponseMessage message;
    private String id;
    private RespondableServerMessageAction serverMessageAction;

    @BeforeEach
    void setUp() {
        message = mock(ResponseMessage.class);
        clientConnection = mock(ClientConnection.class);
        relay = mock(NodeServer.class);
        correspondingId = "correspondingId";
        id = "id";

        when(message.getId()).thenReturn(id);
        serverMessageAction = mock(RespondableServerMessageAction.class);
        when(message.getAction()).thenReturn(serverMessageAction);
        when(message.getCorrespondingId()).thenReturn(correspondingId);
    }

    @Test
    void onMessageServerShouldCallOnResponseServer() {
        ResponseMessageAction action = new ResponseMessageAction(message);

        action.onMessageServer(clientConnection, relay);

        verify(serverMessageAction).onResponseServer(correspondingId, clientConnection, relay);
    }
}