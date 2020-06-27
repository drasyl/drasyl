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
package org.drasyl.peer.connection.superpeer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.event.Node;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.messenger.MessageSink;
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.NoPathToIdentityException;
import org.drasyl.peer.Path;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.connection.handler.AbstractThreeWayHandshakeClientHandler;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_WRONG_PUBLIC_KEY;
import static org.drasyl.peer.connection.server.NodeServerChannelGroup.ATTRIBUTE_PUBLIC_KEY;

/**
 * This handler performs the handshake with the server and processes incoming messages during the
 * session.
 * <p>
 * The handshake is initiated by a {@link JoinMessage} sent by the client, which is answered with a
 * {@link WelcomeMessage} from the server. The client must then confirm this message with a {@link
 * StatusMessage}.
 */
@SuppressWarnings({ "java:S110" })
public class SuperPeerClientConnectionHandler extends AbstractThreeWayHandshakeClientHandler<JoinMessage, WelcomeMessage> {
    public static final String SUPER_PEER_CLIENT_CONNECTION_HANDLER = "superPeerClientConnectionHandler";
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClientConnectionHandler.class);
    private final SuperPeerClientEnvironment environment;
    private ChannelHandlerContext ctx;

    public SuperPeerClientConnectionHandler(SuperPeerClientEnvironment environment) {
        super(
                environment.getConfig().getSuperPeerHandshakeTimeout(),
                environment.getMessenger(),
                new JoinMessage(environment.getIdentity().getProofOfWork(),
                        environment.getIdentity().getPublicKey(),
                        environment.getPeersManager().getChildrenAndGrandchildren().keySet()
                )
        );
        this.environment = environment;
    }

    @SuppressWarnings({ "java:S107" })
    SuperPeerClientConnectionHandler(SuperPeerClientEnvironment environment,
                                     Duration timeout,
                                     Messenger messenger,
                                     CompletableFuture<Void> handshakeFuture,
                                     ScheduledFuture<?> timeoutFuture,
                                     JoinMessage requestMessage) {
        super(timeout, messenger, handshakeFuture, timeoutFuture, requestMessage);
        this.environment = environment;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        this.ctx = ctx;

        ctx.channel().closeFuture().addListener(future -> messenger.unsetRelaySink());
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    @Override
    protected ConnectionExceptionMessage.Error validateSessionOffer(WelcomeMessage offerMessage) {
        // Raise error if the public key is equals to my public key
        if (environment.getIdentity().getPublicKey().equals(ctx.channel().attr(ATTRIBUTE_PUBLIC_KEY).get())) {
            return CONNECTION_ERROR_WRONG_PUBLIC_KEY;
        }
        else {
            return null;
        }
    }

    @Override
    protected void createConnection(ChannelHandlerContext ctx,
                                    WelcomeMessage offerMessage) {
        CompressedPublicKey identity = ctx.channel().attr(ATTRIBUTE_PUBLIC_KEY).get();
        Channel channel = ctx.channel();
        Path path = ctx::writeAndFlush; // We start at this point to save resources
        PeerInformation peerInformation = PeerInformation.of(offerMessage.getPeerInformation().getEndpoints(), path);

        // remove peer information on disconnect
        channel.closeFuture().addListener(future -> {
            environment.getConnected().onNext(false);
            environment.getEventConsumer().accept(new NodeOfflineEvent(Node.of(environment.getIdentity())));

            environment.getPeersManager().unsetSuperPeerAndRemovePeerInformation(peerInformation);
        });

        // store peer information
        environment.getPeersManager().addPeerInformationAndSetSuperPeer(identity, peerInformation);

        MessageSink messageSink = (recipient, message) -> {
            if (channel.isWritable()) {
                ctx.writeAndFlush(message);
            }
            else {
                throw new NoPathToIdentityException(recipient);
            }
        };
        messenger.setSuperPeerSink(messageSink);

        environment.getConnected().onNext(true);
        environment.getEventConsumer().accept(new NodeOnlineEvent(Node.of(environment.getIdentity())));
    }
}
