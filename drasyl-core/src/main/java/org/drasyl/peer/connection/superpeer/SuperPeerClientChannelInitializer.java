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
import io.netty.handler.stream.ChunkedWriteHandler;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.AbstractClientInitializer;
import org.drasyl.peer.connection.handler.stream.ChunkedMessageHandler;
import org.drasyl.peer.connection.handler.ConnectionExceptionMessageHandler;
import org.drasyl.peer.connection.handler.ExceptionHandler;
import org.drasyl.peer.connection.handler.RelayableMessageGuard;
import org.drasyl.peer.connection.handler.SignatureHandler;
import org.drasyl.peer.connection.superpeer.handler.SuperPeerClientConnectionHandler;
import org.drasyl.util.WebSocketUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.peer.connection.handler.stream.ChunkedMessageHandler.CHUNK_HANDLER;
import static org.drasyl.peer.connection.handler.RelayableMessageGuard.HOP_COUNT_GUARD;
import static org.drasyl.peer.connection.superpeer.handler.SuperPeerClientConnectionHandler.SUPER_PEER_CLIENT_CONNECTION_HANDLER;

/**
 * Creates a newly configured {@link ChannelPipeline} for a ClientConnection to a node server.
 */
@SuppressWarnings({ "java:S110", "java:S4818" })
public class SuperPeerClientChannelInitializer extends AbstractClientInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClientChannelInitializer.class);
    private final DrasylNodeConfig config;
    private final SuperPeerClientConnectionHandler clientHandler;
    private final Identity identity;

    public SuperPeerClientChannelInitializer(DrasylNodeConfig config,
                                             URI endpoint,
                                             SuperPeerClient superPeerClient) {
        super(config.getFlushBufferSize(), config.getSuperPeerIdleTimeout(),
                config.getSuperPeerIdleRetries(), endpoint);
        this.config = config;
        this.identity = superPeerClient.getIdentityManager().getIdentity();
        clientHandler = new SuperPeerClientConnectionHandler(superPeerClient.getIdentityManager().getProofOfWork(), this.config.getSuperPeerPublicKey(), superPeerClient.getIdentityManager().getPublicKey(), this.config.getSuperPeerHandshakeTimeout(), superPeerClient.getPeersManager(), superPeerClient.getMessenger());
    }

    public CompletableFuture<Void> handshakeFuture() {
        return clientHandler.handshakeFuture();
    }

    @Override
    protected void afterPojoMarshalStage(ChannelPipeline pipeline) {
        pipeline.addLast(SignatureHandler.SIGNATURE_HANDLER, new SignatureHandler(identity));
        pipeline.addLast(HOP_COUNT_GUARD, new RelayableMessageGuard(config.getMessageHopLimit()));
        pipeline.addLast("streamer", new ChunkedWriteHandler());
        pipeline.addLast(CHUNK_HANDLER, new ChunkedMessageHandler(config.getMessageMaxContentLength(), identity.getPublicKey()));
    }

    @Override
    protected void customStage(ChannelPipeline pipeline) {
        // Exception handler
        pipeline.addLast(ConnectionExceptionMessageHandler.EXCEPTION_MESSAGE_HANDLER, ConnectionExceptionMessageHandler.INSTANCE);

        // Client handler
        pipeline.addLast(SUPER_PEER_CLIENT_CONNECTION_HANDLER, clientHandler);
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
}
