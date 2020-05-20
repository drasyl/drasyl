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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.handler.ConnectionGuard;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.message.action.MessageAction;
import org.drasyl.peer.connection.message.action.ServerMessageAction;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.peer.connection.PeerConnection.CloseReason.REASON_INTERNAL_REJECTION;

/**
 * This handler mange in-/oncoming messages and pass them to the correct sub-function. It also
 * creates a new {@link NodeServerConnection} object if a {@link JoinMessage} has pass the
 * {@link NodeServerJoinGuard} guard.
 */
public class NodeServerConnectionHandler extends SimpleChannelInboundHandler<Message<?>> {
    public static final String HANDLER = "nodeServerConnectionHandler";
    private static final Logger LOG = LoggerFactory.getLogger(NodeServerConnectionHandler.class);
    private final NodeServer server;
    private final CompletableFuture<NodeServerConnection> sessionReadyFuture;
    private NodeServerConnection clientConnection;
    private URI uri;

    /**
     * Creates a new instance of this {@link io.netty.channel.ChannelHandler}
     *
     * @param server a reference to this server instance
     */
    public NodeServerConnectionHandler(NodeServer server) {
        this(server, null, new CompletableFuture<>());
    }

    /**
     * Creates a new instance of this {@link io.netty.channel.ChannelHandler} and completes the
     * given future, when the {@link NodeServerConnection} was created.
     *
     * @param server               a reference to this node server instance
     * @param uri                  the {@link URI} of the newly created {@link NodeServerConnection},
     *                             null to let this class guess the correct IP
     * @param sessionReadyListener the future, that should be completed a clientConnection creation
     */
    public NodeServerConnectionHandler(NodeServer server,
                                       URI uri,
                                       CompletableFuture<NodeServerConnection> sessionReadyListener) {
        this(server, sessionReadyListener, null, uri);
    }

    NodeServerConnectionHandler(NodeServer server,
                                CompletableFuture<NodeServerConnection> sessionReadyFuture,
                                NodeServerConnection clientConnection,
                                URI uri) {
        this.server = server;
        this.sessionReadyFuture = sessionReadyFuture;
        this.clientConnection = clientConnection;
        this.uri = uri;
    }

    /*
     * Adds a listener to the channel close event, to remove the clientConnection from various lists.
     */
    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        ctx.channel().closeFuture().addListener(future -> {
            if (clientConnection != null && !clientConnection.isClosed().isDone()) {
                server.getMessenger().getConnectionsManager().closeConnection(clientConnection, REASON_INTERNAL_REJECTION);
            }
        });
    }

    /*
     * Reads an incoming message and pass it to the correct sub-function.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message<?> msg) throws Exception {
        ctx.executor().submit(() -> {
            createSession(ctx, msg);
            if (clientConnection != null) {
                if (msg instanceof ResponseMessage) {
                    clientConnection.setResponse((ResponseMessage<? extends RequestMessage<?>, ? extends Message<?>>) msg);
                }

                MessageAction<?> action = msg.getAction();
                if (action != null) {
                    if (action instanceof ServerMessageAction) {
                        ((ServerMessageAction<?>) action).onMessageServer(clientConnection, server);
                    }
                    else {
                        LOG.debug("Could not process the message {}", msg);
                    }
                }
            }
        }).addListener(future -> {
            if (!future.isSuccess()) {
                LOG.debug("Could not process the message {}: ", msg, future.cause());
            }
            ReferenceCountUtil.release(msg);
        });
    }

    /**
     * Creates a clientConnection, if not already there.
     *
     * @param ctx channel handler context
     * @param msg probably a {@link JoinMessage}
     */
    private void createSession(final ChannelHandlerContext ctx, Message<?> msg) {
        if (msg instanceof JoinMessage && clientConnection == null) {
            JoinMessage jm = (JoinMessage) msg;
            Identity identity = Identity.of(jm.getPublicKey());
            Channel myChannel = ctx.channel();

            if (uri == null) {
                try {
                    uri = getRemoteAddr(myChannel);
                }
                catch (URISyntaxException e) {
                    LOG.error("[{}]: Cannot determine URI: {}", ctx.channel().id().asShortText(), e.getMessage());
                }
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("[{}]: Create new Connection from Channel {}", ctx.channel().id().asShortText(), ctx.channel().id());
            }

            clientConnection = new NodeServerConnection(ctx.channel(), uri, identity,
                    Optional.ofNullable(jm.getUserAgent()).orElse("U/A"), server.getMessenger().getConnectionsManager());
            sessionReadyFuture.complete(clientConnection);
            ctx.pipeline().remove(ConnectionGuard.CONNECTION_GUARD);
        }
    }

    /**
     * Returns the {@link URI} of a {@link Channel}.
     *
     * @param channel the channel
     */
    public static URI getRemoteAddr(Channel channel) throws URISyntaxException {
        InetSocketAddress socketAddress = (InetSocketAddress) channel.remoteAddress();
        return new URI("ws://" + socketAddress.getAddress().getHostAddress() + ":" + socketAddress.getPort() + "/"); // NOSONAR
    }
}
