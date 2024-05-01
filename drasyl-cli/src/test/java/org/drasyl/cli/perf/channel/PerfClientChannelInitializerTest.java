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
package org.drasyl.cli.perf.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Worm;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfClientChannelInitializerTest {
    @Nested
    class InitChannel {
        @Test
        void shouldAddAllRequiredHandlers(@Mock(answer = RETURNS_DEEP_STUBS) final PrintStream err,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final Worm<Integer> exitCode,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey server,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final DrasylServerChannel channel,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey publicKey,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress inetAddress) throws Exception {
            when(ctx.channel()).thenReturn(channel);
            when(channel.config().getSuperPeers()).thenReturn(Map.of(publicKey, inetAddress));

            final ChannelInboundHandler handler = new PerfClientChannelInitializer(1, err, exitCode, server);
            handler.channelRegistered(ctx);

            verify(channel.pipeline(), times(8)).addLast(any());
        }
    }
}
