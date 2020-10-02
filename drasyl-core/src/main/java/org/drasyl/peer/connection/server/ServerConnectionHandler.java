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
package org.drasyl.peer.connection.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.IdentityManager;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.Path;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.connection.handler.AbstractThreeWayHandshakeServerHandler;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.drasyl.util.FutureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_IDENTITY_COLLISION;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_NOT_A_SUPER_PEER;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_OTHER_NETWORK;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_PROOF_OF_WORK_INVALID;

/**
 * Acts as a guard for in- and outbound connections. A channel is only created, when a {@link
 * JoinMessage} was received. Outgoing messages are dropped unless a {@link JoinMessage} was
 * received. Every other incoming message is also dropped unless a {@link JoinMessage} was
 * received.
 * <p>
 * If a {@link JoinMessage} was not received in {@link DrasylConfig#getServerHandshakeTimeout()} the
 * connection will be closed.
 * <p>
 * This handler closes the channel if an exception occurs before a {@link JoinMessage} has been
 * received.
 */
@SuppressWarnings({ "java:S107", "java:S110" })
public class ServerConnectionHandler extends AbstractThreeWayHandshakeServerHandler<JoinMessage, WelcomeMessage> {
    public static final String SERVER_CONNECTION_HANDLER = "serverConnectionHandler";
    private static final Logger LOG = LoggerFactory.getLogger(ServerConnectionHandler.class);
    private final ServerEnvironment environment;

    public ServerConnectionHandler(final ServerEnvironment environment) {
        super(environment.getConfig().getServerHandshakeTimeout(), environment.getMessenger());
        this.environment = environment;
    }

    ServerConnectionHandler(final ServerEnvironment environment,
                            final Duration timeout,
                            final Messenger messenger,
                            final CompletableFuture<Void> handshakeFuture,
                            final ScheduledFuture<?> timeoutFuture,
                            final JoinMessage requestMessage,
                            final WelcomeMessage offerMessage) {
        super(timeout, messenger, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
        this.environment = environment;
    }

    @Override
    protected ConnectionExceptionMessage.Error validateSessionRequest(final JoinMessage requestMessage) {
        final CompressedPublicKey clientPublicKey = requestMessage.getPublicKey();

        if (!requestMessage.getProofOfWork().isValid(requestMessage.getPublicKey(),
                IdentityManager.POW_DIFFICULTY)) {
            return CONNECTION_ERROR_PROOF_OF_WORK_INVALID;
        }
        else if (requestMessage.isChildrenJoin() && environment.getConfig().isSuperPeerEnabled()) {
            return CONNECTION_ERROR_NOT_A_SUPER_PEER;
        }
        else if (environment.getIdentity().getPublicKey().equals(clientPublicKey)) {
            return CONNECTION_ERROR_IDENTITY_COLLISION;
        }
        else if (environment.getConfig().getNetworkId() != requestMessage.getNetworkId()) {
            return CONNECTION_ERROR_OTHER_NETWORK;
        }
        else {
            return null;
        }
    }

    @Override
    protected WelcomeMessage offerSession(final ChannelHandlerContext ctx,
                                          final JoinMessage requestMessage) {
        return new WelcomeMessage(PeerInformation.of(environment.getEndpoints()), requestMessage.getId());
    }

    @Override
    protected void createConnection(final ChannelHandlerContext ctx,
                                    final JoinMessage requestMessage) {
        final CompressedPublicKey clientPublicKey = requestMessage.getPublicKey();
        final Channel channel = ctx.channel();
        final PeerInformation clientInformation = PeerInformation.of();
        final Path path = msg -> FutureUtil.toFuture(ctx.writeAndFlush(msg));

        environment.getChannelGroup().add(clientPublicKey, channel);

        if (requestMessage.isChildrenJoin()) {
            // remove peer information on disconnect
            channel.closeFuture().addListener(future -> environment.getPeersManager().removeChildrenAndPath(clientPublicKey, path));

            // store peer information
            environment.getPeersManager().setPeerInformationAndAddPathAndChildren(clientPublicKey, clientInformation, path);
        }
        else {
            // remove peer information on disconnect
            channel.closeFuture().addListener(future -> environment.getPeersManager().removePath(clientPublicKey, path));

            // store peer information
            environment.getPeersManager().setPeerInformationAndAddPath(clientPublicKey, clientInformation, path);
        }
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}