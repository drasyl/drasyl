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
import org.drasyl.DrasylException;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.connection.message.*;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_NOT_FOUND;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;

/**
 * This handler mange in-/oncoming messages and pass them to the correct sub-function. It also
 * creates a new {@link NodeServerConnection} object if a {@link JoinMessage} has pass the
 * {@link NodeServerJoinGuard} guard.
 */
public class NodeServerConnectionHandler extends SimpleChannelInboundHandler<Message> {
    public static final String HANDLER = "nodeServerConnectionHandler";
    private static final Logger LOG = LoggerFactory.getLogger(NodeServerConnectionHandler.class);
    private final NodeServer server;
    private final CompletableFuture<NodeServerConnection> sessionReadyFuture;
    private NodeServerConnection connection;
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
                                NodeServerConnection connection,
                                URI uri) {
        this.server = server;
        this.sessionReadyFuture = sessionReadyFuture;
        this.connection = connection;
        this.uri = uri;
    }

    /*
     * Adds a listener to the channel close event, to remove the clientConnection from various lists.
     */
    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        ctx.channel().closeFuture().addListener(future -> {
            if (connection != null && !connection.isClosed().isDone()) {
                server.getMessenger().getConnectionsManager().removeClosingConnection(connection);
            }
        });
    }

    /*
     * Reads an incoming message and pass it to the correct sub-function.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        ctx.executor().submit(() -> {
            createSession(ctx, msg);
            if (connection != null) {
                if (msg instanceof ResponseMessage) {
                    connection.setResponse((ResponseMessage<? extends RequestMessage>) msg);
                }

                if (msg instanceof ApplicationMessage) {
                    ApplicationMessage applicationMessage = (ApplicationMessage) msg;
                    try {
                        server.getMessenger().send(applicationMessage);
                        connection.send(new StatusMessage(STATUS_OK, applicationMessage.getId()));
                    }
                    catch (DrasylException e) {
                        connection.send(new StatusMessage(STATUS_NOT_FOUND, applicationMessage.getId()));
                    }
                }
                else {
                    LOG.debug("Could not process the message {}", msg);
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
    private void createSession(final ChannelHandlerContext ctx, Message msg) {
        if (msg instanceof JoinMessage && connection == null) {
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

            // create peer connection
            connection = new NodeServerConnection(ctx.channel(), uri, identity,
                    Optional.ofNullable(jm.getUserAgent()).orElse("U/A"), server.getMessenger().getConnectionsManager());
            sessionReadyFuture.complete(connection);

            // store peer information
            PeerInformation peerInformation = new PeerInformation();
            peerInformation.setPublicKey(jm.getPublicKey());
            peerInformation.addEndpoint(jm.getEndpoints());
            server.getPeersManager().addPeer(identity, peerInformation);
            server.getPeersManager().addChildren(identity);

            // send confirmation
            ctx.writeAndFlush(new WelcomeMessage(server.getIdentityManager().getKeyPair().getPublicKey(), server.getEntryPoints(), jm.getId()));

            ctx.pipeline().remove(NodeServerNewConnectionsGuard.CONNECTION_GUARD);
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
