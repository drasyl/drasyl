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
package org.drasyl.peer.connection.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.Path;
import org.drasyl.peer.connection.handler.ThreeWayHandshakeClientHandler;
import org.drasyl.peer.connection.message.ErrorMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.SuccessMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.drasyl.pipeline.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.identity.IdentityManager.POW_DIFFICULTY;
import static org.drasyl.peer.connection.message.ErrorMessage.Error.ERROR_IDENTITY_COLLISION;
import static org.drasyl.peer.connection.message.ErrorMessage.Error.ERROR_OTHER_NETWORK;
import static org.drasyl.peer.connection.message.ErrorMessage.Error.ERROR_PROOF_OF_WORK_INVALID;
import static org.drasyl.peer.connection.message.ErrorMessage.Error.ERROR_WRONG_PUBLIC_KEY;
import static org.drasyl.util.FutureUtil.toFuture;

/**
 * This handler performs the handshake with the server and processes incoming messages during the
 * session.
 * <p>
 * The handshake is initiated by a {@link JoinMessage} sent by the client, which is answered with a
 * {@link WelcomeMessage} from the server. The client must then confirm this message with a {@link
 * SuccessMessage}.
 */
@SuppressWarnings({ "java:S110" })
public class ClientConnectionHandler extends ThreeWayHandshakeClientHandler<JoinMessage, WelcomeMessage> {
    public static final String CLIENT_CONNECTION_HANDLER = "clientConnectionHandler";
    private static final Logger LOG = LoggerFactory.getLogger(ClientConnectionHandler.class);
    private final ClientEnvironment environment;
    private final boolean childrenJoin;

    public ClientConnectionHandler(final ClientEnvironment environment) {
        super(
                environment.getConfig().getNetworkId(),
                environment.getIdentity(),
                environment.getHandshakeTimeout(),
                environment.getPipeline(),
                new JoinMessage(environment.getConfig().getNetworkId(), environment.getIdentity().getPublicKey(), environment.getIdentity().getProofOfWork(),
                        environment.getEndpoint().getPublicKey(), environment.joinAsChildren() ? System.currentTimeMillis() : 0
                )
        );
        this.environment = environment;
        this.childrenJoin = environment.joinAsChildren();
    }

    @SuppressWarnings({ "java:S107" })
    ClientConnectionHandler(final ClientEnvironment environment,
                            final Duration timeout,
                            final Pipeline pipeline,
                            final CompletableFuture<Void> handshakeFuture,
                            final ScheduledFuture<?> timeoutFuture,
                            final JoinMessage requestMessage) {
        super(environment.getConfig().getNetworkId(), environment.getIdentity(), timeout, pipeline, handshakeFuture, timeoutFuture, requestMessage);
        this.environment = environment;
        this.childrenJoin = environment.joinAsChildren();
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    @Override
    protected ErrorMessage.Error validateSessionOffer(final WelcomeMessage offerMessage) {
        final CompressedPublicKey serverPublicKey = offerMessage.getSender();

        if (!environment.getEndpoint().getPublicKey().equals(serverPublicKey)) {
            return ERROR_WRONG_PUBLIC_KEY;
        }
        else if (!offerMessage.getProofOfWork().isValid(offerMessage.getSender(), POW_DIFFICULTY)) {
            return ERROR_PROOF_OF_WORK_INVALID;
        }
        else if (environment.getIdentity().getPublicKey().equals(serverPublicKey)) {
            return ERROR_IDENTITY_COLLISION;
        }
        else if (environment.getConfig().getNetworkId() != offerMessage.getNetworkId()) {
            return ERROR_OTHER_NETWORK;
        }
        else {
            return null;
        }
    }

    @Override
    protected void createConnection(final ChannelHandlerContext ctx,
                                    final WelcomeMessage offerMessage) {
        final CompressedPublicKey serverPublicKey = offerMessage.getSender();
        final Channel channel = ctx.channel();
        final Path path = msg -> toFuture(ctx.writeAndFlush(msg));

        ctx.channel().attr(ATTRIBUTE_PUBLIC_KEY).set(serverPublicKey);

        environment.getChannelGroup().add(serverPublicKey, channel);

        if (childrenJoin) {
            // remove peer information on disconnect
            channel.closeFuture().addListener(future -> environment.getPeersManager().unsetSuperPeerAndRemovePath(path));

            // store peer information
            environment.getPeersManager().setPeerInformationAndAddPathAndSetSuperPeer(serverPublicKey, offerMessage.getPeerInformation(), path);
        }
        else {
            // remove peer information on disconnect
            channel.closeFuture().addListener(future -> environment.getPeersManager().removePath(serverPublicKey, path));

            // store peer information
            environment.getPeersManager().setPeerInformationAndAddPath(serverPublicKey, offerMessage.getPeerInformation(), path);
        }
    }
}