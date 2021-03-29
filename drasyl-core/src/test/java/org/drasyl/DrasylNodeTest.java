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
package org.drasyl;

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.annotation.NonNull;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrasylNodeTest {
    private final byte[] payload = new byte[]{ 0x4f };
    @Mock
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private PeersManager peersManager;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Pipeline pipeline;
    @Mock
    private PluginManager pluginManager;
    @Mock
    private Scheduler scheduler;

    @Nested
    class Start {
        @Test
        void shouldEmitUpEventOnSuccessfulStart() {
            when(scheduler.scheduleDirect(any())).then(invocation -> {
                final Runnable runnable = invocation.getArgument(0, Runnable.class);
                runnable.run();
                return null;
            });
            when(pipeline.processInbound(any())).thenReturn(completedFuture(null));

            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, pipeline, pluginManager, new AtomicReference<>(null), new AtomicReference<>(), scheduler) {
                @Override
                public void onEvent(final @NonNull Event event) {
                }
            });
            underTest.start();

            verify(underTest).onInternalEvent(NodeUpEvent.of(Node.of(identity)));
        }

        @Test
        void shouldEmitNodeUnrecoverableErrorEventOnFailedStart() {
            when(scheduler.scheduleDirect(any())).then(invocation -> {
                final Runnable runnable = invocation.getArgument(0, Runnable.class);
                runnable.run();
                return null;
            });
            when(pipeline.processInbound(any(NodeUpEvent.class))).thenReturn(failedFuture(new DrasylException("error")));
            when(pipeline.processInbound(any(NodeUnrecoverableErrorEvent.class))).thenReturn(completedFuture(null));

            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, pipeline, pluginManager, new AtomicReference<>(null), new AtomicReference<>(), scheduler) {
                @Override
                public void onEvent(final @NonNull Event event) {
                }
            });
            assertThrows(ExecutionException.class, underTest.start()::get);

            final InOrder inOrder = inOrder(underTest);
            inOrder.verify(underTest).onInternalEvent(any(NodeUpEvent.class));
            inOrder.verify(underTest).onInternalEvent(NodeUnrecoverableErrorEvent.of(Node.of(identity), new DrasylException("error")));
        }

        @Test
        void shouldReturnSameFutureIfStartHasAlreadyBeenTriggered(@Mock final CompletableFuture<Void> startFuture) {
            final DrasylNode drasylNode = spy(new DrasylNode(config, identity, peersManager, pipeline, pluginManager, new AtomicReference<>(startFuture), new AtomicReference<>(), scheduler) {
                @Override
                public void onEvent(final @NonNull Event event) {
                }
            });
            assertSame(startFuture, drasylNode.start());
        }

        @Test
        void shouldStartPlugins() {
            when(scheduler.scheduleDirect(any())).then(invocation -> {
                final Runnable runnable = invocation.getArgument(0, Runnable.class);
                runnable.run();
                return null;
            });

            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, pipeline, pluginManager, new AtomicReference<>(null), new AtomicReference<>(), scheduler) {
                @Override
                public void onEvent(final @NonNull Event event) {
                }
            });
            underTest.start();

            verify(pluginManager).beforeStart();
        }
    }

    @Nested
    class Shutdown {
        @Test
        void shouldReturnSameFutureIfShutdownHasAlreadyBeenTriggered(@Mock final CompletableFuture<Void> shutdownFuture) {
            final DrasylNode drasylNode = spy(new DrasylNode(config, identity, peersManager, pipeline, pluginManager, new AtomicReference<>(), new AtomicReference<>(shutdownFuture), scheduler) {
                @Override
                public void onEvent(final @NonNull Event event) {
                }
            });
            assertSame(shutdownFuture, drasylNode.shutdown());
        }

        @Test
        void shouldStopPlugins() {
            when(scheduler.scheduleDirect(any())).then(invocation -> {
                final Runnable runnable = invocation.getArgument(0, Runnable.class);
                runnable.run();
                return null;
            });
            when(pipeline.processInbound(any())).thenReturn(completedFuture(null));

            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, pipeline, pluginManager, new AtomicReference<>(), new AtomicReference<>(null), scheduler) {
                @Override
                public void onEvent(final @NonNull Event event) {
                }
            });
            underTest.shutdown();

            verify(pluginManager).afterShutdown();
        }
    }

    @Nested
    class Send {
        private DrasylNode underTest;

        @BeforeEach
        void setUp() {
            underTest = spy(new DrasylNode(config, identity, peersManager, pipeline, pluginManager, new AtomicReference<>(), new AtomicReference<>(), scheduler) {
                @Override
                public void onEvent(final @NonNull Event event) {
                }
            });
        }

        @Test
        void shouldPassMessageToPipeline(@Mock final CompressedPublicKey myRecipient) {
            underTest.send(myRecipient, new byte[]{ 0x4f });

            verify(pipeline).processOutbound(myRecipient, payload);
        }

        @Test
        void recipientAsStringShouldPassMessageToPipeline() {
            underTest.send("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458", payload);

            verify(pipeline).processOutbound(
                    CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"),
                    payload
            );
        }

        @Test
        void payloadAsStringShouldPassMessageToPipeline(@Mock final CompressedPublicKey myRecipient) {
            underTest.send(myRecipient, "Hallo Welt");

            verify(pipeline).processOutbound(
                    myRecipient,
                    "Hallo Welt"
            );
        }

        @Test
        void recipientAndPayloadAsStringShouldPassMessageToPipeline() {
            underTest.send("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458", "Hallo Welt");

            verify(pipeline).processOutbound(
                    CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"),
                    "Hallo Welt"
            );
        }
    }

    @Nested
    class TestPipeline {
        @Test
        void shouldReturnPipeline() {
            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, pipeline, pluginManager, new AtomicReference<>(), new AtomicReference<>(), scheduler) {
                @Override
                public void onEvent(final @NonNull Event event) {
                }
            });

            assertEquals(pipeline, underTest.pipeline());
        }
    }

    @Nested
    class TestIdentity {
        @Test
        void shouldReturnIdentity() {
            final DrasylNode underTest = spy(new DrasylNode(config, identity, peersManager, pipeline, pluginManager, new AtomicReference<>(), new AtomicReference<>(), scheduler) {
                @Override
                public void onEvent(final @NonNull Event event) {
                }
            });

            assertEquals(identity, underTest.identity());
        }
    }

    @Nested
    class GetVersion {
        @Test
        void shouldNotReturnNull() {
            assertNotNull(DrasylNode.getVersion());
        }
    }
}
