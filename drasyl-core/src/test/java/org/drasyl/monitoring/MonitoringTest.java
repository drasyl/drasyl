/*
 * Copyright (c) 2020.
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
package org.drasyl.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.skeletons.SimpleDuplexHandler;
import org.drasyl.pipeline.address.Address;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.drasyl.monitoring.Monitoring.MONITORING_HANDLER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringTest {
    @Mock
    private PeersManager peersManager;
    @Mock
    private CompressedPublicKey publicKey;
    @Mock
    private Pipeline pipeline;
    @Mock
    private AtomicBoolean opened;
    @Mock
    private Supplier<MeterRegistry> registrySupplier;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MeterRegistry registry;
    @InjectMocks
    private Monitoring underTest;

    @Nested
    class Open {
        @Test
        void shouldSetOpenToTrue() {
            opened = new AtomicBoolean();
            when(registrySupplier.get()).thenReturn(registry);
            underTest = new Monitoring(peersManager, publicKey, pipeline, registrySupplier, opened, registry);
            underTest.open();

            assertTrue(opened.get());
        }

        @Test
        void shouldAddHandlerToPipelineAndListenOnPeerRelayEvents(@Mock(answer = Answers.RETURNS_DEEP_STUBS) final HandlerContext ctx) {
            when(registrySupplier.get()).thenReturn(registry);
            when(pipeline.addFirst(eq(MONITORING_HANDLER), any())).then(invocation -> {
                final SimpleDuplexHandler<?, ?, Address> handler = invocation.getArgument(1);
                handler.eventTriggered(ctx, mock(Event.class), new CompletableFuture<>());
                handler.read(ctx, mock(Address.class), mock(Object.class), new CompletableFuture<>());
                handler.write(ctx, mock(Address.class), mock(Object.class), new CompletableFuture<>());
                return invocation.getMock();
            });

            underTest = new Monitoring(peersManager, publicKey, pipeline, registrySupplier, new AtomicBoolean(), registry);
            underTest.open();

            verify(pipeline).addFirst(eq(MONITORING_HANDLER), any());
            verify(ctx.scheduler(), times(3)).scheduleDirect(any());
        }
    }

    @Nested
    class Close {
        @Test
        void shouldSetOpenToFalse() {
            underTest = new Monitoring(peersManager, publicKey, pipeline, registrySupplier, new AtomicBoolean(true), registry);

            underTest.close();

            assertFalse(opened.get());
        }

        @Test
        void shouldRemoveHandlerFromPipeline() {
            underTest = new Monitoring(peersManager, publicKey, pipeline, registrySupplier, new AtomicBoolean(true), registry);

            underTest.close();

            verify(pipeline).remove(MONITORING_HANDLER);
        }
    }
}