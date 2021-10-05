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
package org.drasyl.cli.command.perf;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import org.drasyl.DrasylNode;
import org.drasyl.behaviour.Behavior;
import org.drasyl.behaviour.DeferredBehavior;
import org.drasyl.cli.command.perf.PerfTestSender.TestCompleted;
import org.drasyl.cli.command.perf.message.Probe;
import org.drasyl.cli.command.perf.message.SessionRequest;
import org.drasyl.cli.command.perf.message.TestResults;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfTestSenderTest {
    @Mock
    private IdentityPublicKey receiver;
    @Mock
    private SessionRequest session;
    @Mock
    private EventLoopGroup eventLoopGroup;
    private ByteArrayOutputStream outputStream;
    private PrintStream printStream;
    @Mock(answer = RETURNS_DEEP_STUBS)
    Channel channel;
    @Mock
    Supplier<Behavior> successBehavior;
    @Mock
    Function<Exception, Behavior> failureBehavior;
    @Mock
    LongSupplier currentTimeSupplier;
    @Mock
    DrasylNode node;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        printStream = new PrintStream(outputStream, true);
    }

    @Nested
    class Run {
        @Test
        void shouldRunTest(@Mock(answer = RETURNS_DEEP_STUBS) final TestCompleted testCompleted) {
            when(channel.isWritable()).thenReturn(true);
            when(session.getMps()).thenReturn(1);
            when(session.getSize()).thenReturn(1);
            when(session.getTime()).thenReturn(3);
            when(eventLoopGroup.submit(any(Runnable.class))).then(invocation -> {
                final Runnable runnable = invocation.getArgument(0, Runnable.class);
                runnable.run();
                return null;
            });
            when(currentTimeSupplier.getAsLong()).thenReturn(1_000_000_000L, 1_000_000_000L, 1_500_000_000L, 2_000_000_000L, 3_000_000_000L, 4_000_000_000L);

            final PerfTestSender sender = spy(new PerfTestSender(session, eventLoopGroup, printStream, channel, successBehavior, failureBehavior, currentTimeSupplier));
            final Behavior behavior = ((DeferredBehavior) sender.run()).apply(node);
            behavior.receive(testCompleted);

            // messages sent?
            verify(channel, times(3)).writeAndFlush(any(Probe.class));

            // output?
            final String output = outputStream.toString();
            assertThat(output, containsString("Interval"));
            assertThat(output, containsString("0.00 -"));
            assertThat(output, containsString("1.00 sec"));
            assertThat(output, containsString("2.00 sec"));
            assertThat(output, containsString("3.00 sec"));
            assertThat(output, containsString("17 B"));
            assertThat(output, containsString("51 B"));
            assertThat(output, containsString("0/1 (0.00%)"));
            assertThat(output, containsString("0/3 (0.00%)"));
            assertThat(output, containsString("Sender:"));

            // completing test?
            verify(sender).completing(testCompleted.getTotalResults(), (short) 10);
        }
    }

    @Nested
    class Completing {
        @Mock
        private TestResults results;

        @Test
        void shouldSendResultsWhenRetriesAreNotExhausted() {
            final PerfTestSender sender = new PerfTestSender(session, eventLoopGroup, printStream, channel, successBehavior, failureBehavior, currentTimeSupplier);
            ((DeferredBehavior) sender.completing(results, (short) 1)).apply(node);

            verify(channel).writeAndFlush(results);
        }

        @Test
        void shouldPrintResultsOnResponse(@Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent message,
                                          @Mock final Behavior newBehavior) {
            when(channel.remoteAddress()).thenReturn(receiver);
            when(message.getPayload()).thenReturn(results);
            when(message.getSender()).thenReturn(receiver);
            when(successBehavior.get()).thenReturn(newBehavior);

            final PerfTestSender sender = new PerfTestSender(session, eventLoopGroup, printStream, channel, successBehavior, failureBehavior, currentTimeSupplier);
            final Behavior behavior = ((DeferredBehavior) sender.completing(results, (short) 1)).apply(node);
            behavior.receive(message);

            // output?
            final String output = outputStream.toString();
            assertThat(output, containsString("Receiver:"));
        }

        @Test
        void shouldFailWhenRetriesAreExhausted(@Mock final Behavior newBehavior) {
            when(failureBehavior.apply(any())).thenReturn(newBehavior);

            final PerfTestSender sender = new PerfTestSender(session, eventLoopGroup, printStream, channel, successBehavior, failureBehavior, currentTimeSupplier);
            sender.completing(results, (short) 0);

            verify(failureBehavior).apply(any());
        }
    }
}
