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
import org.drasyl.peer.connection.handler.ExceptionHandler;
import org.drasyl.peer.connection.handler.RelayableMessageGuard;
import org.drasyl.peer.connection.handler.SignatureHandler;
import org.drasyl.peer.connection.server.handler.ServerHttpHandler;
import org.drasyl.peer.connection.server.handler.ServerNewConnectionsGuard;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;

import static org.drasyl.peer.connection.handler.ExceptionHandler.EXCEPTION_HANDLER;
import static org.drasyl.peer.connection.handler.RelayableMessageGuard.HOP_COUNT_GUARD;
import static org.drasyl.peer.connection.handler.SignatureHandler.SIGNATURE_HANDLER;
import static org.drasyl.peer.connection.server.ServerConnectionHandler.SERVER_CONNECTION_HANDLER;
import static org.drasyl.peer.connection.server.handler.ServerNewConnectionsGuard.CONNECTION_GUARD;

/**
 * Creates a newly configured {@link ChannelPipeline} for the node server.
 */
@SuppressWarnings({ "java:S4818", "java:S110" })
public class DefaultServerChannelInitializer extends ServerChannelInitializer {
    protected final ServerEnvironment environment;

    public DefaultServerChannelInitializer(final ServerEnvironment environment) {
        super(environment.getConfig().getNetworkId(), environment.getIdentity(), environment.getConfig().getFlushBufferSize(), environment.getConfig().getServerIdleTimeout(), environment.getConfig().getServerIdleRetries());
        this.environment = environment;
    }

    @Override
    protected void beforeMarshalStage(final ChannelPipeline pipeline) {
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));
        pipeline.addLast(new WebSocketServerCompressionHandler());
        pipeline.addLast(new ServerHttpHandler(environment.getConfig().getNetworkId(), environment.getIdentity().getPublicKey(), environment.getPeersManager()));
        pipeline.addLast(new WebSocketServerProtocolHandler("/", null, true));
    }

    @Override
    protected void afterPojoMarshalStage(final ChannelPipeline pipeline) {
        pipeline.addLast(SIGNATURE_HANDLER, new SignatureHandler(environment.getConfig().getNetworkId(), environment.getIdentity()));
        pipeline.addLast(HOP_COUNT_GUARD, new RelayableMessageGuard(environment.getConfig().getMessageHopLimit()));
        pipeline.addLast(CONNECTION_GUARD, new ServerNewConnectionsGuard(environment.getConfig().getNetworkId(), environment.getIdentity(), environment.getAcceptNewConnectionsSupplier()));
    }

    @Override
    protected void customStage(final ChannelPipeline pipeline) {
        pipeline.addLast(SERVER_CONNECTION_HANDLER, new ServerConnectionHandler(environment));
    }

    @Override
    protected void exceptionStage(final ChannelPipeline pipeline) {
        pipeline.addLast(EXCEPTION_HANDLER, new ExceptionHandler(environment.getIdentity()));
    }

    @Override
    protected SslHandler generateSslContext(final SocketChannel ch) throws ServerException {
        if (environment.getConfig().getServerSSLEnabled()) {
            try {
                final SelfSignedCertificate ssc = new SelfSignedCertificate();

                return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                        .protocols(environment.getConfig().getServerSSLProtocols()).build().newHandler(ch.alloc());
            }
            catch (final SSLException | CertificateException e) {
                throw new ServerException(e);
            }
        }
        return null;
    }
}