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

import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.peer.connection.AbstractClientInitializer;
import org.drasyl.peer.connection.handler.ConnectionExceptionMessageHandler;
import org.drasyl.peer.connection.handler.ExceptionHandler;
import org.drasyl.peer.connection.handler.QuitMessageHandler;
import org.drasyl.peer.connection.superpeer.handler.SuperPeerClientConnectionHandler;
import org.drasyl.peer.connection.superpeer.handler.SuperPeerClientJoinHandler;
import org.drasyl.peer.connection.superpeer.handler.SuperPeerClientWelcomeGuard;
import org.drasyl.util.WebSocketUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.util.Set;

import static org.drasyl.peer.connection.superpeer.handler.SuperPeerClientJoinHandler.JOIN_HANDLER;
import static org.drasyl.peer.connection.superpeer.handler.SuperPeerClientWelcomeGuard.WELCOME_GUARD;

/**
 * Creates a newly configured {@link ChannelPipeline} for a ClientConnection to a node server.
 */
@SuppressWarnings({ "java:S110", "java:S4818" })
public class SuperPeerClientChannelInitializer extends AbstractClientInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClientChannelInitializer.class);
    private final DrasylNodeConfig config;
    private final Set<URI> entryPoints;
    private final SuperPeerClient superPeerClient;
    private final SuperPeerClientJoinHandler joinHandler;

    public SuperPeerClientChannelInitializer(DrasylNodeConfig config,
                                             URI endpoint,
                                             Set<URI> entryPoints,
                                             SuperPeerClient superPeerClient) {
        super(config.getFlushBufferSize(), config.getSuperPeerIdleTimeout(),
                config.getSuperPeerIdleRetries(), endpoint);
        this.config = config;
        this.entryPoints = entryPoints;
        this.superPeerClient = superPeerClient;
        joinHandler = new SuperPeerClientJoinHandler(this.superPeerClient.getIdentityManager().getKeyPair().getPublicKey(), this.entryPoints);
    }

    @Override
    protected void customStage(ChannelPipeline pipeline) {
        // QuitMessage handler
        pipeline.addLast(QuitMessageHandler.QUIT_MESSAGE_HANDLER, QuitMessageHandler.INSTANCE);

        // ConnectionExceptionMessage Handler
        pipeline.addLast(ConnectionExceptionMessageHandler.EXCEPTION_MESSAGE_HANDLER, ConnectionExceptionMessageHandler.INSTANCE);

        // Guards
        pipeline.addLast(WELCOME_GUARD, new SuperPeerClientWelcomeGuard(config.getSuperPeerPublicKey(), superPeerClient.getIdentityManager().getKeyPair().getPublicKey(), config.getSuperPeerHandshakeTimeout()));

        pipeline.addLast(JOIN_HANDLER, joinHandler);

        // Super peer handler
        pipeline.addLast(SuperPeerClientConnectionHandler.SUPER_PEER_HANDLER, new SuperPeerClientConnectionHandler(superPeerClient, target));
    }

    @Override
    protected void exceptionStage(ChannelPipeline pipeline) {
        // Catch Errors
        pipeline.addLast(ExceptionHandler.EXCEPTION_HANDLER, new ExceptionHandler());
    }

    @Override
    protected SslHandler generateSslContext(SocketChannel ch) {
        if (WebSocketUtil.isWebSocketSecureURI(target)) {
            try {
                SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .protocols(config.getServerSSLProtocols()).build();
                return sslContext.newHandler(ch.alloc(), target.getHost(), WebSocketUtil.webSocketPort(target));
            }
            catch (SSLException e) {
                LOG.error("SSLException: ", e);
            }
        }
        return null;
    }

    public SuperPeerClientJoinHandler getJoinHandler() {
        return joinHandler;
    }
}
