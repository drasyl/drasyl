/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.handler;

import org.drasyl.all.models.IPAddress;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientInitializerTest {
    private int flushBufferSize;
    private Duration readIdleTimeout;
    private int pingPongRetries;
    private int maxContentLength;
    private IPAddress ipAddress;
    private CompletableFuture<Void> channelReadyFuture;
    private ChannelPipeline pipeline;

    @BeforeEach
    void setUp() {
        flushBufferSize = FlushConsolidationHandler.DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES;
        readIdleTimeout = Duration.ofSeconds(2);
        pingPongRetries = 3;
        maxContentLength = 65536;
        ipAddress = new IPAddress("localhost", 22527);
        channelReadyFuture = mock(CompletableFuture.class);
        pipeline = mock(ChannelPipeline.class);

        when(pipeline.get("sslHandler")).thenReturn(mock(ChannelHandler.class));
    }

    @Test
    void beforeMarshalStage() {
        ClientInitializer initializer = new ClientInitializer(flushBufferSize, readIdleTimeout, pingPongRetries,
                maxContentLength, ipAddress, channelReadyFuture) {
            @Override
            protected SslHandler generateSslContext(SocketChannel ch) {
                return null;
            }

            @Override
            protected void customStage(ChannelPipeline pipeline) {

            }
        };

        initializer.beforeMarshalStage(pipeline);

        verify(pipeline, times(1)).addLast(any(HttpClientCodec.class), any(HttpObjectAggregator.class),
                any(WebSocketClientHandler.class));
    }

    @Test
    void exceptionOnInvalidTarget() {
        ClientInitializer initializer = new ClientInitializer(flushBufferSize, readIdleTimeout, pingPongRetries,
                maxContentLength, new IPAddress("|<>:22527"), channelReadyFuture) {
            @Override
            protected SslHandler generateSslContext(SocketChannel ch) {
                return null;
            }

            @Override
            protected void customStage(ChannelPipeline pipeline) {

            }
        };

        assertThrows(IllegalArgumentException.class, () -> initializer.beforeMarshalStage(pipeline));

        verify(pipeline, never()).addLast(any(HttpClientCodec.class), any(HttpObjectAggregator.class),
                any(WebSocketClientHandler.class));
        verify(channelReadyFuture, times(1)).completeExceptionally(any());
        assertEquals(channelReadyFuture, initializer.waitUntilConnected());
    }
}