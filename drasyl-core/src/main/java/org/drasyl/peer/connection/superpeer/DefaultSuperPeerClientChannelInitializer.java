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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
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
import org.drasyl.util.WebSocketUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;

import static io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE;
import static io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT;
import static org.drasyl.peer.connection.handler.ConnectionExceptionMessageHandler.EXCEPTION_MESSAGE_HANDLER;
import static org.drasyl.peer.connection.handler.ExceptionHandler.EXCEPTION_HANDLER;
import static org.drasyl.peer.connection.handler.RelayableMessageGuard.HOP_COUNT_GUARD;
import static org.drasyl.peer.connection.handler.SignatureHandler.SIGNATURE_HANDLER;
import static org.drasyl.peer.connection.handler.stream.ChunkedMessageHandler.CHUNK_HANDLER;
import static org.drasyl.peer.connection.superpeer.SuperPeerClientConnectionHandler.SUPER_PEER_CLIENT_CONNECTION_HANDLER;

/**
 * Creates a newly configured {@link ChannelPipeline} for a ClientConnection to a node server.
 */
@SuppressWarnings({ "java:S110", "java:S4818" })
public class DefaultSuperPeerClientChannelInitializer extends SuperPeerClientChannelInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultSuperPeerClientChannelInitializer.class);
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
        pipeline.addLast("streamer", new ChunkedWriteHandler());
        pipeline.addLast(CHUNK_HANDLER, new ChunkedMessageHandler(environment.getConfig().getMessageMaxContentLength(), environment.getIdentity().getPublicKey(), environment.getConfig().getComposedMessageTransferTimeout()));
    }

    @Override
    protected void customStage(ChannelPipeline pipeline) {
        pipeline.addLast(EXCEPTION_MESSAGE_HANDLER, ConnectionExceptionMessageHandler.INSTANCE);

        pipeline.addLast(new SimpleChannelInboundHandler<>() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                super.userEventTriggered(ctx, evt);

                if (evt instanceof WebSocketClientProtocolHandler.ClientHandshakeStateEvent) {
                    WebSocketClientProtocolHandler.ClientHandshakeStateEvent e = (WebSocketClientProtocolHandler.ClientHandshakeStateEvent) evt;
                    if (e == HANDSHAKE_COMPLETE) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("[{}]: WebSocket Handshake completed. Now adding SuperPeerClientConnectionHandler.", ctx.channel().id().asShortText());
                        }
                        pipeline.addLast(SUPER_PEER_CLIENT_CONNECTION_HANDLER, new SuperPeerClientConnectionHandler(environment));
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

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                ctx.fireChannelRead(msg);
            }
        });
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
