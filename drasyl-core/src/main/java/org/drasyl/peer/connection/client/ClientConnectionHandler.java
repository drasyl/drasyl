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
import org.drasyl.event.Node;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.Path;
import org.drasyl.peer.connection.handler.AbstractThreeWayHandshakeClientHandler;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.peer.connection.PeerChannelGroup.ATTRIBUTE_PUBLIC_KEY;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_WRONG_PUBLIC_KEY;
import static org.drasyl.util.FutureUtil.toFuture;

/**
 * This handler performs the handshake with the server and processes incoming messages during the
 * session.
 * <p>
 * The handshake is initiated by a {@link JoinMessage} sent by the client, which is answered with a
 * {@link WelcomeMessage} from the server. The client must then confirm this message with a {@link
 * StatusMessage}.
 */
@SuppressWarnings({ "java:S110" })
public class ClientConnectionHandler extends AbstractThreeWayHandshakeClientHandler<JoinMessage, WelcomeMessage> {
    public static final String CLIENT_CONNECTION_HANDLER = "clientConnectionHandler";
    private static final Logger LOG = LoggerFactory.getLogger(ClientConnectionHandler.class);
    private final ClientEnvironment environment;
    private final boolean childrenJoin;
    private ChannelHandlerContext ctx;

    public ClientConnectionHandler(final ClientEnvironment environment) {
        super(
                environment.getHandshakeTimeout(),
                environment.getMessenger(),
                new JoinMessage(environment.getIdentity().getProofOfWork(),
                        environment.getIdentity().getPublicKey(),
                        environment.joinAsChildren(),
                        environment.getConfig().getNetworkId())
        );
        this.environment = environment;
        this.childrenJoin = environment.joinAsChildren();
    }

    @SuppressWarnings({ "java:S107" })
    ClientConnectionHandler(final ClientEnvironment environment,
                            final Duration timeout,
                            final Messenger messenger,
                            final CompletableFuture<Void> handshakeFuture,
                            final ScheduledFuture<?> timeoutFuture,
                            final JoinMessage requestMessage) {
        super(timeout, messenger, handshakeFuture, timeoutFuture, requestMessage);
        this.environment = environment;
        this.childrenJoin = environment.joinAsChildren();
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        this.ctx = ctx;
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    @Override
    protected ConnectionExceptionMessage.Error validateSessionOffer(final WelcomeMessage offerMessage) {
        // Raise error if the public key is equals to my public key
        if (environment.getIdentity().getPublicKey().equals(ctx.channel().attr(ATTRIBUTE_PUBLIC_KEY).get())) {
            return CONNECTION_ERROR_WRONG_PUBLIC_KEY;
        }
        else {
            return null;
        }
    }

    @Override
    protected void createConnection(final ChannelHandlerContext ctx,
                                    final WelcomeMessage offerMessage) {
        final CompressedPublicKey identity = ctx.channel().attr(ATTRIBUTE_PUBLIC_KEY).get();
        final Channel channel = ctx.channel();
        final Path path = msg -> toFuture(ctx.writeAndFlush(msg));

        environment.getChannelGroup().add(identity, channel);

        if (childrenJoin) {
            // remove peer information on disconnect
            channel.closeFuture().addListener(future -> {
                environment.getEventConsumer().accept(new NodeOfflineEvent(Node.of(environment.getIdentity())));

                environment.getPeersManager().unsetSuperPeerAndRemovePath(path);
            });

            // store peer information
            environment.getPeersManager().setPeerInformationAndAddPathAndSetSuperPeer(identity, offerMessage.getPeerInformation(), path);

            environment.getEventConsumer().accept(new NodeOnlineEvent(Node.of(environment.getIdentity())));
        }
        else {
            // remove peer information on disconnect
            channel.closeFuture().addListener(future -> environment.getPeersManager().removePath(identity, path));

            // store peer information
            environment.getPeersManager().setPeerInformationAndAddPath(identity, offerMessage.getPeerInformation(), path);
        }
    }
}