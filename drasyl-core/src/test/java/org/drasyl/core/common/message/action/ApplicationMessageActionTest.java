package org.drasyl.core.common.message.action;

import com.typesafe.config.ConfigFactory;
import org.drasyl.core.common.message.ApplicationMessage;
import org.drasyl.core.common.message.StatusMessage;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.DrasylNodeConfig;
import org.drasyl.core.node.PeersManager;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.server.NodeServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.drasyl.core.common.message.StatusMessage.Code.STATUS_NOT_FOUND;
import static org.drasyl.core.common.message.StatusMessage.Code.STATUS_OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApplicationMessageActionTest {
    private ApplicationMessage message;
    private ClientConnection clientConnection;
    private NodeServer nodeServer;
    private Identity sender, recipient;
    private byte[] payload;
    private String id;
    private PeersManager peersManager;

    @BeforeEach
    void setUp() {
        message = mock(ApplicationMessage.class);
        clientConnection = mock(ClientConnection.class);
        nodeServer = mock(NodeServer.class);
        peersManager = mock(PeersManager.class);
        sender = mock(Identity.class);
        recipient = mock(Identity.class);
        payload = new byte[]{ 0x00, 0x01, 0x03 };
        id = "id";

        when(nodeServer.getPeersManager()).thenReturn(peersManager);
        when(nodeServer.getConfig()).thenReturn(new DrasylNodeConfig(ConfigFactory.load()));

        when(message.getSender()).thenReturn(sender);
        when(message.getRecipient()).thenReturn(recipient);
        when(message.getPayload()).thenReturn(payload);
        when(message.getId()).thenReturn(id);
    }

    @Test
    void onMessageServerShouldSendStatusOkIfMessageCouldBeSent() throws DrasylException {
        ApplicationMessageAction action = new ApplicationMessageAction(message);
        action.onMessageServer(clientConnection, nodeServer);

        verify(clientConnection).send(new StatusMessage(STATUS_OK, message.getId()));
        verify(nodeServer).send(message);
    }

    @Test
    public void onMessageServerShouldSendStatusNotFoundIfMessageCouldNotBeSent() throws DrasylException {
        doThrow(DrasylException.class).when(nodeServer).send(any());

        ApplicationMessageAction action = new ApplicationMessageAction(message);
        action.onMessageServer(clientConnection, nodeServer);

        verify(clientConnection).send(new StatusMessage(STATUS_NOT_FOUND, message.getId()));
    }

    @Test
    public void onMessageServerShouldRejectNullValues() throws DrasylException {
        ApplicationMessageAction action = new ApplicationMessageAction(message);

        assertThrows(NullPointerException.class, () -> action.onMessageServer(null, nodeServer));
        assertThrows(NullPointerException.class, () -> action.onMessageServer(clientConnection, null));

        verify(clientConnection, never()).send(any());
        verify(nodeServer, never()).send(any());
    }
}