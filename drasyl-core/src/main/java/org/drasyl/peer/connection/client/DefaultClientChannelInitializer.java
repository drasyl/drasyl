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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.drasyl.peer.connection.handler.ConnectionExceptionMessageHandler;
import org.drasyl.peer.connection.handler.ExceptionHandler;
import org.drasyl.peer.connection.handler.RelayableMessageGuard;
import org.drasyl.peer.connection.handler.SignatureHandler;
import org.drasyl.peer.connection.handler.stream.ChunkedMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;

import static io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE;
import static io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT;
import static org.drasyl.peer.connection.client.ClientConnectionHandler.CLIENT_CONNECTION_HANDLER;
import static org.drasyl.peer.connection.handler.ConnectionExceptionMessageHandler.EXCEPTION_MESSAGE_HANDLER;
import static org.drasyl.peer.connection.handler.ExceptionHandler.EXCEPTION_HANDLER;
import static org.drasyl.peer.connection.handler.RelayableMessageGuard.HOP_COUNT_GUARD;
import static org.drasyl.peer.connection.handler.SignatureHandler.SIGNATURE_HANDLER;
import static org.drasyl.peer.connection.handler.stream.ChunkedMessageHandler.CHUNK_HANDLER;

/**
 * Creates a newly configured {@link ChannelPipeline} for a ClientConnection to a node server.
 */
@SuppressWarnings({ "java:S110", "java:S4818" })
public class DefaultClientChannelInitializer extends ClientChannelInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultClientChannelInitializer.class);
    protected static final String DRASYL_HANDSHAKE_AFTER_WEBSOCKET_HANDSHAKE = "drasylHandshakeAfterWebsocketHandshake";
    private final ClientEnvironment environment;

    public DefaultClientChannelInitializer(final ClientEnvironment environment) {
        super(environment.getConfig().getFlushBufferSize(), environment.getIdleTimeout(),
                environment.getIdleRetries(), environment.getEndpoint());
        this.environment = environment;
    }

    @Override
    protected void afterPojoMarshalStage(final ChannelPipeline pipeline) {
        pipeline.addLast(SIGNATURE_HANDLER, new SignatureHandler(environment.getIdentity()));
        pipeline.addLast(HOP_COUNT_GUARD, new RelayableMessageGuard(environment.getConfig().getMessageHopLimit()));
        pipeline.addLast(CHUNKED_WRITER, new ChunkedWriteHandler());
        pipeline.addLast(CHUNK_HANDLER, new ChunkedMessageHandler(environment.getConfig().getMessageMaxContentLength(), environment.getIdentity().getPublicKey(), environment.getConfig().getMessageComposedMessageTransferTimeout()));
    }

    @Override
    protected void customStage(final ChannelPipeline pipeline) {
        pipeline.addLast(EXCEPTION_MESSAGE_HANDLER, ConnectionExceptionMessageHandler.INSTANCE);

        pipeline.addLast(DRASYL_HANDSHAKE_AFTER_WEBSOCKET_HANDSHAKE, new MyChannelInboundHandlerAdapter(pipeline));
    }

    @Override
    protected void exceptionStage(final ChannelPipeline pipeline) {
        pipeline.addLast(EXCEPTION_HANDLER, new ExceptionHandler(environment.getIdentity()));
    }

    @Override
    protected SslHandler generateSslContext(final SocketChannel ch) throws ClientException {
        if (target.isSecureEndpoint()) {
            try {
                final SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .protocols(environment.getConfig().getServerSSLProtocols()).build();
                return sslContext.newHandler(ch.alloc(), target.getHost(), target.getPort());
            }
            catch (final SSLException e) {
                throw new ClientException(e);
            }
        }
        return null;
    }

    private class MyChannelInboundHandlerAdapter extends ChannelInboundHandlerAdapter {
        private final ChannelPipeline pipeline;

        public MyChannelInboundHandlerAdapter(final ChannelPipeline pipeline) {
            this.pipeline = pipeline;
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx,
                                       final Object evt) throws Exception {
            super.userEventTriggered(ctx, evt);

            if (evt instanceof ClientHandshakeStateEvent) {
                handleClientHandshakeStateEvent(ctx, (ClientHandshakeStateEvent) evt);
            }
        }

        private void handleClientHandshakeStateEvent(final ChannelHandlerContext ctx,
                                                     final ClientHandshakeStateEvent e) {
            if (e == HANDSHAKE_COMPLETE) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("[{}]: Public key available. Now adding {}.", ctx.channel().id().asShortText(), ClientConnectionHandler.class.getSimpleName());
                }
                // Must be added before the exception handler otherwise exceptions are not captured anymore and raising an error
                // See: https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/issues/77
                pipeline.addBefore(EXCEPTION_HANDLER, CLIENT_CONNECTION_HANDLER, new ClientConnectionHandler(environment));
                pipeline.remove(this);
            }
            else if (e == HANDSHAKE_TIMEOUT) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("[{}]: WebSocket Handshake timed out. Close channel.", ctx.channel().id().asShortText());
                }
                ctx.close();
            }
        }
    }
}