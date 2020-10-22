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

import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.ssl.SslHandler;
import org.drasyl.identity.Identity;
import org.drasyl.peer.Endpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClientChannelInitializerTest {
    private int flushBufferSize;
    private Duration readIdleTimeout;
    private short pingPongRetries;
    private Endpoint endpoint;
    @Mock
    private ChannelPipeline pipeline;
    @Mock
    private Identity identity;

    @BeforeEach
    void setUp() {
        flushBufferSize = FlushConsolidationHandler.DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES;
        readIdleTimeout = Duration.ofSeconds(2);
        pingPongRetries = 3;
        endpoint = Endpoint.of("ws://localhost:22527/#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
    }

    @Test
    void beforeMarshalStage() {
        final ClientChannelInitializer initializer = new ClientChannelInitializer(identity, flushBufferSize, readIdleTimeout, pingPongRetries,
                endpoint) {
            @Override
            protected void customStage(final ChannelPipeline pipeline) {

            }

            @Override
            protected SslHandler generateSslContext(final SocketChannel ch) {
                return null;
            }
        };

        initializer.beforeMarshalStage(pipeline);

        verify(pipeline).addLast(any(HttpClientCodec.class));
        verify(pipeline).addLast(any(HttpObjectAggregator.class));
        verify(pipeline).addLast(any(WebSocketClientProtocolHandler.class));
    }
}