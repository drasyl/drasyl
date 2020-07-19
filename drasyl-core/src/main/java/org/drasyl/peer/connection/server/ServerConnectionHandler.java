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
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RegisterGrandchildMessage;
import org.drasyl.peer.connection.message.UnregisterGrandchildMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.drasyl.util.SetUtil;
import org.drasyl.util.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_IDENTITY_COLLISION;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_PROOF_OF_WORK_INVALID;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_SUPER_PEER_DISCONNECTED;
import static org.drasyl.peer.connection.PeerChannelGroup.ATTRIBUTE_PUBLIC_KEY;

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

    public ServerConnectionHandler(ServerEnvironment environment) {
        super(environment.getConfig().getServerHandshakeTimeout(), environment.getMessenger());
        this.environment = environment;
    }

    ServerConnectionHandler(ServerEnvironment environment,
                            Duration timeout,
                            Messenger messenger,
                            CompletableFuture<Void> handshakeFuture,
                            ScheduledFuture<?> timeoutFuture,
                            JoinMessage requestMessage,
                            WelcomeMessage offerMessage) {
        super(timeout, messenger, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
        this.environment = environment;
    }

    @Override
    protected ConnectionExceptionMessage.Error validateSessionRequest(JoinMessage requestMessage) {
        CompressedPublicKey clientPublicKey = requestMessage.getPublicKey();

        if (requestMessage.isChildrenJoin() && !environment.getSuperPeerConnectedSupplier().getAsBoolean()) {
            return CONNECTION_ERROR_SUPER_PEER_DISCONNECTED;
        }
        else if (environment.getIdentity().getPublicKey().equals(clientPublicKey)) {
            return CONNECTION_ERROR_IDENTITY_COLLISION;
        }
        else if (!requestMessage.getProofOfWork().isValid(requestMessage.getPublicKey(),
                IdentityManager.POW_DIFFICULTY)) {
            return CONNECTION_ERROR_PROOF_OF_WORK_INVALID;
        }
        else {
            return null;
        }
    }

    @Override
    protected WelcomeMessage offerSession(ChannelHandlerContext ctx,
                                          JoinMessage requestMessage) {
        return new WelcomeMessage(PeerInformation.of(environment.getEndpoints()), requestMessage.getId());
    }

    @Override
    protected void createConnection(ChannelHandlerContext ctx,
                                    JoinMessage requestMessage) {
        CompressedPublicKey clientPublicKey = requestMessage.getPublicKey();
        Channel channel = ctx.channel();
        PeerInformation clientInformation = PeerInformation.of();
        Path path = ctx::writeAndFlush;

        environment.getChannelGroup().add(clientPublicKey, channel);

        if (requestMessage.isChildrenJoin()) {
            // remove peer information on disconnect
            channel.closeFuture().addListener(future -> environment.getPeersManager().removeChildrenAndPath(clientPublicKey, path));

            // store peer information
            environment.getPeersManager().setPeerInformationAndAddPathAndChildren(clientPublicKey, clientInformation, path);

            // inform super peer about my new children and grandchildren
            Set<CompressedPublicKey> childrenAndGrandchildren = SetUtil.merge(requestMessage.getChildrenAndGrandchildren(), clientPublicKey);
            registerGrandchildrenAtSuperPeer(ctx, childrenAndGrandchildren);

            // store peer's children (my grandchildren) information
            registerGrandchildrenLocally(ctx, requestMessage.getChildrenAndGrandchildren());
        }
        else {
            // remove peer information on disconnect
            channel.closeFuture().addListener(future -> environment.getPeersManager().removePath(clientPublicKey, path));

            // store peer information
            environment.getPeersManager().setPeerInformationAndAddPath(clientPublicKey, clientInformation, path);
        }
    }

    private void registerGrandchildrenAtSuperPeer(ChannelHandlerContext ctx,
                                                  Set<CompressedPublicKey> grandchildren) {
        Triple<CompressedPublicKey, PeerInformation, Set<Path>> superPeer = environment.getPeersManager().getSuperPeer();
        if (superPeer != null) {
            Set<Path> paths = superPeer.third();
            Path superPeerPath = paths.iterator().next();
            if (superPeerPath != null) {
                Channel channel = ctx.channel();
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug("[{}]: Register Grandchildren {} at Super Peer", channel.id().asShortText(), grandchildren);
                }
                channel.closeFuture().addListener(future -> unregisterGrandchildrenAtSuperPeer(ctx, grandchildren));
                superPeerPath.send(new RegisterGrandchildMessage(grandchildren));
            }
        }
    }

    private void registerGrandchildrenLocally(ChannelHandlerContext ctx,
                                              Set<CompressedPublicKey> grandchildren) {
        Channel channel = ctx.channel();
        if (!grandchildren.isEmpty() && getLogger().isDebugEnabled()) {
            getLogger().debug("[{}]: Client want to register Grandchildren {}", channel.id().asShortText(), grandchildren);
        }

        for (CompressedPublicKey grandchildIdentity : grandchildren) {

            // remove peer information on disconnect
            channel.closeFuture().addListener(future -> environment.getPeersManager().removeGrandchildrenRoute(grandchildIdentity));

            // store peer information
            CompressedPublicKey clientPublicKey = channel.attr(ATTRIBUTE_PUBLIC_KEY).get();
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("[{}]: Client {} can Route to {}", channel.id().asShortText(), clientPublicKey, grandchildIdentity);
            }
            environment.getPeersManager().addGrandchildrenRoute(grandchildIdentity, clientPublicKey);
        }
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    @Override
    protected void processMessageAfterHandshake(ChannelHandlerContext ctx,
                                                Message message) {
        if (message instanceof RegisterGrandchildMessage) {
            RegisterGrandchildMessage registerGrandchildMessage = (RegisterGrandchildMessage) message;
            registerGrandchildrenAtSuperPeer(ctx, registerGrandchildMessage.getGrandchildren());
            registerGrandchildrenLocally(ctx, registerGrandchildMessage.getGrandchildren());
        }
        else if (message instanceof UnregisterGrandchildMessage) {
            UnregisterGrandchildMessage unregisterGrandchildMessage = (UnregisterGrandchildMessage) message;
            unregisterGrandchildLocally(ctx, unregisterGrandchildMessage.getGrandchildren());
        }
        else {
            super.processMessageAfterHandshake(ctx, message);
        }
    }

    private void unregisterGrandchildrenAtSuperPeer(ChannelHandlerContext ctx,
                                                    Set<CompressedPublicKey> grandchildren) {
        Triple<CompressedPublicKey, PeerInformation, Set<Path>> superPeer = environment.getPeersManager().getSuperPeer();
        if (superPeer != null) {
            Set<Path> paths = superPeer.third();
            Path superPeerPath = paths.iterator().next();
            if (superPeerPath != null) {
                Channel channel = ctx.channel();
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug("[{}]: Unregister Grandchildren {} at Super Peer", channel.id().asShortText(), grandchildren);
                }
                superPeerPath.send(new UnregisterGrandchildMessage(grandchildren));
            }
        }
    }

    private void unregisterGrandchildLocally(ChannelHandlerContext ctx,
                                             Set<CompressedPublicKey> grandchildren) {
        for (CompressedPublicKey grandchildIdentity : grandchildren) {
            Channel channel = ctx.channel();

            if (getLogger().isDebugEnabled()) {
                getLogger().debug("[{}]: Client want to unregister Grandchild {}", channel.id().asShortText(), grandchildIdentity);
            }

            // unregister grandchild at super peer
            unregisterGrandchildrenAtSuperPeer(ctx, Set.of(grandchildIdentity));

            // remove peer information
            environment.getPeersManager().removeGrandchildrenRoute(grandchildIdentity);
        }
    }
}
