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

package org.drasyl.remote.handler.portmapper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.HexUtil;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.protocol.AddressedByteBuf;
import org.drasyl.util.ReferenceCountUtil;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PcpPortMappingTest {
    @Nested
    class Start {
        @Test
        void shouldRequestMapping(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                  @Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                  @Mock(answer = RETURNS_DEEP_STUBS) final Runnable onFailure,
                                  @Mock final Supplier<InetAddress> defaultGatewaySupplier,
                                  @Mock final Supplier<Set<InetAddress>> interfaceSupplier) throws UnknownHostException, CryptoException {
            when(ctx.identity().getPublicKey()).thenReturn(CompressedPublicKey.of("034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d"));
            when(defaultGatewaySupplier.get()).thenReturn(InetAddress.getByName("38.12.1.15"));
            when(interfaceSupplier.get()).thenReturn(Set.of(InetAddress.getByName("38.12.1.15")));

            new PcpPortMapping(new AtomicInteger(), 0, null, new byte[]{}, null, null, null, null, defaultGatewaySupplier, interfaceSupplier).start(ctx, event, onFailure);

            verify(ctx).write(any(), any(), any());
        }
    }

    @Nested
    class Stop {
        @Test
        void shouldDestroyMapping(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                  @Mock final Supplier<InetAddress> defaultGatewaySupplier,
                                  @Mock final Disposable timeoutGuard,
                                  @Mock final Disposable refreshTask,
                                  @Mock final Supplier<Set<InetAddress>> interfaceSupplier,
                                  @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddressWrapper defaultGateway) throws UnknownHostException {
            new PcpPortMapping(new AtomicInteger(), 0, null, new byte[]{}, defaultGateway, timeoutGuard, refreshTask, Set.of(InetAddress.getByName("38.12.1.15")), defaultGatewaySupplier, interfaceSupplier).stop(ctx);

            verify(timeoutGuard).dispose();
            verify(refreshTask).dispose();
            verify(ctx).write(any(), any(), any());
        }
    }

    @Nested
    class HandleMessage {
        @Nested
        class FromGateway {
            @Test
            void shouldScheduleRefreshOnMappingMessage(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddressWrapper defaultGateway,
                                                       @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                                       @Mock final AddressedByteBuf msg,
                                                       @Mock final Disposable timeoutGuard,
                                                       @Mock final Supplier<InetAddress> defaultGatewaySupplier,
                                                       @Mock final Supplier<Set<InetAddress>> interfaceSupplier) {
                ByteBuf byteBuf = null;
                try {
                    byteBuf = Unpooled.wrappedBuffer(HexUtil.fromString("02810000000002580004ea00000000000000000000000000027c2af0012b29445e68a77e1100000063f163f100000000000000000000ffffc0a8b202"));
                    when(msg.getContent()).thenReturn(byteBuf);
                    when(msg.refCnt()).thenReturn(1);
                    new PcpPortMapping(new AtomicInteger(1), 25585, null, new byte[]{}, defaultGateway, timeoutGuard, null, null, defaultGatewaySupplier, interfaceSupplier).handleMessage(ctx, msg);

                    verify(timeoutGuard).dispose();
                    verify(ctx.independentScheduler()).scheduleDirect(any(), eq((long) 300), eq(SECONDS));
                }
                finally {
                    ReferenceCountUtil.safeRelease(byteBuf);
                }
            }
        }

        @Nested
        class NotFromGateway {
            @Test
            void shouldReturnFalse(@Mock final AddressedByteBuf msg,
                                   @Mock final Supplier<InetAddress> defaultGatewaySupplier,
                                   @Mock final Supplier<Set<InetAddress>> interfaceSupplier) {
                assertFalse(new PcpPortMapping(new AtomicInteger(), 0, null, new byte[]{}, null, null, null, null, defaultGatewaySupplier, interfaceSupplier).acceptMessage(msg));
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
                @Mock final Supplier<InetAddress> defaultGatewaySupplier,
                @Mock final Supplier<Set<InetAddress>> interfaceSupplier) {
            new PcpPortMapping(new AtomicInteger(), 0, onFailure, new byte[]{}, null, timeoutGuard, refreshTask, null, defaultGatewaySupplier, interfaceSupplier).fail();

            verify(timeoutGuard).dispose();
            verify(refreshTask).dispose();
            verify(onFailure).run();
        }
    }
}
