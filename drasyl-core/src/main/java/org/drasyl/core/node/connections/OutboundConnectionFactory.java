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
package org.drasyl.core.node.connections;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.drasyl.core.common.handler.ClientInitializer;
import org.drasyl.core.common.handler.DefaultSessionInitializer;
import org.drasyl.core.common.handler.ExceptionHandler;
import org.drasyl.core.common.handler.codec.message.MessageDecoder;
import org.drasyl.core.common.handler.codec.message.MessageEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.core.common.handler.ExceptionHandler.EXCEPTION_HANDLER;
import static org.drasyl.core.common.handler.codec.message.MessageDecoder.MESSAGE_DECODER;
import static org.drasyl.core.common.handler.codec.message.MessageEncoder.MESSAGE_ENCODER;

/**
 * This factory produces outbound netty connections.
 */
@SuppressWarnings({ "squid:S3776", "squid:S00107" })
public class OutboundConnectionFactory {
    private static final Logger LOG = LoggerFactory.getLogger(OutboundConnectionFactory.class);
    private final CompletableFuture<Void> channelReadyFuture;
    private final List<ChannelHandler> handler;
    private Runnable shutdownProcedure;
    private ChannelHandler initializer;
    private EventLoopGroup eventGroup;
    private final List<String> sslProtocols;
    private boolean pingPong = true;
    private SslContext sslCtx;
    private final URI uri;
    private Duration idleTimeout;
    private int idleRetries;
    private boolean ssl;
    private int maxContentLength;
    private boolean relayToRelayCon;

    private OutboundConnectionFactory(URI target,
                                      ChannelInitializer<SocketChannel> initializer,
                                      Runnable shutdownProcedure,
                                      SslContext sslCtx,
                                      List<ChannelHandler> handler,
                                      List<String> sslProtocols,
                                      EventLoopGroup eventGroup,
                                      int maxContentLength,
                                      boolean relayToRelayCon) {
        this.uri = target;
        this.initializer = initializer;
        this.shutdownProcedure = shutdownProcedure;
        this.sslCtx = sslCtx;
        this.handler = handler;
        this.sslProtocols = sslProtocols;
        this.eventGroup = eventGroup;
        this.maxContentLength = maxContentLength;
        this.channelReadyFuture = new CompletableFuture<>();
        this.relayToRelayCon = relayToRelayCon;
    }

    /**
     * Produces an {@link OutboundConnectionFactory} with the given {@link URI} as target.
     *
     * @param target the IP address of the target system
     */
    public OutboundConnectionFactory(URI target, EventLoopGroup eventGroup) {
        this(target, null, () -> {
        }, null, new ArrayList<>(), Collections.singletonList("TLSv1.3"), eventGroup, 1000000, false);
    }

    /**
     * Sets the max content length for a web frame.
     *
     * @param maxContentLength max content length
     * @return {@link OutboundConnectionFactory} with the changed property
     */
    public OutboundConnectionFactory maxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;

        return this;
    }

    /**
     * Sets init this {@link OutboundConnectionFactory} as relay to relay connection.
     *
     * @param relayToRelayCon if true this is a relay to relay connection
     * @return {@link OutboundConnectionFactory} with the changed property
     */
    public OutboundConnectionFactory relayCon(boolean relayToRelayCon) {
        this.relayToRelayCon = relayToRelayCon;

        return this;
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
     * Adds an event group to the outbound connection.
     *
     * @param eventGroup the event group
     * @return {@link OutboundConnectionFactory} with the changed property
     */
    public OutboundConnectionFactory eventGroup(EventLoopGroup eventGroup) {
        this.eventGroup = eventGroup;

        return this;
    }

    /**
     * Adds a list of supported SSL protocols.
     *
     * @param protocols a list of supported protocols
     * @return {@link OutboundConnectionFactory} with the changed property
     */
    public OutboundConnectionFactory sslProtocols(String... protocols) {
        this.sslProtocols.addAll(Arrays.asList(protocols));

        return this;
    }

    /**
     * Adds a list of supported SSL protocols.
     *
     * @param protocols a list of supported protocols
     * @return {@link OutboundConnectionFactory} with the changed property
     */
    public OutboundConnectionFactory sslProtocols(List<String> protocols) {
        this.sslProtocols.addAll(protocols);

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
    public OutboundConnectionFactory idleRetries(int retries) {
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
     * Sets a {@link SslContext}.
     *
     * @param sslCtx the ssl context
     * @return {@link OutboundConnectionFactory} with the changed property
     */
    public OutboundConnectionFactory sslContext(SslContext sslCtx) {
        this.sslCtx = sslCtx;

        return this;
    }

    /**
     * Adds the given {@link Runnable} to the close listener.
     *
     * @param procedure the procedure
     * @return {@link OutboundConnectionFactory} with the changed property
     */
    public OutboundConnectionFactory shutdownProcedure(Runnable procedure) {
        this.shutdownProcedure = procedure;

        return this;
    }

    /**
     * Replaced the default initializer with this one.
     *
     * @param initializer the new initializer
     * @return {@link OutboundConnectionFactory} with the changed property
     */
    public OutboundConnectionFactory initializer(ChannelHandler initializer) {
        this.initializer = initializer;

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

        int port = uri.getPort();
        if (port == -1 && uri.getScheme() != null) {
            if (uri.getScheme().equals("ws")) {
                port = 80;
            }
            else if (uri.getScheme().equals("wss")) {
                port = 443;
            }
        }

        Channel ch = b.connect(uri.getHost(), port).sync().channel();

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
        return new ClientInitializer(FlushConsolidationHandler.DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES, //NOSONAR
                idleTimeout, idleRetries, maxContentLength, uri, channelReadyFuture) {
            @Override
            protected SslHandler generateSslContext(SocketChannel ch) {
                if (ssl) {
                    try {
                        if (sslCtx == null) {
                            sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                                    .protocols(sslProtocols).build();
                        }
                        return sslCtx.newHandler(ch.alloc(), uri.getHost(), uri.getPort());
                    }
                    catch (SSLException e) {
                        LOG.error("SSLException: ", e);
                    }
                }
                return null;
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
            protected void pojoMarshalStage(ChannelPipeline pipeline) {
                // From String to Message
                pipeline.addLast(MESSAGE_DECODER, MessageDecoder.INSTANCE);

                pipeline.addLast(MESSAGE_ENCODER, MessageEncoder.INSTANCE);
            }
        };
    }

    public CompletableFuture<Void> getChannelReadyFuture() {
        return channelReadyFuture;
    }
}
