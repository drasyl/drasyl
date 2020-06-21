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
import org.drasyl.peer.connection.handler.ConnectionExceptionMessageHandler;
import org.drasyl.peer.connection.handler.ExceptionHandler;
import org.drasyl.peer.connection.handler.RelayableMessageGuard;
import org.drasyl.peer.connection.handler.SignatureHandler;
import org.drasyl.util.WebSocketUtil;

import javax.net.ssl.SSLException;

import static org.drasyl.peer.connection.handler.ConnectionExceptionMessageHandler.EXCEPTION_MESSAGE_HANDLER;
import static org.drasyl.peer.connection.handler.ExceptionHandler.EXCEPTION_HANDLER;
import static org.drasyl.peer.connection.handler.RelayableMessageGuard.HOP_COUNT_GUARD;
import static org.drasyl.peer.connection.handler.SignatureHandler.SIGNATURE_HANDLER;
import static org.drasyl.peer.connection.superpeer.SuperPeerClientConnectionHandler.SUPER_PEER_CLIENT_CONNECTION_HANDLER;

/**
 * Creates a newly configured {@link ChannelPipeline} for a ClientConnection to a node server.
 */
@SuppressWarnings({ "java:S110", "java:S4818" })
public class DefaultSuperPeerClientChannelInitializer extends SuperPeerClientChannelInitializer {
    private final SuperPeerClientEnvironment environment;

    public DefaultSuperPeerClientChannelInitializer(SuperPeerClientEnvironment environment) {
        super(environment.getConfig().getFlushBufferSize(), environment.getConfig().getSuperPeerIdleTimeout(),
                environment.getConfig().getSuperPeerIdleRetries(), environment.getEndpoint());
        this.environment = environment;
    }

    @Override
    protected void afterPojoMarshalStage(ChannelPipeline pipeline) {
        pipeline.addLast(SIGNATURE_HANDLER, new SignatureHandler(environment.getIdentity()));
        pipeline.addLast(HOP_COUNT_GUARD, new RelayableMessageGuard(environment.getConfig().getMessageHopLimit()));
    }

    @Override
    protected void customStage(ChannelPipeline pipeline) {
        pipeline.addLast(EXCEPTION_MESSAGE_HANDLER, ConnectionExceptionMessageHandler.INSTANCE);
        pipeline.addLast(SUPER_PEER_CLIENT_CONNECTION_HANDLER, new SuperPeerClientConnectionHandler(environment));
    }

    @Override
    protected void exceptionStage(ChannelPipeline pipeline) {
        pipeline.addLast(EXCEPTION_HANDLER, new ExceptionHandler());
    }

    @Override
    protected SslHandler generateSslContext(SocketChannel ch) throws SuperPeerClientException {
        if (WebSocketUtil.isWebSocketSecureURI(target)) {
            try {
                SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .protocols(environment.getConfig().getServerSSLProtocols()).build();
                return sslContext.newHandler(ch.alloc(), target.getHost(), WebSocketUtil.webSocketPort(target));
            }
            catch (SSLException e) {
                throw new SuperPeerClientException(e);
            }
        }
        return null;
    }
}
