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
package org.drasyl.peer.connection;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import org.drasyl.peer.connection.handler.WebSocketHandshakeClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Creates a newly configured {@link ChannelPipeline} for a ClientConnection to a node server.
 */
@SuppressWarnings("java:S4818")
public abstract class AbstractClientInitializer extends DefaultSessionInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractClientInitializer.class);
    protected final URI target;
    private final CompletableFuture<Void> channelReadyFuture;

    /**
     * Initialize a netty Channel for an outbound connection to a node server.
     *
     * @param flushBufferSize The size of the flush buffer, to minimize IO overhead. A high value is
     *                        good for throughput. A low value is good for latency.
     * @param readIdleTimeout The maximum time that an active connection can spend in idle before
     *                        the client checks with a PING request whether the remote station is
     *                        still alive. Note: every long value <= 0 s deactivates the idle
     *                        function.
     * @param pingPongRetries The maximum amount that a remote station cannot reply to a PING
     *                        request in succession in the interval {@code readIdleTimeout}. Min
     *                        value is 1, max 32767
     * @param target          the target URI
     */
    public AbstractClientInitializer(int flushBufferSize,
                                     Duration readIdleTimeout,
                                     short pingPongRetries,
                                     URI target) {
        this(flushBufferSize, readIdleTimeout, pingPongRetries,
                target, new CompletableFuture<>());
    }

    protected AbstractClientInitializer(int flushBufferSize,
                                        Duration readIdleTimeout,
                                        short pingPongRetries,
                                        URI target,
                                        CompletableFuture<Void> channelReadyFuture) {
        super(flushBufferSize, readIdleTimeout, pingPongRetries);
        this.channelReadyFuture = channelReadyFuture;
        this.target = target;
    }

    @Override
    protected void beforeMarshalStage(ChannelPipeline pipeline) {
        WebSocketHandshakeClientHandler webSocketHandshakeClientHandler =
                new WebSocketHandshakeClientHandler(WebSocketClientHandshakerFactory.newHandshaker(target,
                        WebSocketVersion.V13, null, false, new DefaultHttpHeaders()));
        webSocketHandshakeClientHandler.handshakeFuture().whenComplete((v, cause) -> {
            if (cause != null) {
                channelReadyFuture.completeExceptionally(cause);
            }
            else {
                channelReadyFuture.complete(null);
                LOG.debug("Client WebSocket created for {}", target);
            }
        });

        pipeline.addLast(new HttpClientCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));
        pipeline.addLast(webSocketHandshakeClientHandler);
    }

    /**
     * A future is returned if the handshake was successful and the channel is ready to receive and
     * send messages.
     * <p>
     * The future may fail if a connection could not be established.
     */
    public CompletableFuture<Void> connectedFuture() {
        return channelReadyFuture;
    }
}
