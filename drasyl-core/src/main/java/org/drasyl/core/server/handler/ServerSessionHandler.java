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
package org.drasyl.core.server.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.core.common.message.JoinMessage;
import org.drasyl.core.common.message.Message;
import org.drasyl.core.common.message.action.MessageAction;
import org.drasyl.core.common.message.action.ServerMessageAction;
import org.drasyl.core.node.PeerInformation;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.node.connections.PeerConnection;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.server.NodeServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.core.common.handler.ConnectionGuardHandler.CONNECTION_GUARD;
import static org.drasyl.core.server.handler.KillOnExceptionHandler.KILL_SWITCH;

/**
 * This handler mange in-/oncoming messages and pass them to the correct sub-function. It also
 * creates a new {@link ClientConnection} object if a {@link JoinMessage} has pass the {@link JoinHandler}
 * guard.
 */
public class ServerSessionHandler extends SimpleChannelInboundHandler<Message> {
    private static final Logger LOG = LoggerFactory.getLogger(ServerSessionHandler.class);
    public static final String HANDLER = "handler";
    private final NodeServer server;
    private final CompletableFuture<ClientConnection> sessionReadyFuture;
    private ClientConnection clientConnection;
    private URI uri;

    /**
     * Creates a new instance of this {@link io.netty.channel.ChannelHandler}
     *
     * @param server a reference to this server instance
     */
    public ServerSessionHandler(NodeServer server) {
        this(server, null, new CompletableFuture<>());
    }

    /**
     * Creates a new instance of this {@link io.netty.channel.ChannelHandler} and completes the
     * given future, when the {@link ClientConnection} was created.
     *
     * @param server               a reference to this node server instance
     * @param uri                  the {@link URI} of the newly created {@link ClientConnection},
     *                             null to let this class guess the correct IP
     * @param sessionReadyListener the future, that should be completed a clientConnection creation
     */
    public ServerSessionHandler(NodeServer server,
                                URI uri,
                                CompletableFuture<ClientConnection> sessionReadyListener) {
        this.sessionReadyFuture = sessionReadyListener;
        this.server = server;
        this.uri = uri;
    }

    /*
     * Adds a listener to the channel close event, to remove the clientConnection from various lists.
     */
    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        ctx.channel().closeFuture().addListener(future -> {
            if (clientConnection != null) {
                server.getPeersManager().getPeer(clientConnection.getIdentity()).removePeerConnection(clientConnection);
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
            if (clientConnection != null) {
                MessageAction action = msg.getAction();
                if (action instanceof ServerMessageAction) {
                    ((ServerMessageAction) action).onMessageServer(clientConnection, server);
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
        if (msg instanceof JoinMessage && clientConnection == null) {
            JoinMessage jm = (JoinMessage) msg;
            Identity identity = Identity.of(jm.getPublicKey());
            Channel myChannel = ctx.channel();

            // close and remove any existing sessions that may exist
            PeerInformation peerInformation = server.getPeersManager().getPeer(identity);
            if (peerInformation != null) {
                Optional<PeerConnection> existingServerSessionOptional = peerInformation.getConnections().stream().filter(peerConnection -> peerConnection instanceof ClientConnection && peerConnection.getIdentity().equals(identity)).findFirst();

                if (existingServerSessionOptional.isPresent()) {
                    PeerConnection existingServerSession = existingServerSessionOptional.get();
                    LOG.debug("There is an existing Session for Node '{}'. Replace and close existing Session '{}' before adding new Session", identity, existingServerSession);
                    peerInformation.removePeerConnection(existingServerSession);
                    existingServerSession.close();
                }
            }

            if (uri == null) {
                try {
                    uri = getRemoteAddr(myChannel);
                }
                catch (URISyntaxException e) {
                    LOG.error("[{}]: Cannot determine URI: {}", ctx.channel().id().asShortText(), e.getMessage());
                }
            }

            clientConnection = new ClientConnection(ctx.channel(), uri, identity,
                    Optional.ofNullable(jm.getUserAgent()).orElse("U/A"));
            sessionReadyFuture.complete(clientConnection);
            ctx.pipeline().remove(KILL_SWITCH);
            ctx.pipeline().remove(CONNECTION_GUARD);
            LOG.debug("[{}]: Create new channel {}, for ClientConnection {}", ctx.channel().id().asShortText(), ctx.channel().id(), clientConnection);
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
