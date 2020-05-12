package org.drasyl.core.server.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ServerChannel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.drasyl.core.common.handler.codec.message.MessageDecoder;
import org.drasyl.core.common.message.LeaveMessage;
import org.drasyl.core.common.message.Message;
import org.drasyl.core.common.message.ResponseMessage;
import org.drasyl.core.common.message.StatusMessage;
import org.drasyl.core.common.message.action.MessageAction;
import org.drasyl.core.common.message.action.ServerMessageAction;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerSessionHandlerTest {
    private NodeServer nodeServer;
    private URI uri;
    private CompletableFuture<ClientConnection> completableFuture;
    private EmbeddedChannel channel;
    private ClientConnection clientConnection;
    private ResponseMessage responseMessage;
    private MessageAction messageAction;
    private Message message;
    private ServerMessageAction serverMessageAction;

    @BeforeEach
    void setUp() {
        nodeServer = mock(NodeServer.class);
        uri = URI.create("ws://example.com");
        completableFuture = mock(CompletableFuture.class);
        clientConnection = mock(ClientConnection.class);
        responseMessage = mock(ResponseMessage.class);
        messageAction = mock(MessageAction.class);
        message = mock(Message.class);
        serverMessageAction = mock(ServerMessageAction.class);

        ChannelHandler handler = new ServerSessionHandler(nodeServer, completableFuture, clientConnection, uri);
        channel = new EmbeddedChannel(handler);
    }

    @Test
    void shouldSetResponseForResponseMessageIfSessionIsCreated() {
        when(responseMessage.getAction()).thenReturn(messageAction);

        channel.writeInbound(responseMessage);
        channel.flush();

        verify(clientConnection).setResponse(responseMessage);
    }

    @Test
    void shouldExecuteOnServerActionForMessageIfSessionIsCreated() {
        when(message.getAction()).thenReturn(serverMessageAction);

        channel.writeInbound(message);
        channel.flush();

        verify(serverMessageAction).onMessageServer(clientConnection, nodeServer);
    }
}