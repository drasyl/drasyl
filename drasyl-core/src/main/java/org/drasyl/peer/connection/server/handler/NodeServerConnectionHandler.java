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
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.identity.Address;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.Path;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.handler.AbstractThreeWayHandshakeServerHandler;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.IdentityMessage;
import org.drasyl.peer.connection.message.RegisterGrandchildMessage;
import org.drasyl.peer.connection.message.UnregisterGrandchildMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.drasyl.peer.connection.message.WhoisMessage;
import org.drasyl.peer.connection.server.NodeServerChannelGroup;
import org.drasyl.util.KeyValue;
import org.drasyl.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_SAME_PUBLIC_KEY;
import static org.drasyl.peer.connection.server.NodeServerChannelGroup.ATTRIBUTE_IDENTITY;

/**
 * Acts as a guard for in- and outbound connections. A channel is only created, when a {@link
 * JoinMessage} was received. Outgoing messages are dropped unless a {@link JoinMessage} was
 * received. Every other incoming message is also dropped unless a {@link JoinMessage} was
 * received.
 * <p>
 * If a {@link JoinMessage} was not received in {@link org.drasyl.DrasylNodeConfig#getServerHandshakeTimeout()}
 * the connection will be closed.
 * <p>
 * This handler closes the channel if an exception occurs before a {@link JoinMessage} has been
 * received.
 */
@SuppressWarnings({ "java:S107", "java:S110" })
public class NodeServerConnectionHandler extends AbstractThreeWayHandshakeServerHandler<JoinMessage, WelcomeMessage> {
    public static final String NODE_SERVER_CONNECTION_HANDLER = "nodeServerConnectionHandler";
    private static final Logger LOG = LoggerFactory.getLogger(NodeServerConnectionHandler.class);
    private final PeersManager peersManager;
    private final Set<URI> endpoints;
    private final Identity identity;
    private final NodeServerChannelGroup channelGroup;

    public NodeServerConnectionHandler(Identity identity,
                                       PeersManager peersManager,
                                       Set<URI> endpoints,
                                       Duration timeout,
                                       Messenger messenger,
                                       NodeServerChannelGroup channelGroup) {
        super(timeout, messenger);
        this.peersManager = peersManager;
        this.endpoints = endpoints;
        this.identity = identity;
        this.channelGroup = channelGroup;
    }

    NodeServerConnectionHandler(Identity identity,
                                PeersManager peersManager,
                                Set<URI> endpoints,
                                Duration timeout,
                                Messenger messenger,
                                CompletableFuture<Void> handshakeFuture,
                                ScheduledFuture<?> timeoutFuture,
                                JoinMessage requestMessage,
                                NodeServerChannelGroup channelGroup,
                                WelcomeMessage offerMessage) {
        super(timeout, messenger, handshakeFuture, timeoutFuture, requestMessage, offerMessage);
        this.peersManager = peersManager;
        this.endpoints = endpoints;
        this.identity = identity;
        this.channelGroup = channelGroup;
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
            Identity grandchildIdentity = registerGrandchildMessage.getIdentity();
            PeerInformation grandchildInformation = registerGrandchildMessage.getPeerInformation();
            registerGrandchild(ctx, grandchildIdentity, grandchildInformation);
        }
        else if (message instanceof UnregisterGrandchildMessage) {
            UnregisterGrandchildMessage unregisterGrandchildMessage = (UnregisterGrandchildMessage) message;
            Identity grandchildIdentity = unregisterGrandchildMessage.getIdentity();
            PeerInformation grandchildInformation = unregisterGrandchildMessage.getPeerInformation();
            unregisterGrandchild(ctx, grandchildIdentity, grandchildInformation);
        }
        else if (message instanceof WhoisMessage) {
            WhoisMessage whoisMessage = (WhoisMessage) message;
            handleWhoisMessage(ctx, whoisMessage);
        }
        else {
            super.processMessageAfterHandshake(ctx, message);
        }
    }

    @Override
    protected ConnectionExceptionMessage.Error validateSessionRequest(JoinMessage requestMessage) {
        CompressedPublicKey clientPublicKey = requestMessage.getIdentity().getPublicKey();

        if (identity.getPublicKey().equals(clientPublicKey)) {
            return CONNECTION_ERROR_SAME_PUBLIC_KEY;
        }
        else {
            return null;
        }
    }

    @Override
    protected WelcomeMessage offerSession(ChannelHandlerContext ctx,
                                          JoinMessage requestMessage) {
        return new WelcomeMessage(identity, PeerInformation.of(endpoints), requestMessage.getId());
    }

    @Override
    protected void createConnection(ChannelHandlerContext ctx,
                                    JoinMessage requestMessage) {
        Identity clientIdentity = requestMessage.getIdentity();
        Channel channel = ctx.channel();
        Path path = ctx::writeAndFlush; // We start at this point to save resources
        PeerInformation clientInformation = PeerInformation.of(requestMessage.getPeerInformation().getEndpoints(), path);

        channelGroup.add(clientIdentity, channel);

        // remove peer information on disconnect
        channel.closeFuture().addListener(future -> peersManager.removeChildrenAndRemovePeerInformation(clientIdentity, clientInformation));

        // store peer information
        peersManager.addPeerInformationAndAddChildren(clientIdentity, clientInformation);

        // inform super peer about my new children
        registerGrandchildAtSuperPeer(ctx, clientIdentity, requestMessage.getPeerInformation());

        // store peer's children (my grandchildren) information
        for (KeyValue<Identity, PeerInformation> grandchild : requestMessage.getChildrenAndGrandchildren()) {
            Identity grandchildIdentity = grandchild.key();
            PeerInformation grandchildInformation = grandchild.value();
            registerGrandchild(ctx, grandchildIdentity, grandchildInformation);
        }
    }

    private void registerGrandchildAtSuperPeer(ChannelHandlerContext ctx,
                                               Identity grandchildIdentity,
                                               PeerInformation grandchildInformation) {
        Pair<Identity, PeerInformation> superPeer = peersManager.getSuperPeer();
        if (superPeer != null) {
            PeerInformation superPeerInformation = superPeer.second();
            Path superPeerPath = superPeerInformation.getPaths().iterator().next();
            if (superPeerPath != null) {
                Channel channel = ctx.channel();
                channel.closeFuture().addListener(future -> unregisterGrandchildAtSuperPeer(ctx, grandchildIdentity, grandchildInformation));
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug("[{}]: Register Grandchild {} at Super Peer", channel.id().asShortText(), grandchildIdentity);
                }
                superPeerPath.send(new RegisterGrandchildMessage(grandchildIdentity, grandchildInformation));
            }
        }
    }

    private void unregisterGrandchildAtSuperPeer(ChannelHandlerContext ctx,
                                                 Identity grandchildIdentity,
                                                 PeerInformation grandchildInformation) {
        Pair<Identity, PeerInformation> superPeer = peersManager.getSuperPeer();
        if (superPeer != null) {
            PeerInformation superPeerInformation = superPeer.second();
            Path superPeerPath = superPeerInformation.getPaths().iterator().next();
            if (superPeerPath != null) {
                Channel channel = ctx.channel();
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug("[{}]: Unregister Grandchild {} at Super Peer", channel.id().asShortText(), grandchildIdentity);
                }
                superPeerPath.send(new UnregisterGrandchildMessage(grandchildIdentity, grandchildInformation));
            }
        }
    }

    private void registerGrandchild(ChannelHandlerContext ctx,
                                    Identity grandchildIdentity,
                                    PeerInformation grandchildInformation) {
        Channel channel = ctx.channel();

        if (getLogger().isDebugEnabled()) {
            getLogger().debug("[{}]: Client want to register Grandchild {}", channel.id().asShortText(), grandchildIdentity);
        }

        // register grandchild at super peer
        registerGrandchildAtSuperPeer(ctx, grandchildIdentity, grandchildInformation);

        // remove peer information on disconnect
        channel.closeFuture().addListener(future -> peersManager.removeGrandchildrenRouteAndRemovePeerInformation(grandchildIdentity, grandchildInformation));

        // store peer information
        Identity clientIdentity = channel.attr(ATTRIBUTE_IDENTITY).get();
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("[{}]: Client {} can Route to {}", channel.id().asShortText(), clientIdentity, grandchildIdentity);
        }
        peersManager.addPeerInformationAndAddGrandchildren(grandchildIdentity, grandchildInformation, clientIdentity);
    }

    private void unregisterGrandchild(ChannelHandlerContext ctx,
                                      Identity grandchildIdentity,
                                      PeerInformation grandchildInformation) {
        Channel channel = ctx.channel();

        if (getLogger().isDebugEnabled()) {
            getLogger().debug("[{}]: Client want to unregister Grandchild {}", channel.id().asShortText(), grandchildIdentity);
        }

        // unregister grandchild at super peer
        unregisterGrandchildAtSuperPeer(ctx, grandchildIdentity, grandchildInformation);

        // remove peer information
        peersManager.removeGrandchildrenRouteAndRemovePeerInformation(grandchildIdentity, grandchildInformation);
    }

    private void handleWhoisMessage(ChannelHandlerContext ctx, WhoisMessage whoisMessage) {
        Address requester = whoisMessage.getRequester();
        Address address = whoisMessage.getAddress();
        Pair<Identity, PeerInformation> identityAndPeerInformation = peersManager.getIdentityAndPeerInformation(address);
        Identity identity = identityAndPeerInformation.first();
        PeerInformation peerInformation = identityAndPeerInformation.second();
        if (identity.hasPublicKey()) {
            // we have the requested information. Send it back to the requester.
            ctx.writeAndFlush(new IdentityMessage(requester, identity, peerInformation, whoisMessage.getId()));
        }
        else {
            // we cannot provide the requested information. Forward request to Super Peer.
            Pair<Identity, PeerInformation> superPeer = peersManager.getSuperPeer();
            if (superPeer != null) {
                PeerInformation superPeerInformation = superPeer.second();
                Path superPeerPath = superPeerInformation.getPaths().iterator().next();
                if (superPeerPath != null) {
                    superPeerPath.send(whoisMessage);
                }
            }
        }
    }
}
