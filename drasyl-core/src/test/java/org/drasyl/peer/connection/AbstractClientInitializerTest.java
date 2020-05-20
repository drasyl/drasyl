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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.ssl.SslHandler;
import org.drasyl.peer.connection.handler.WebSocketHandshakeClientHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AbstractClientInitializerTest {
    private int flushBufferSize;
    private Duration readIdleTimeout;
    private short pingPongRetries;
    private int maxContentLength;
    private URI ipAddress;
    private CompletableFuture<Void> channelReadyFuture;
    private ChannelPipeline pipeline;

    @BeforeEach
    void setUp() throws URISyntaxException {
        flushBufferSize = FlushConsolidationHandler.DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES;
        readIdleTimeout = Duration.ofSeconds(2);
        pingPongRetries = 3;
        maxContentLength = 65536;
        ipAddress = new URI("ws://localhost:22527/");
        channelReadyFuture = mock(CompletableFuture.class);
        pipeline = mock(ChannelPipeline.class);

        when(pipeline.get("sslHandler")).thenReturn(mock(ChannelHandler.class));
    }

    @Test
    void beforeMarshalStage() throws URISyntaxException {
        AbstractClientInitializer initializer = new AbstractClientInitializer(flushBufferSize, readIdleTimeout, pingPongRetries,
                ipAddress, channelReadyFuture) {
            @Override
            protected void customStage(ChannelPipeline pipeline) {

            }

            @Override
            protected SslHandler generateSslContext(SocketChannel ch) {
                return null;
            }
        };

        initializer.beforeMarshalStage(pipeline);

        verify(pipeline).addLast(any(HttpClientCodec.class), any(HttpObjectAggregator.class),
                any(WebSocketHandshakeClientHandler.class));
    }

    @Test
    void exceptionOnInvalidTarget() {
        assertThrows(URISyntaxException.class, () -> {
            AbstractClientInitializer initializer = new AbstractClientInitializer(flushBufferSize, readIdleTimeout, pingPongRetries,
                    new URI("|<>:22527"), channelReadyFuture) {
                @Override
                protected void customStage(ChannelPipeline pipeline) {

                }

                @Override
                protected SslHandler generateSslContext(SocketChannel ch) {
                    return null;
                }
            };
            initializer.beforeMarshalStage(pipeline);
        });

        verify(pipeline, never()).addLast(any(HttpClientCodec.class), any(HttpObjectAggregator.class),
                any(WebSocketHandshakeClientHandler.class));
    }
}