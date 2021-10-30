/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import org.drasyl.handler.remote.protocol.HopCount;
import org.drasyl.identity.Identity;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.channel.DrasylNodeServerChannelInitializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static java.time.Duration.ofSeconds;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrasylNodeServerChannelInitializerTest {
    @Nested
    class InitChannel {
        @Test
        void shouldAddAllRequiredHandlers(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylConfig config,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final Identity identity,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final DrasylNode node,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final DrasylServerChannel channel) throws Exception {
            when(ctx.channel()).thenReturn(channel);
            when(config.isRemoteEnabled()).thenReturn(true);
            when(config.isRemoteExposeEnabled()).thenReturn(true);
            when(config.isRemoteTcpFallbackEnabled()).thenReturn(true);
            when(config.isRemoteLocalNetworkDiscoveryEnabled()).thenReturn(true);
            when(config.isRemoteMessageArmProtocolEnabled()).thenReturn(true);
            when(config.isRemoteLocalHostDiscoveryEnabled()).thenReturn(true);
            when(config.isIntraVmDiscoveryEnabled()).thenReturn(true);
            when(config.getRemotePingInterval()).thenReturn(ofSeconds(5L));
            when(config.getRemotePingTimeout()).thenReturn(ofSeconds(30L));
            when(config.getRemotePingInterval()).thenReturn(ofSeconds(5L));
            when(config.getRemoteUniteMinInterval()).thenReturn(ofSeconds(20L));

            final ChannelInitializer<DrasylServerChannel> handler = new DrasylNodeServerChannelInitializer(config, identity, node);
            handler.channelRegistered(ctx);

            verify(channel.pipeline(), times(20)).addLast(any());
        }
    }
}
