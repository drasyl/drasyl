/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
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
import io.sentry.util.CircularFifoQueue;
import org.drasyl.core.common.handler.*;
import org.drasyl.core.common.handler.codec.message.MessageEncoder;
import org.drasyl.core.server.RelayServer;
import org.drasyl.core.server.handler.codec.message.ServerActionMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;

/**
 * Creates a newly configured {@link ChannelPipeline} for the relay server.
 */
public class RelayServerInitializer extends DefaultSessionInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(RelayServerInitializer.class);
    private final CircularFifoQueue<String> outboundMessagesQueue;
    private final RelayServer relay;

    public RelayServerInitializer(RelayServer relay) {
        super(relay.getConfig().getRelayFlushBufferSize(), relay.getConfig().getIdleTimeout(),
                relay.getConfig().getIdleRetries());
        this.outboundMessagesQueue = new CircularFifoQueue<>(relay.getConfig().getRelayMsgBucketLimit());
        this.relay = relay;
    }

    @Override
    protected SslHandler generateSslContext(SocketChannel ch) {
        if (relay.getConfig().isRelaySslEnabled()) {
            try {
                SelfSignedCertificate ssc = new SelfSignedCertificate();

                return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                        .protocols(relay.getConfig().getRelaySslProtocols()).build().newHandler(ch.alloc());
            } catch (SSLException | CertificateException e) {
                LOG.error("SSLException: ", e);
            }
        }
        return null;
    }

    @Override
    protected void pojoMarshalStage(ChannelPipeline pipeline) {
        // From String to Message
        pipeline.addLast("messageDecoder", ServerActionMessageDecoder.INSTANCE);
        pipeline.addLast("messageEncoder", MessageEncoder.INSTANCE);
    }

    @Override
    protected void beforeMarshalStage(ChannelPipeline pipeline) {
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(relay.getConfig().getRelayMaxContentLength()));
        pipeline.addLast(new WebSocketServerCompressionHandler());
        pipeline.addLast(new WebSocketServerProtocolHandler("/", null, true));
    }

    @Override
    protected void customStage(ChannelPipeline pipeline) {
        // Filter duplicate messages
        pipeline.addLast("filterDuplicates", new DuplicateMessageFilter(outboundMessagesQueue,
                relay.getConfig().getRelayMsgBucketLimit()));

        // Leave handler
        pipeline.addLast("leaveHandler", LeaveHandler.INSTANCE);

        // Guards
        pipeline.addLast("joinGuard", new JoinHandler(relay.getConfig().getRelayMaxHandshakeTimeout().toMillis()));

        // Filters not expected response messages
        pipeline.addLast("responseFilter", new ResponseFilter(outboundMessagesQueue));

        // Server handler
        pipeline.addLast("handler", new SessionHandler(this.relay));
    }

    @Override
    protected void exceptionStage(ChannelPipeline pipeline) {
        // Catch Errors
        pipeline.addLast("exceptionHandler", new ExceptionHandler(true));
    }

    @Override
    protected void afterExceptionStage(ChannelPipeline pipeline) {
        // Kill if Client is not initialized
        pipeline.addLast("killSwitch", new KillOnExceptionHandler(relay));
    }
}
