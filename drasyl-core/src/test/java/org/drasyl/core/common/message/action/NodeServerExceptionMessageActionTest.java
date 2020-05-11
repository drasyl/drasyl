package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.NodeServerExceptionMessage;
import org.drasyl.core.common.message.ResponseMessage;
import org.drasyl.core.common.message.StatusMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NodeServerExceptionMessageActionTest {
    private NodeServerExceptionMessage message;
    private ClientConnection clientConnection;
    private NodeServer nodeServer;
    private String correspondingId;
    private String msgID;

    @BeforeEach
    void setUp() {
        message = mock(NodeServerExceptionMessage.class);
        clientConnection = mock(ClientConnection.class);
        nodeServer = mock(NodeServer.class);
        correspondingId = "correspondingId";
        msgID = "id";

        when(message.getId()).thenReturn(msgID);
    }

    @Test
    void onMessageServerShouldShouldSendResponseOk() {
        NodeServerExceptionMessageAction action = new NodeServerExceptionMessageAction(message);

        action.onMessageServer(clientConnection, nodeServer);

        verify(clientConnection).send(new ResponseMessage<>(StatusMessage.OK, message.getId()));
        verifyNoInteractions(nodeServer);
    }

    @Test
    void onResponseServerShouldShouldSetResponseOk() {
        NodeServerExceptionMessageAction action = new NodeServerExceptionMessageAction(message);

        action.onResponseServer(correspondingId, clientConnection, nodeServer);

        verify(clientConnection).setResponse(new ResponseMessage<>(message, correspondingId));
        verifyNoInteractions(nodeServer);
    }
}