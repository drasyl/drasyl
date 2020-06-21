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

import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.peer.connection.DefaultSessionInitializer;
import org.drasyl.peer.connection.handler.ConnectionExceptionMessageHandler;
import org.drasyl.peer.connection.handler.ExceptionHandler;
import org.drasyl.peer.connection.handler.RelayableMessageGuard;
import org.drasyl.peer.connection.handler.SignatureHandler;
import org.drasyl.peer.connection.server.handler.NodeServerHttpHandler;
import org.drasyl.peer.connection.server.handler.NodeServerNewConnectionsGuard;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;

import static org.drasyl.peer.connection.handler.RelayableMessageGuard.HOP_COUNT_GUARD;
import static org.drasyl.peer.connection.server.NodeServerConnectionHandler.NODE_SERVER_CONNECTION_HANDLER;

/**
 * Creates a newly configured {@link ChannelPipeline} for the node server.
 */
@SuppressWarnings({ "java:S4818" })
public class NodeServerChannelInitializer extends DefaultSessionInitializer {
    private final DrasylNodeConfig config;
    protected final NodeServer server;

    public NodeServerChannelInitializer(DrasylNodeConfig config, NodeServer server) {
        super(config.getFlushBufferSize(), config.getServerIdleTimeout(), config.getServerIdleRetries());
        this.config = config;
        this.server = server;
    }

    @Override
    protected void beforeMarshalStage(ChannelPipeline pipeline) {
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));
        pipeline.addLast(new WebSocketServerCompressionHandler());
        pipeline.addLast(new NodeServerHttpHandler(server.getIdentity().getPublicKey(), server.getPeersManager()));
        pipeline.addLast(new WebSocketServerProtocolHandler("/", null, true));
    }

    @Override
    protected void afterPojoMarshalStage(ChannelPipeline pipeline) {
        pipeline.addLast(SignatureHandler.SIGNATURE_HANDLER, new SignatureHandler(server.getIdentity()));
        pipeline.addLast(HOP_COUNT_GUARD, new RelayableMessageGuard(config.getMessageHopLimit()));
        pipeline.addLast(NodeServerNewConnectionsGuard.CONNECTION_GUARD, new NodeServerNewConnectionsGuard(() -> server.isOpen() && (!config.isSuperPeerEnabled() || server.getSuperPeerConnected().blockingFirst())));
    }

    @Override
    protected void customStage(ChannelPipeline pipeline) {
        // Exception handler
        pipeline.addLast(ConnectionExceptionMessageHandler.EXCEPTION_MESSAGE_HANDLER, ConnectionExceptionMessageHandler.INSTANCE);

        // Server handler
        pipeline.addLast(NODE_SERVER_CONNECTION_HANDLER, new NodeServerConnectionHandler(config, server));
    }

    @Override
    protected void exceptionStage(ChannelPipeline pipeline) {
        // Catch Errors
        pipeline.addLast(ExceptionHandler.EXCEPTION_HANDLER, new ExceptionHandler());
    }

    @Override
    protected SslHandler generateSslContext(SocketChannel ch) throws NodeServerException {
        if (config.getServerSSLEnabled()) {
            try {
                SelfSignedCertificate ssc = new SelfSignedCertificate();

                return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                        .protocols(config.getServerSSLProtocols()).build().newHandler(ch.alloc());
            }
            catch (SSLException | CertificateException e) {
                throw new NodeServerException(e);
            }
        }
        return null;
    }
}
