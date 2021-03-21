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
package org.drasyl.remote.handler.portmapper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.crypto.HexUtil;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NatPmpPortMappingTest {
    @Nested
    class Start {
        @Test
        void shouldRequestExternalMessage(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final Runnable onFailure,
                                          @Mock final Supplier<InetAddress> defaultGatewaySupplier) throws UnknownHostException {
            when(defaultGatewaySupplier.get()).thenReturn(InetAddress.getByName("38.12.1.15"));
            final AtomicBoolean externalAddressRequested = new AtomicBoolean();
            final AtomicBoolean mappingRequested = new AtomicBoolean();
            new NatPmpPortMapping(externalAddressRequested, mappingRequested, 0, null, null, null, null, onFailure, defaultGatewaySupplier).start(ctx, event, onFailure);

            verify(ctx).passOutbound(any(), any(), any());
            assertTrue(externalAddressRequested.get());
        }
    }

    @Nested
    class Stop {
        @Test
        void shouldDestroyMapping(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                  @Mock final InetAddress externalAddress,
                                  @Mock final Disposable timeoutGuard,
                                  @Mock final Disposable refreshTask,
                                  @Mock final Supplier<InetAddress> defaultGatewaySupplier) {
            final AtomicBoolean externalAddressRequested = new AtomicBoolean();
            final AtomicBoolean mappingRequested = new AtomicBoolean();
            new NatPmpPortMapping(externalAddressRequested, mappingRequested, 0, new InetSocketAddressWrapper(12345), externalAddress, timeoutGuard, refreshTask, null, defaultGatewaySupplier).stop(ctx);

            verify(timeoutGuard).dispose();
            verify(refreshTask).dispose();
            verify(ctx).passOutbound(any(), any(), any());
        }
    }

    @Nested
    class HandleMessage {
        @Nested
        class FromGateway {
            @Test
            void shouldRequestMappingAfterReceivingExternalAddressMessage(@Mock final HandlerContext ctx,
                                                                          @Mock final InetSocketAddressWrapper sender,
                                                                          @Mock final Supplier<InetAddress> defaultGatewaySupplier) {
                final ByteBuf byteBuf = Unpooled.wrappedBuffer(HexUtil.fromString("008000000004f79fc0a8b202"));
                final AtomicBoolean externalAddressRequested = new AtomicBoolean(true);
                final AtomicBoolean mappingRequested = new AtomicBoolean();
                new NatPmpPortMapping(externalAddressRequested, mappingRequested, 0, new InetSocketAddressWrapper(12345), null, null, null, null, defaultGatewaySupplier).handleMessage(ctx, sender, byteBuf);

                verify(ctx).passOutbound(any(), any(), any());
            }

            @Test
            void shouldScheduleRefreshOnMappingMessage(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                                       @Mock final InetSocketAddressWrapper sender,
                                                       @Mock final Disposable timeoutGuard,
                                                       @Mock final InetAddress externalAddress,
                                                       @Mock final Supplier<InetAddress> defaultGatewaySupplier) {
                final ByteBuf byteBuf = Unpooled.wrappedBuffer(HexUtil.fromString("008100000004f9bf63f163f100000258"));
                final AtomicBoolean externalAddressRequested = new AtomicBoolean();
                final AtomicBoolean mappingRequested = new AtomicBoolean(true);
                new NatPmpPortMapping(externalAddressRequested, mappingRequested, 25585, new InetSocketAddressWrapper(12345), externalAddress, timeoutGuard, null, null, defaultGatewaySupplier).handleMessage(ctx, sender, byteBuf);

                verify(timeoutGuard).dispose();
                verify(ctx.independentScheduler()).scheduleDirect(any(), eq((long) 300), eq(SECONDS));
            }
        }

        @Nested
        class NotFromGateway {
            @Test
            void shouldReturnFalse(@Mock final InetSocketAddressWrapper sender,
                                   @Mock final ByteBuf msg,
                                   @Mock final Supplier<InetAddress> defaultGatewaySupplier) {
                final AtomicBoolean externalAddressRequested = new AtomicBoolean();
                final AtomicBoolean mappingRequested = new AtomicBoolean();
                assertFalse(new NatPmpPortMapping(externalAddressRequested, mappingRequested, 0, null, null, null, null, null, defaultGatewaySupplier).acceptMessage(sender, msg));
            }
        }
    }

    @Nested
    class Fail {
        @Test
        void shouldDisposeAllTasks(
                @Mock final Disposable timeoutGuard,
                @Mock final Disposable refreshTask,
                @Mock final Runnable onFailure,
                @Mock final Supplier<InetAddress> defaultGatewaySupplier) {
            final AtomicBoolean externalAddressRequested = new AtomicBoolean();
            final AtomicBoolean mappingRequested = new AtomicBoolean();
            new NatPmpPortMapping(externalAddressRequested, mappingRequested, 0, null, null, timeoutGuard, refreshTask, onFailure, defaultGatewaySupplier).fail();

            verify(timeoutGuard).dispose();
            verify(refreshTask).dispose();
            verify(onFailure).run();
        }
    }
}
