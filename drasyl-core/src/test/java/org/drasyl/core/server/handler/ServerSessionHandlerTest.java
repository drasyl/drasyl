package org.drasyl.core.server.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ServerChannel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.drasyl.core.common.handler.codec.message.MessageDecoder;
import org.drasyl.core.common.message.*;
import org.drasyl.core.common.message.action.MessageAction;
import org.drasyl.core.common.message.action.ServerMessageAction;
import org.drasyl.core.models.CompressedPublicKey;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;
import org.drasyl.crypto.CryptoException;
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
    private JoinMessage joinMessage;
    private CompressedPublicKey compressedPublicKey;

    @BeforeEach
    void setUp() throws CryptoException {
        nodeServer = mock(NodeServer.class);
        uri = URI.create("ws://example.com");
        completableFuture = mock(CompletableFuture.class);
        clientConnection = mock(ClientConnection.class);
        responseMessage = mock(ResponseMessage.class);
        messageAction = mock(MessageAction.class);
        message = mock(Message.class);
        serverMessageAction = mock(ServerMessageAction.class);
        joinMessage = mock(JoinMessage.class);
        compressedPublicKey = CompressedPublicKey.of("030b6adedef11147b3eea4b4f526f1226ffab218f2b81497e5175e6496f7aa929d");

        ChannelHandler handler = new ServerSessionHandler(nodeServer, completableFuture, clientConnection, uri);
        channel = new EmbeddedChannel(handler);
    }

    @Test
    void shouldSetResponseForResponseMessageIfSessionExists() {
        when(responseMessage.getAction()).thenReturn(messageAction);

        ChannelHandler handler = new ServerSessionHandler(nodeServer, completableFuture, clientConnection, uri);
        channel = new EmbeddedChannel(handler);

        channel.writeInbound(responseMessage);
        channel.flush();

        verify(clientConnection).setResponse(responseMessage);
    }

    @Test
    void shouldExecuteOnServerActionForMessageIfSessionExists() {
        when(message.getAction()).thenReturn(serverMessageAction);

        ChannelHandler handler = new ServerSessionHandler(nodeServer, completableFuture, clientConnection, uri);
        channel = new EmbeddedChannel(handler);

        channel.writeInbound(message);
        channel.flush();

        verify(serverMessageAction).onMessageServer(clientConnection, nodeServer);
    }

    @Test
    void shouldCreateSessionOnJoinMessageIfNoSessionExists() {
        when(joinMessage.getPublicKey()).thenReturn(compressedPublicKey);

        ChannelHandler handler = new ServerSessionHandler(nodeServer, completableFuture, null, uri);
        channel = new EmbeddedChannel(handler);

        channel.writeInbound(joinMessage);
        channel.flush();

        verify(completableFuture).complete(any());
    }

    @Test
    void shouldCreateNoSessionOnJoinMessageIfSessionExists() {
        ChannelHandler handler = new ServerSessionHandler(nodeServer, completableFuture, clientConnection, uri);
        channel = new EmbeddedChannel(handler);

        channel.writeInbound(joinMessage);
        channel.flush();

        verify(completableFuture, never()).complete(any());
    }
}