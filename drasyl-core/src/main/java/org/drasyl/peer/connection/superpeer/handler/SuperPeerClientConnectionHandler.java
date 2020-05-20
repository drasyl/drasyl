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
package org.drasyl.peer.connection.superpeer.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.drasyl.peer.connection.message.action.ClientMessageAction;
import org.drasyl.peer.connection.message.action.MessageAction;
import org.drasyl.peer.connection.superpeer.SuperPeerClient;
import org.drasyl.peer.connection.superpeer.SuperPeerClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static org.drasyl.peer.connection.PeerConnection.CloseReason.REASON_INTERNAL_REJECTION;

/**
 * This handler mange in-/oncoming messages and pass them to the correct sub-function. It also
 * creates a new {@link SuperPeerClientConnection} object if a {@link WelcomeMessage} has pass the {@link
 * SuperPeerClientWelcomeGuard} guard.
 */
public class SuperPeerClientConnectionHandler extends SimpleChannelInboundHandler<Message<?>> {
    public static final String SUPER_PEER_HANDLER = "superPeerClientConnectionHandler";
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClientConnectionHandler.class);
    private final SuperPeerClient superPeerClient;
    private final URI endpoint;
    private SuperPeerClientConnection connection;

    public SuperPeerClientConnectionHandler(SuperPeerClient superPeerClient, URI endpoint) {
        this.superPeerClient = superPeerClient;
        this.endpoint = endpoint;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        ctx.channel().closeFuture().addListener(future -> {
            if (connection != null && !connection.isClosed().isDone()) {
                superPeerClient.getMessenger().getConnectionsManager().closeConnection(connection, REASON_INTERNAL_REJECTION);
            }
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                Message<?> msg) throws Exception {
        ctx.executor().submit(() -> {
            createConnection(ctx, msg);
            if (connection != null) {
                if (msg instanceof ResponseMessage) {
                    connection.setResponse((ResponseMessage<? extends RequestMessage<?>, ? extends Message<?>>) msg);
                }

                MessageAction<?> action = msg.getAction();
                if (action != null) {
                    if (action instanceof ClientMessageAction) {
                        ((ClientMessageAction<?>) action).onMessageClient(connection, superPeerClient);
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
     * Creates a new {@link SuperPeerClientConnection}, if not already there.
     */
    private void createConnection(final ChannelHandlerContext ctx, Message<?> msg) {
        if (msg instanceof WelcomeMessage && connection == null) {
            WelcomeMessage welcomeMessage = (WelcomeMessage) msg;
            Identity identity = Identity.of(welcomeMessage.getPublicKey());

            if (LOG.isDebugEnabled()) {
                LOG.debug("[{}]: Create new Connection from Channel {}", ctx.channel().id().asShortText(), ctx.channel().id());
            }

            connection = new SuperPeerClientConnection(ctx.channel(), endpoint, identity, welcomeMessage.getUserAgent(), superPeerClient.getMessenger().getConnectionsManager());
        }
    }
}
