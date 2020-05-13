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
package org.drasyl.core.server.handler;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.drasyl.core.common.handler.ConnectionGuardHandler;
import org.drasyl.core.common.handler.DefaultSessionInitializer;
import org.drasyl.core.common.handler.ExceptionHandler;
import org.drasyl.core.common.handler.LeaveHandler;
import org.drasyl.core.common.handler.codec.message.MessageDecoder;
import org.drasyl.core.common.handler.codec.message.MessageEncoder;
import org.drasyl.core.server.NodeServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;

import static org.drasyl.core.common.handler.ConnectionGuardHandler.CONNECTION_GUARD;
import static org.drasyl.core.common.handler.ExceptionHandler.EXCEPTION_HANDLER;
import static org.drasyl.core.common.handler.LeaveHandler.LEAVE_HANDLER;
import static org.drasyl.core.common.handler.codec.message.MessageDecoder.MESSAGE_DECODER;
import static org.drasyl.core.common.handler.codec.message.MessageEncoder.MESSAGE_ENCODER;
import static org.drasyl.core.server.handler.JoinHandler.JOIN_GUARD;
import static org.drasyl.core.server.handler.KillOnExceptionHandler.KILL_SWITCH;
import static org.drasyl.core.server.handler.ServerSessionHandler.HANDLER;

/**
 * Creates a newly configured {@link ChannelPipeline} for the node server.
 */
@SuppressWarnings({ "java:S4818" })
public class NodeServerInitializer extends DefaultSessionInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(NodeServerInitializer.class);
    private final NodeServer server;

    public NodeServerInitializer(NodeServer server) {
        super(server.getConfig().getFlushBufferSize(), server.getConfig().getServerIdleTimeout(),
                server.getConfig().getServerIdleRetries());
        this.server = server;
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

    @Override
    protected void pojoMarshalStage(ChannelPipeline pipeline) {
        // From String to Message
        pipeline.addLast(MESSAGE_DECODER, MessageDecoder.INSTANCE);
        pipeline.addLast(MESSAGE_ENCODER, MessageEncoder.INSTANCE);
    }

    @Override
    protected void beforeMarshalStage(ChannelPipeline pipeline) {
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));
        pipeline.addLast(new WebSocketServerCompressionHandler());
        pipeline.addLast(new WebSocketMissingUpgradeErrorPageHandler(server.getMyIdentity()));
        pipeline.addLast(new WebSocketServerProtocolHandler("/", null, true));
    }

    @Override
    protected void afterPojoMarshalStage(ChannelPipeline pipeline) {
        pipeline.addLast(CONNECTION_GUARD, new ConnectionGuardHandler(server::isOpen));
    }

    @Override
    protected void customStage(ChannelPipeline pipeline) {
        // Leave handler
        pipeline.addLast(LEAVE_HANDLER, LeaveHandler.INSTANCE);

        // Guards
        pipeline.addLast(JOIN_GUARD, new JoinHandler(server.getConfig().getServerHandshakeTimeout().toMillis()));

        // Server handler
        pipeline.addLast(HANDLER, new ServerSessionHandler(this.server));
    }

    @Override
    protected void exceptionStage(ChannelPipeline pipeline) {
        // Catch Errors
        pipeline.addLast(EXCEPTION_HANDLER, new ExceptionHandler(true));
    }

    @Override
    protected void afterExceptionStage(ChannelPipeline pipeline) {
        // Kill if Client is not initialized
        pipeline.addLast(KILL_SWITCH, KillOnExceptionHandler.INSTANCE);
    }
}
