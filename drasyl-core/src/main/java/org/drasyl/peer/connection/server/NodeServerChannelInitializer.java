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
import org.drasyl.peer.connection.DefaultSessionInitializer;
import org.drasyl.peer.connection.handler.ConnectionExceptionMessageHandler;
import org.drasyl.peer.connection.handler.ExceptionHandler;
import org.drasyl.peer.connection.handler.QuitMessageHandler;
import org.drasyl.peer.connection.server.handler.NodeServerConnectionHandler;
import org.drasyl.peer.connection.server.handler.NodeServerJoinGuard;
import org.drasyl.peer.connection.server.handler.NodeServerMissingWebSocketUpgradeErrorPage;
import org.drasyl.peer.connection.server.handler.NodeServerNewConnectionsGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;

import static org.drasyl.peer.connection.server.handler.NodeServerConnectionHandler.HANDLER;
import static org.drasyl.peer.connection.server.handler.NodeServerJoinGuard.JOIN_GUARD;

/**
 * Creates a newly configured {@link ChannelPipeline} for the node server.
 */
@SuppressWarnings({ "java:S4818" })
public class NodeServerChannelInitializer extends DefaultSessionInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(NodeServerChannelInitializer.class);
    protected final NodeServer server;

    public NodeServerChannelInitializer(NodeServer server) {
        super(server.getConfig().getFlushBufferSize(), server.getConfig().getServerIdleTimeout(),
                server.getConfig().getServerIdleRetries());
        this.server = server;
    }

    @Override
    protected void beforeMarshalStage(ChannelPipeline pipeline) {
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));
        pipeline.addLast(new WebSocketServerCompressionHandler());
        pipeline.addLast(new NodeServerMissingWebSocketUpgradeErrorPage(server.getIdentityManager()));
        pipeline.addLast(new WebSocketServerProtocolHandler("/", null, true));
    }

    @Override
    protected void afterPojoMarshalStage(ChannelPipeline pipeline) {
        pipeline.addLast(NodeServerNewConnectionsGuard.CONNECTION_GUARD, new NodeServerNewConnectionsGuard(server::isOpen));
    }

    @Override
    protected void customStage(ChannelPipeline pipeline) {
        // QuitMessage handler
        pipeline.addLast(QuitMessageHandler.QUIT_MESSAGE_HANDLER, QuitMessageHandler.INSTANCE);

        // ConnectionExceptionMessage Handler
        pipeline.addLast(ConnectionExceptionMessageHandler.EXCEPTION_MESSAGE_HANDLER, ConnectionExceptionMessageHandler.INSTANCE);

        // Guards
        pipeline.addLast(JOIN_GUARD, new NodeServerJoinGuard(server.getIdentityManager().getKeyPair().getPublicKey(), server.getConfig().getServerHandshakeTimeout()));

        // Server handler
        pipeline.addLast(HANDLER, new NodeServerConnectionHandler(this.server));
    }

    @Override
    protected void exceptionStage(ChannelPipeline pipeline) {
        // Catch Errors
        pipeline.addLast(ExceptionHandler.EXCEPTION_HANDLER, new ExceptionHandler());
    }

    @Override
    protected SslHandler generateSslContext(SocketChannel ch) {
        if (server.getConfig().getServerSSLEnabled()) {
            try {
                SelfSignedCertificate ssc = new SelfSignedCertificate();

                return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                        .protocols(server.getConfig().getServerSSLProtocols()).build().newHandler(ch.alloc());
            }
            catch (SSLException | CertificateException e) {
                LOG.error("SSLException: ", e);
            }
        }
        return null;
    }
}
