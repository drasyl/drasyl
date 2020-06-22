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
import io.netty.handler.stream.ChunkedWriteHandler;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.DefaultSessionInitializer;
import org.drasyl.peer.connection.handler.ExceptionHandler;
import org.drasyl.peer.connection.handler.MessageDecoder;
import org.drasyl.peer.connection.handler.MessageEncoder;
import org.drasyl.peer.connection.handler.SignatureHandler;
import org.drasyl.peer.connection.handler.stream.ChunkedMessageHandler;
import org.drasyl.peer.connection.superpeer.SuperPeerClientChannelInitializer;
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

import static org.drasyl.peer.connection.handler.ExceptionHandler.EXCEPTION_HANDLER;
import static org.drasyl.peer.connection.handler.MessageDecoder.MESSAGE_DECODER;
import static org.drasyl.peer.connection.handler.MessageEncoder.MESSAGE_ENCODER;
import static org.drasyl.peer.connection.handler.stream.ChunkedMessageHandler.CHUNK_HANDLER;
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
    private Duration transferTimeout;
    private short idleRetries;
    private boolean ssl;
    private int maxContentLength;
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
        }, null, new ArrayList<>(), Collections.singletonList("TLSv1.3"), eventGroup, 1000000, identity, Duration.ofSeconds(60));
    }

    private OutboundConnectionFactory(URI target,
                                      ChannelInitializer<SocketChannel> initializer,
                                      Runnable shutdownProcedure,
                                      SslContext sslCtx,
                                      List<ChannelHandler> handler,
                                      List<String> sslProtocols,
                                      EventLoopGroup eventGroup,
                                      int maxContentLength,
                                      Identity identity,
                                      Duration transferTimeout) {
        this.uri = target;
        this.initializer = initializer;
        this.shutdownProcedure = shutdownProcedure;
        this.sslCtx = sslCtx;
        this.handler = handler;
        this.sslProtocols = sslProtocols;
        this.eventGroup = eventGroup;
        this.maxContentLength = maxContentLength;
        this.channelReadyFuture = new CompletableFuture<>();
        this.identity = identity;
        this.transferTimeout = transferTimeout;
    }

    /**
     * Sets the transferTimeout for a composed message.
     *
     * @param transferTimeout transfer timeout
     * @return {@link OutboundConnectionFactory} with the changed property
     */
    public OutboundConnectionFactory transferTimeout(Duration transferTimeout) {
        this.transferTimeout = transferTimeout;

        return this;
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
                pipeline.addLast("streamer", new ChunkedWriteHandler());
                pipeline.addLast(CHUNK_HANDLER, new ChunkedMessageHandler(maxContentLength, identity.getPublicKey(), transferTimeout));
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
        };
    }

    public CompletableFuture<Void> getChannelReadyFuture() {
        return channelReadyFuture;
    }
}
