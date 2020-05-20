/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.peer.connection.server.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.connection.ConnectionsManager;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.message.action.MessageAction;
import org.drasyl.peer.connection.message.action.ServerMessageAction;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

class NodeServerConnectionHandlerTest {
    private NodeServer nodeServer;
    private URI uri;
    private CompletableFuture<NodeServerConnection> completableFuture;
    private EmbeddedChannel channel;
    private NodeServerConnection clientConnection;
    private ResponseMessage<?, ?> responseMessage;
    private MessageAction messageAction;
    private Message<?> message;
    private ServerMessageAction serverMessageAction;
    private JoinMessage joinMessage;
    private CompressedPublicKey compressedPublicKey;
    private Messenger messenger;
    private ConnectionsManager connectionsManager;

    @BeforeEach
    void setUp() throws CryptoException {
        nodeServer = mock(NodeServer.class);
        uri = URI.create("ws://example.com");
        completableFuture = mock(CompletableFuture.class);
        clientConnection = mock(NodeServerConnection.class);
        responseMessage = mock(ResponseMessage.class);
        messageAction = mock(MessageAction.class);
        message = mock(Message.class);
        serverMessageAction = mock(ServerMessageAction.class);
        joinMessage = mock(JoinMessage.class);
        compressedPublicKey = CompressedPublicKey.of("030b6adedef11147b3eea4b4f526f1226ffab218f2b81497e5175e6496f7aa929d");
        messenger = mock(Messenger.class);
        connectionsManager = mock(ConnectionsManager.class);

        ChannelHandler handler = new NodeServerConnectionHandler(nodeServer, completableFuture, clientConnection, uri);
        channel = new EmbeddedChannel(handler);

        when(nodeServer.getMessenger()).thenReturn(messenger);
        when(messenger.getConnectionsManager()).thenReturn(connectionsManager);
    }

    @Test
    void shouldSetResponseForResponseMessageIfSessionExists() {
        when(responseMessage.getAction()).thenReturn(messageAction);

        ChannelHandler handler = new NodeServerConnectionHandler(nodeServer, completableFuture, clientConnection, uri);
        channel = new EmbeddedChannel(handler);

        channel.writeInbound(responseMessage);
        channel.flush();

        verify(clientConnection).setResponse(responseMessage);
    }

    @Test
    void shouldExecuteOnServerActionForMessageIfSessionExists() {
        when(message.getAction()).thenReturn(serverMessageAction);

        ChannelHandler handler = new NodeServerConnectionHandler(nodeServer, completableFuture, clientConnection, uri);
        channel = new EmbeddedChannel(handler);

        channel.writeInbound(message);
        channel.flush();

        verify(serverMessageAction).onMessageServer(clientConnection, nodeServer);
    }

    @Test
    void shouldCreateSessionOnJoinMessageIfNoSessionExists() {
        when(joinMessage.getPublicKey()).thenReturn(compressedPublicKey);

        ChannelHandler handler = new NodeServerConnectionHandler(nodeServer, completableFuture, null, uri);
        channel = new EmbeddedChannel(handler);

        channel.writeInbound(joinMessage);
        channel.flush();

        verify(completableFuture).complete(any());
    }

    @Test
    void shouldCreateNoSessionOnJoinMessageIfSessionExists() {
        ChannelHandler handler = new NodeServerConnectionHandler(nodeServer, completableFuture, clientConnection, uri);
        channel = new EmbeddedChannel(handler);

        channel.writeInbound(joinMessage);
        channel.flush();

        verify(completableFuture, never()).complete(any());
    }
}