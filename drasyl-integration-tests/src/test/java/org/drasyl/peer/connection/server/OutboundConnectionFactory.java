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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.DefaultSessionInitializer;
import org.drasyl.peer.connection.handler.ExceptionHandler;
import org.drasyl.peer.connection.handler.MessageDecoder;
import org.drasyl.peer.connection.handler.MessageEncoder;
import org.drasyl.peer.connection.handler.SignatureHandler;
import org.drasyl.peer.connection.superpeer.SuperPeerClientChannelInitializer;
import org.drasyl.peer.connection.superpeer.SuperPeerClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.peer.connection.handler.ExceptionHandler.EXCEPTION_HANDLER;
import static org.drasyl.peer.connection.handler.MessageDecoder.MESSAGE_DECODER;
import static org.drasyl.peer.connection.handler.MessageEncoder.MESSAGE_ENCODER;
import static org.drasyl.util.WebSocketUtil.webSocketPort;

/**
 * This factory produces outbound netty connections.
 */
@SuppressWarnings({ "squid:S3776", "squid:S00107" })
public class OutboundConnectionFactory {
    private static final Logger LOG = LoggerFactory.getLogger(OutboundConnectionFactory.class);
    private final CompletableFuture<Void> channelReadyFuture;
    private final List<ChannelHandler> handler;
    private final List<String> sslProtocols;
    private final URI uri;
    private Runnable shutdownProcedure;
    private ChannelHandler initializer;
    private EventLoopGroup eventGroup;
    private boolean pingPong = true;
    private SslContext sslCtx;
    private Duration idleTimeout;
    private short idleRetries;
    private boolean ssl;
    private final Identity identity;

    /**
     * Produces an {@link OutboundConnectionFactory} with the given {@link URI} as target.
     *
     * @param target the IP address of the target system
     */
    public OutboundConnectionFactory(URI target,
                                     EventLoopGroup eventGroup,
                                     Identity identity) {
        this(target, null, () -> {
        }, null, new ArrayList<>(), Collections.singletonList("TLSv1.3"), eventGroup, identity);
    }

    private OutboundConnectionFactory(URI target,
                                      ChannelInitializer<SocketChannel> initializer,
                                      Runnable shutdownProcedure,
                                      SslContext sslCtx,
                                      List<ChannelHandler> handler,
                                      List<String> sslProtocols,
                                      EventLoopGroup eventGroup,
                                      Identity identity) {
        this.uri = target;
        this.initializer = initializer;
        this.shutdownProcedure = shutdownProcedure;
        this.sslCtx = sslCtx;
        this.handler = handler;
        this.sslProtocols = sslProtocols;
        this.eventGroup = eventGroup;
        this.channelReadyFuture = new CompletableFuture<>();
        this.identity = identity;
    }

    /**
     * Adds a handler to the default initializer.
     *
     * <b>If you override the default initializer with {@link #initializer}, this method does
     * nothing.</b>
     *
     * @param handler handler that should be added
     * @return {@link OutboundConnectionFactory} with the changed property
     */
    public OutboundConnectionFactory handler(ChannelHandler handler) {
        this.handler.add(handler);

        return this;
    }

    /**
     * De-/Activates SSL on this channel.
     *
     * @param ssl if ssl should be used or not
     * @return {@link OutboundConnectionFactory} with the changed property
     */
    public OutboundConnectionFactory ssl(boolean ssl) {
        this.ssl = ssl;

        return this;
    }

    /**
     * Sets the max idle retries before the connection is closed.
     *
     * @param retries amount of retries
     * @return {@link OutboundConnectionFactory} with the changed property
     */
    public OutboundConnectionFactory idleRetries(short retries) {
        this.idleRetries = retries;

        return this;
    }

    /**
     * Sets the max idle timeout before connection is closed.
     *
     * @param timeout the timeout in ms
     * @return {@link OutboundConnectionFactory} with the changed property
     */
    public OutboundConnectionFactory idleTimeout(Duration timeout) {
        this.idleTimeout = timeout;

        return this;
    }

    /**
     * De-/Activate automatically ping/pong handling.
     *
     * @param enabled enabled or not
     * @return {@link OutboundConnectionFactory} with the changed property
     */
    public OutboundConnectionFactory pingPong(boolean enabled) {
        this.pingPong = enabled;

        return this;
    }

    /**
     * Builds a {@link Channel} from this {@link OutboundConnectionFactory}.
     *
     * @return the created {@link ChannelHandler}
     * @throws InterruptedException if the channel was interrupted
     */
    public Channel build() throws InterruptedException {
        Bootstrap b = new Bootstrap();

        b.group(eventGroup).channel(NioSocketChannel.class).handler(new LoggingHandler(LogLevel.INFO))
                .handler(defaultInitializer());

        if (initializer != null) {
            b.handler(initializer);
        }

        Channel ch = b.connect(uri.getHost(), webSocketPort(uri)).sync().channel();

        ch.closeFuture().addListener(future -> {
            LOG.debug("OutboundConnection for {} was closed.", uri, future.cause());
            shutdownProcedure.run();
        });

        return ch;
    }

    /*
     * The default initializer for the {@link OutboundConnectionFactory}.
     */
    private DefaultSessionInitializer defaultInitializer() {
        return new SuperPeerClientChannelInitializer(FlushConsolidationHandler.DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES, //NOSONAR
                idleTimeout, idleRetries, uri, channelReadyFuture) {
            @Override
            protected void pojoMarshalStage(ChannelPipeline pipeline) {
                // From String to Message
                pipeline.addLast(MESSAGE_DECODER, MessageDecoder.INSTANCE);
                pipeline.addLast(MESSAGE_ENCODER, MessageEncoder.INSTANCE);
            }

            @Override
            protected void afterPojoMarshalStage(ChannelPipeline pipeline) {
                pipeline.addLast(SignatureHandler.SIGNATURE_HANDLER, new SignatureHandler(identity));
            }

            @Override
            protected void idleStage(ChannelPipeline pipeline) {
                if (pingPong) {
                    super.idleStage(pipeline);
                }
            }

            @Override
            protected void customStage(ChannelPipeline pipeline) {
                handler.forEach(pipeline::addLast);
            }

            @Override
            protected void exceptionStage(ChannelPipeline pipeline) {
                // Catch Errors
                pipeline.addLast(EXCEPTION_HANDLER, new ExceptionHandler(false));
            }

            @Override
            protected SslHandler generateSslContext(SocketChannel ch) throws SuperPeerClientException {
                if (ssl) {
                    try {
                        if (sslCtx == null) {
                            sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                                    .protocols(sslProtocols).build();
                        }
                        return sslCtx.newHandler(ch.alloc(), uri.getHost(), uri.getPort());
                    }
                    catch (SSLException e) {
                        throw new SuperPeerClientException(e);
                    }
                }
                return null;
            }
        };
    }

    public CompletableFuture<Void> getChannelReadyFuture() {
        return channelReadyFuture;
    }
}
