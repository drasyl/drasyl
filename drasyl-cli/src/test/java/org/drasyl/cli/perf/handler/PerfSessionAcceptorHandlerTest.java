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
package org.drasyl.cli.perf.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.cli.perf.message.SessionConfirmation;
import org.drasyl.cli.perf.message.SessionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfSessionAcceptorHandlerTest {
    @Test
    void shouldConfirmSessionAndReplaceItselfWithReceiverForNormalSessions(@Mock final PrintStream printStream,
                                                                           @Mock final SessionRequest request) {
        final ChannelHandler handler = new PerfSessionAcceptorHandler(printStream);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(request);

        assertThat(channel.readOutbound(), instanceOf(SessionConfirmation.class));
        assertNull(channel.pipeline().get(PerfSessionAcceptorHandler.class));
        assertNotNull(channel.pipeline().get(PerfSessionReceiverHandler.class));
    }

    @Test
    void shouldConfirmSessionAndReplaceItselfWithSenderForReverseSessions(@Mock final PrintStream printStream,
                                                                          @Mock final SessionRequest request) {
        when(request.isReverse()).thenReturn(true);

        final ChannelHandler handler = new PerfSessionAcceptorHandler(printStream);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(request);

        assertThat(channel.readOutbound(), instanceOf(SessionConfirmation.class));
        assertNull(channel.pipeline().get(PerfSessionAcceptorHandler.class));
        assertNotNull(channel.pipeline().get(PerfSessionSenderHandler.class));
    }
}
