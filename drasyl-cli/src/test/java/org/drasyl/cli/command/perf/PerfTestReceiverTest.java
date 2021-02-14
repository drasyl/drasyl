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

import com.google.common.primitives.Longs;
import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylNode;
import org.drasyl.behaviour.Behavior;
import org.drasyl.behaviour.DeferredBehavior;
import org.drasyl.cli.command.perf.PerfTestReceiver.CheckTestStatus;
import org.drasyl.cli.command.perf.PerfTestReceiver.ResultsReplied;
import org.drasyl.cli.command.perf.message.SessionRequest;
import org.drasyl.cli.command.perf.message.TestResults;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.ArrayUtil;
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
import static org.drasyl.cli.command.perf.PerfTestSender.PROBE_HEADER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfTestReceiverTest {
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private SessionRequest session;
    @Mock(answer = RETURNS_DEEP_STUBS)
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
        private PerfTestReceiver receiver;

        @BeforeEach
        void setUp() {
            receiver = spy(new PerfTestReceiver(sender, session, scheduler, printStream, sendMethod, successBehavior, failureBehavior, currentTimeSupplier));
        }

        @Test
        void shouldHandleProbeMessage(@Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent probeMessageEvent) {
            when(probeMessageEvent.getPayload()).thenReturn(probeMessage(0));
            when(probeMessageEvent.getSender()).thenReturn(sender);
            when(currentTimeSupplier.getAsLong()).thenReturn(1_000_000_000L);

            final Behavior behavior = ((DeferredBehavior) receiver.run()).apply(node);
            behavior.receive(probeMessageEvent);

            // message handled?
            verify(receiver).handleProbeMessage(any(), any(), any(), any());

            // output?
            final String output = outputStream.toString();
            assertThat(output, containsString("Interval"));
            System.out.println(output);
        }

        @Test
        void shouldHandleTestResults(@Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent probeMessageEvent,
                                     @Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent resultsMessageEvent,
                                     @Mock(answer = RETURNS_DEEP_STUBS) final CheckTestStatus checkTestStatus,
                                     @Mock final ResultsReplied resultsReplied,
                                     @Mock final TestResults testResults,
                                     @Mock final Behavior newBehavior) {
            when(probeMessageEvent.getPayload()).thenReturn(probeMessage(0), probeMessage(2), probeMessage(1));
            when(probeMessageEvent.getSender()).thenReturn(sender);
            when(resultsMessageEvent.getPayload()).thenReturn(testResults);
            when(resultsMessageEvent.getSender()).thenReturn(sender);
            when(sendMethod.apply(any(), any())).thenReturn(completedFuture(null));
            when(successBehavior.get()).thenReturn(newBehavior);
            when(currentTimeSupplier.getAsLong()).thenReturn(1_000_000_000L, 1_100_000_000L, 2_000_000_000L, 2_000_000_000L, 3_000_000_000L);
            when(testResults.getTotalMessages()).thenReturn(4L);

            Behavior behavior = ((DeferredBehavior) receiver.run()).apply(node);
            behavior.receive(probeMessageEvent); // same
            behavior.receive(probeMessageEvent); // same
            behavior.receive(checkTestStatus); // same
            behavior.receive(probeMessageEvent); // same
            behavior = ((DeferredBehavior) behavior.receive(resultsMessageEvent)).apply(node);
            behavior.receive(resultsReplied);

            // results ?
            verify(sendMethod).apply(eq(sender), any(TestResults.class));

            // output?
            final String output = outputStream.toString();
            System.out.println(output);
            assertThat(output, containsString("Interval"));
            assertThat(output, containsString("0.00 -"));
            assertThat(output, containsString("1.00 sec"));
            assertThat(output, containsString("2.00 sec"));
            assertThat(output, containsString("32 B"));
            assertThat(output, containsString("16 B"));
            assertThat(output, containsString("0/2 (0.00%)"));
            assertThat(output, containsString("0/1 (0.00%)"));
            assertThat(output, containsString("0/1 (0.00%)"));
            assertThat(output, containsString("Sender:"));
            assertThat(output, containsString("Receiver:"));
            assertThat(output, containsString("64 B"));
            assertThat(output, containsString("1/4 (25.00%)"));
        }

        @Test
        void shouldTimeoutWhenSenderStopSendingMessages(@Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent probeMessageEvent,
                                                        @Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent resultsMessageEvent,
                                                        @Mock(answer = RETURNS_DEEP_STUBS) final CheckTestStatus checkTestStatus,
                                                        @Mock final Behavior newBehavior) {
            when(probeMessageEvent.getPayload()).thenReturn(probeMessage(0));
            when(probeMessageEvent.getSender()).thenReturn(sender);
            when(resultsMessageEvent.getSender()).thenReturn(sender);
            when(currentTimeSupplier.getAsLong()).thenReturn(1_000_000_000L, 1_000_000_000L, 100_000_000_000L);
            when(failureBehavior.apply(any())).thenReturn(newBehavior);

            final Behavior behavior = ((DeferredBehavior) receiver.run()).apply(node);
            behavior.receive(probeMessageEvent);
            behavior.receive(checkTestStatus);

            // failed?
            verify(failureBehavior).apply(any());

            final String output = outputStream.toString();
            assertThat(output, containsString("No message received for 99.00s"));
        }
    }

    private static byte[] probeMessage(final long messageNo) {
        return ArrayUtil.concat(PROBE_HEADER, Longs.toByteArray(messageNo));
    }
}
