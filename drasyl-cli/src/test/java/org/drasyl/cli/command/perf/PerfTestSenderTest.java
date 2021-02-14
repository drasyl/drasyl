/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.cli.command.perf;

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylNode;
import org.drasyl.behaviour.Behavior;
import org.drasyl.behaviour.DeferredBehavior;
import org.drasyl.cli.command.perf.PerfTestSender.TestCompleted;
import org.drasyl.cli.command.perf.message.SessionRequest;
import org.drasyl.cli.command.perf.message.TestResults;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.completedFuture;
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
    private CompressedPublicKey receiver;
    @Mock
    private SessionRequest session;
    @Mock
    private Scheduler scheduler;
    private ByteArrayOutputStream outputStream;
    private PrintStream printStream;
    @Mock
    BiFunction<CompressedPublicKey, Object, CompletableFuture<Void>> sendMethod;
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
            when(session.getMps()).thenReturn(1);
            when(session.getSize()).thenReturn(1);
            when(session.getTime()).thenReturn(3);
            when(scheduler.scheduleDirect(any())).then(invocation -> {
                final Runnable runnable = invocation.getArgument(0, Runnable.class);
                runnable.run();
                return null;
            });
            when(sendMethod.apply(any(), any())).thenReturn(completedFuture(null));
            when(currentTimeSupplier.getAsLong()).thenReturn(1_000_000_000L, 1_000_000_000L, 1_500_000_000L, 2_000_000_000L, 3_000_000_000L, 4_000_000_000L);

            final PerfTestSender sender = spy(new PerfTestSender(receiver, session, scheduler, printStream, sendMethod, successBehavior, failureBehavior, currentTimeSupplier));
            final Behavior behavior = ((DeferredBehavior) sender.run()).apply(node);
            behavior.receive(testCompleted);

            // messages sent?
            verify(sendMethod, times(3)).apply(any(CompressedPublicKey.class), any(byte[].class));

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
            final PerfTestSender sender = new PerfTestSender(receiver, session, scheduler, printStream, sendMethod, successBehavior, failureBehavior, currentTimeSupplier);
            ((DeferredBehavior) sender.completing(results, (short) 1)).apply(node);

            verify(sendMethod).apply(receiver, results);
        }

        @Test
        void shouldPrintResultsOnResponse(@Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent message,
                                          @Mock final Behavior newBehavior) {
            when(message.getPayload()).thenReturn(results);
            when(message.getSender()).thenReturn(receiver);
            when(successBehavior.get()).thenReturn(newBehavior);

            final PerfTestSender sender = new PerfTestSender(receiver, session, scheduler, printStream, sendMethod, successBehavior, failureBehavior, currentTimeSupplier);
            final Behavior behavior = ((DeferredBehavior) sender.completing(results, (short) 1)).apply(node);
            behavior.receive(message);

            // output?
            final String output = outputStream.toString();
            assertThat(output, containsString("Receiver:"));
        }

        @Test
        void shouldFailWhenRetriesAreExhausted(@Mock final Behavior newBehavior) {
            when(failureBehavior.apply(any())).thenReturn(newBehavior);

            final PerfTestSender sender = new PerfTestSender(receiver, session, scheduler, printStream, sendMethod, successBehavior, failureBehavior, currentTimeSupplier);
            sender.completing(results, (short) 0);

            verify(failureBehavior).apply(any());
        }
    }
}
