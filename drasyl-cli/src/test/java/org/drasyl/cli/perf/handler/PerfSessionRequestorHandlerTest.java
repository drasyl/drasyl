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
import org.drasyl.cli.perf.handler.PerfSessionRequestorHandler.PerfSessionRequestTimeoutException;
import org.drasyl.cli.perf.message.SessionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintStream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class PerfSessionRequestorHandlerTest {
    @Test
    void shouldRequestNonDirectSessionOnChannelActive(@Mock final PrintStream out,
                                                      @Mock final SessionRequest request) {
        final ChannelHandler handler = new PerfSessionRequestorHandler(out, request, 1, false);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        try {
            assertEquals(request, channel.readOutbound());
        }
        finally {
            channel.close();
        }
    }

    @Test
    void shouldThrowExceptionOnTimeout(@Mock final PrintStream out,
                                       @Mock final SessionRequest request) {
        final ChannelHandler handler = new PerfSessionRequestorHandler(out, request, 1, false);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        try {
            await().untilAsserted(() -> {
                channel.runScheduledPendingTasks();
                assertThrows(PerfSessionRequestTimeoutException.class, channel::checkException);
            });
        }
        finally {
            channel.close();
        }
    }
}
