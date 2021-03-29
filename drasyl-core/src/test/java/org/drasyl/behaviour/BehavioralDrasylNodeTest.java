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
package org.drasyl.behaviour;

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.drasyl.behaviour.Behavior.SAME;
import static org.drasyl.behaviour.Behavior.SHUTDOWN;
import static org.drasyl.behaviour.Behavior.UNHANDLED;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BehavioralDrasylNodeTest {
    private final AtomicReference<CompletableFuture<Void>> startFuture = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> shutdownFuture = new AtomicReference<>();
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
    @Mock
    private Behavior behavior;

    @Nested
    class OnEvent {
        private BehavioralDrasylNode node;

        @BeforeEach
        void setUp() {
            node = spy(new BehavioralDrasylNode(config, identity, peersManager, pipeline, pluginManager, startFuture, shutdownFuture, scheduler, behavior) {
                @Override
                protected Behavior created() {
                    return null;
                }
            });
        }

        @Test
        void shouldPassEventToBehavior(@Mock final Event event) {
            node.onEvent(event);

            verify(behavior).receive(event);
        }

        @Test
        void shouldSwitchToNewBehavior(@Mock final Event event,
                                       @Mock final Behavior newBehavior) {
            when(behavior.receive(any())).thenReturn(newBehavior);

            node.onEvent(event);

            assertSame(newBehavior, node.behavior);
        }

        @Test
        void shouldUnpackDeferredBehavior(@Mock final Event event,
                                          @Mock final DeferredBehavior deferredBehavior) {
            when(behavior.receive(any())).thenReturn(deferredBehavior);

            node.onEvent(event);

            verify(deferredBehavior).apply(node);
        }

        @Test
        void shouldStayOnSameBehaviorIfNewBehaviorIsSame(@Mock final Event event) {
            when(behavior.receive(any())).thenReturn(SAME);

            node.onEvent(event);

            assertSame(behavior, node.behavior);
        }

        @Test
        void shouldStayOnSameBehaviorIfNewBehaviorIsUnhandled(@Mock final Event event) {
            when(behavior.receive(any())).thenReturn(UNHANDLED);

            node.onEvent(event);

            assertSame(behavior, node.behavior);
        }

        @Test
        void shouldShutdownNodeIfNewBehaviorIsShutdown(@Mock final Event event) {
            when(behavior.receive(any())).thenReturn(SHUTDOWN);

            node.onEvent(event);

            verify(node).shutdown();
            assertSame(SHUTDOWN, node.behavior);
        }
    }
}
