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
import io.netty.util.concurrent.Future;
import org.drasyl.cli.perf.handler.PerfSessionRequestorHandler.PerfSessionRequestRejectedException;
import org.drasyl.cli.perf.handler.PerfSessionRequestorHandler.PerfSessionRequestTimeoutException;
import org.drasyl.cli.perf.message.Noop;
import org.drasyl.cli.perf.message.SessionConfirmation;
import org.drasyl.cli.perf.message.SessionRejection;
import org.drasyl.cli.perf.message.SessionRequest;
import org.drasyl.handler.discovery.AddPathEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintStream;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfSessionRequestorHandlerTest {
    @Test
    void shouldRequestNonDirectSessionOnChannelActive(@Mock final PrintStream out,
                                                      @Mock final SessionRequest request) {
        final ChannelHandler handler = new PerfSessionRequestorHandler(out, request, 1, false);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        assertEquals(request, channel.readOutbound());
    }

    @Test
    void shouldRequestDirectSessionOnDirectConnection(@Mock final PrintStream out,
                                                      @Mock final SessionRequest request,
                                                      @Mock final AddPathEvent pathEvent) {
        final ChannelHandler handler = new PerfSessionRequestorHandler(out, request, 1, true);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        assertThat(channel.readOutbound(), instanceOf(Noop.class));

        channel.pipeline().fireUserEventTriggered(pathEvent);
        assertEquals(request, channel.readOutbound());
    }

    @Test
    void shouldThrowExceptionOnTimeout(@Mock final PrintStream out,
                                       @Mock final SessionRequest request) {
        final ChannelHandler handler = new PerfSessionRequestorHandler(out, request, 1, false);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        await().untilAsserted(() -> {
            channel.runScheduledPendingTasks();
            assertThrows(PerfSessionRequestTimeoutException.class, () -> channel.checkException());
        });
    }

    @Test
    void shouldStartSessionOnRequestConfirmationWithSenderForNormalSession(@Mock final PrintStream out,
                                                                           @Mock final SessionRequest request,
                                                                           @Mock final Future<?> timeoutTask,
                                                                           @Mock final SessionConfirmation confirmation) {
        final ChannelHandler handler = new PerfSessionRequestorHandler(out, request, 1, true, timeoutTask, true, false, false);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(confirmation);

        assertNull(channel.pipeline().get(PerfSessionRequestorHandler.class));
        assertNotNull(channel.pipeline().get(PerfSessionSenderHandler.class));
        verify(timeoutTask).cancel(false);
    }

    @Test
    void shouldStartSessionOnRequestConfirmationWithReceiverForReverseSession(@Mock final PrintStream out,
                                                                              @Mock final SessionRequest request,
                                                                              @Mock final Future<?> timeoutTask,
                                                                              @Mock final SessionConfirmation confirmation) {
        when(request.isReverse()).thenReturn(true);

        final ChannelHandler handler = new PerfSessionRequestorHandler(out, request, 1, true, timeoutTask, true, false, false);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(confirmation);

        assertNull(channel.pipeline().get(PerfSessionRequestorHandler.class));
        assertNotNull(channel.pipeline().get(PerfSessionReceiverHandler.class));
        verify(timeoutTask).cancel(false);
    }

    @Test
    void shouldThrowExceptionOnRejection(@Mock final PrintStream out,
                                         @Mock final SessionRequest request,
                                         @Mock final Future<?> timeoutTask,
                                         @Mock final SessionRejection rejection) {
        final ChannelHandler handler = new PerfSessionRequestorHandler(out, request, 1, true, timeoutTask, true, false, false);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        assertThrows(PerfSessionRequestRejectedException.class, () -> channel.writeInbound(rejection));
        verify(timeoutTask).cancel(false);
    }
}
