package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.MessageExceptionMessage;
import org.drasyl.core.common.message.StatusMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.drasyl.core.common.message.StatusMessage.Code.STATUS_OK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessageExceptionMessageActionTest {
    private MessageExceptionMessage message;
    private ClientConnection clientConnection;
    private NodeServer nodeServer;
    private String correspondingId;
    private String msgID;

    @BeforeEach
    void setUp() {
        message = mock(MessageExceptionMessage.class);
        clientConnection = mock(ClientConnection.class);
        nodeServer = mock(NodeServer.class);
        correspondingId = "correspondingId";
        msgID = "id";

        when(message.getId()).thenReturn(msgID);
    }

    @Test
    void onMessageServerShouldShouldSetResponse() {
        MessageExceptionMessageAction action = new MessageExceptionMessageAction(message);

        action.onMessageServer(clientConnection, nodeServer);

        verify(clientConnection).setResponse(message);
        verifyNoInteractions(nodeServer);
    }
}