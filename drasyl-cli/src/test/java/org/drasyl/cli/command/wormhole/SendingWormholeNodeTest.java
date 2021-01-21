/*
 * Copyright (c) 2021.
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

package org.drasyl.cli.command.wormhole;

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendingWormholeNodeTest {
    private final String password = "123";
    private final AtomicBoolean sent = new AtomicBoolean();
    private final AtomicReference<CompletableFuture<Void>> startFuture = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> shutdownFuture = new AtomicReference<>();
    private ByteArrayOutputStream outputStream;
    private PrintStream printStream;
    @Mock
    private CompletableFuture<Void> doneFuture;
    @Mock
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Pipeline pipeline;
    @Mock
    private PluginManager pluginManager;
    @Mock
    private Scheduler scheduler;
    private SendingWormholeNode underTest;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        printStream = new PrintStream(outputStream, true);
        underTest = new SendingWormholeNode(doneFuture, printStream, password, sent, config, identity, peersManager, pipeline, pluginManager, startFuture, shutdownFuture, scheduler);
    }

    @Nested
    class OnEvent {
        @Test
        void shouldCompleteExceptionallyOnError(@Mock final NodeUnrecoverableErrorEvent event) {
            underTest.onEvent(event);

            verify(doneFuture).completeExceptionally(any());
        }

        @Test
        void shouldCompleteOnTerminationEvent(@Mock final NodeNormalTerminationEvent event) {
            underTest.onEvent(event);

            verify(doneFuture).complete(null);
        }

        @Test
        void shouldSendTextOnPasswordMessageWithCorrectPassword(@Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent event) {
            when(event.getPayload()).thenReturn(new PasswordMessage("123"));
            underTest.setText("Hi");

            underTest.onEvent(event);

            verify(pipeline).processOutbound(eq(event.getSender()), any(TextMessage.class));
        }

        @Test
        void shouldSendTextOnPasswordMessageWithWrongPassword(@Mock(answer = RETURNS_DEEP_STUBS) final MessageEvent event) {
            when(event.getPayload()).thenReturn(new PasswordMessage("456"));
            underTest.setText("Hi");

            underTest.onEvent(event);

            verify(pipeline).processOutbound(eq(event.getSender()), any(WrongPasswordMessage.class));
        }
    }

    @Nested
    class DoneFuture {
        @Test
        void shouldReturnDoneFuture() {
            assertEquals(doneFuture, underTest.doneFuture());
        }
    }
}