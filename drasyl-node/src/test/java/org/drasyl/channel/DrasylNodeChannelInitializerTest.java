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
import io.netty.channel.group.DefaultChannelGroup;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.time.Duration.ofSeconds;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@ExtendWith(MockitoExtension.class)
class DrasylNodeChannelInitializerTest {
    @Nested
    class InitChannel {
        @Test
        void shouldAddAllRequiredHandlers(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylConfig config,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final DrasylNode node,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final DrasylChannel channel) throws Exception {
            node.channels = mock(DefaultChannelGroup.class);
            when(config.getRemoteMessageMtu()).thenReturn(1_000);
            when(config.getRemoteMessageMaxContentLength()).thenReturn(2_000);
            when(config.getRemoteMessageComposedMessageTransferTimeout()).thenReturn(ofSeconds(10));
            when(config.isRemoteMessageArmApplicationEnabled()).thenReturn(true);
            when(config.getRemoteMessageArmApplicationAgreementMaxCount()).thenReturn(100);
            when(ctx.channel()).thenReturn(channel);
            when(channel.localAddress0()).thenReturn(ID_1);
            when(channel.remoteAddress0()).thenReturn(ID_2.getIdentityPublicKey());

            final ChannelInitializer<DrasylChannel> handler = new DrasylNodeChannelInitializer(config, node);
            handler.channelRegistered(ctx);

            verify(channel.pipeline(), times(5)).addLast(any());
        }
    }
}
