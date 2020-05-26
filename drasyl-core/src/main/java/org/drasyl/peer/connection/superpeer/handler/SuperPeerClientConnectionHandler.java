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
import org.drasyl.DrasylException;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.connection.message.*;
import org.drasyl.peer.connection.superpeer.SuperPeerClient;
import org.drasyl.peer.connection.superpeer.SuperPeerClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_NOT_FOUND;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;

/**
 * This handler mange in-/oncoming messages and pass them to the correct sub-function. It also
 * creates a new {@link SuperPeerClientConnection} object if a {@link WelcomeMessage} has pass the
 * {@link SuperPeerClientWelcomeGuard} guard.
 */
public class SuperPeerClientConnectionHandler extends SimpleChannelInboundHandler<Message> {
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
                superPeerClient.getMessenger().getConnectionsManager().removeClosingConnection(connection);
            }
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                Message msg) throws Exception {
        ctx.executor().submit(() -> {
            createConnection(ctx, msg);
            if (connection != null) {
                if (msg instanceof ResponseMessage) {
                    connection.setResponse((ResponseMessage<? extends RequestMessage>) msg);
                }

                if (msg instanceof ApplicationMessage) {
                    ApplicationMessage applicationMessage = (ApplicationMessage) msg;
                    try {
                        superPeerClient.getMessenger().send(applicationMessage);
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
     * Creates a new {@link SuperPeerClientConnection}, if not already there.
     */
    private void createConnection(final ChannelHandlerContext ctx, Message msg) {
        if (msg instanceof WelcomeMessage && connection == null) {
            WelcomeMessage welcomeMessage = (WelcomeMessage) msg;
            Identity identity = Identity.of(welcomeMessage.getPublicKey());

            if (LOG.isDebugEnabled()) {
                LOG.debug("[{}]: Create new Connection from Channel {}", ctx.channel().id().asShortText(), ctx.channel().id());
            }

            // create peer connection
            connection = new SuperPeerClientConnection(ctx.channel(), endpoint, identity, welcomeMessage.getUserAgent(), superPeerClient.getMessenger().getConnectionsManager());

            // store peer information
            PeerInformation peerInformation = new PeerInformation();
            peerInformation.setPublicKey(welcomeMessage.getPublicKey());
            peerInformation.addEndpoint(welcomeMessage.getEndpoints());
            superPeerClient.getPeersManager().addPeer(identity, peerInformation);
            superPeerClient.getPeersManager().setSuperPeer(identity);

            // send confirmation
            ctx.writeAndFlush(new StatusMessage(STATUS_OK, msg.getId()));
        }
    }
}
