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
package org.drasyl.remote.handler.portmapper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.crypto.HexUtil;
import org.drasyl.identity.IdentityPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.InetSocketAddress;
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
        void shouldRequestMapping(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                  @Mock(answer = RETURNS_DEEP_STUBS) final Runnable onFailure,
                                  @Mock final Supplier<InetAddress> defaultGatewaySupplier,
                                  @Mock final Supplier<Set<InetAddress>> interfaceSupplier) throws UnknownHostException {
            final IdentityPublicKey myAddress = IdentityPublicKey.of("18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127");
            when(defaultGatewaySupplier.get()).thenReturn(InetAddress.getByName("38.12.1.15"));
            when(interfaceSupplier.get()).thenReturn(Set.of(InetAddress.getByName("38.12.1.15")));

            new PcpPortMapping(new AtomicInteger(), 0, null, new byte[]{}, null, null, null, null, defaultGatewaySupplier, interfaceSupplier).start(ctx, 12345, onFailure);

            verify(ctx).writeAndFlush(any(AddressedMessage.class));
        }
    }

    @Nested
    class Stop {
        @Test
        void shouldDestroyMapping(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                  @Mock final Supplier<InetAddress> defaultGatewaySupplier,
                                  @Mock final Future<?> timeoutGuard,
                                  @Mock final Future<?> refreshTask,
                                  @Mock final Supplier<Set<InetAddress>> interfaceSupplier) throws UnknownHostException {
            new PcpPortMapping(new AtomicInteger(), 0, null, new byte[]{}, new InetSocketAddress(12345), timeoutGuard, refreshTask, Set.of(InetAddress.getByName("38.12.1.15")), defaultGatewaySupplier, interfaceSupplier).stop(ctx);

            verify(timeoutGuard).cancel(false);
            verify(refreshTask).cancel(false);
            verify(ctx).writeAndFlush(any(AddressedMessage.class));
        }
    }

    @Nested
    class HandleMessage {
        @Nested
        class FromGateway {
            @Test
            void shouldScheduleRefreshOnMappingMessage(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                       @Mock final InetSocketAddress sender,
                                                       @Mock final Future<?> timeoutGuard,
                                                       @Mock final Supplier<InetAddress> defaultGatewaySupplier,
                                                       @Mock final Supplier<Set<InetAddress>> interfaceSupplier) {
                final ByteBuf byteBuf = Unpooled.wrappedBuffer(HexUtil.fromString("02810000000002580004ea00000000000000000000000000027c2af0012b29445e68a77e1100000063f163f100000000000000000000ffffc0a8b202"));
                new PcpPortMapping(new AtomicInteger(1), 25585, null, new byte[]{}, new InetSocketAddress(12345), timeoutGuard, null, null, defaultGatewaySupplier, interfaceSupplier).handleMessage(ctx, sender, byteBuf);

                verify(timeoutGuard).cancel(false);
                verify(ctx.executor()).schedule(ArgumentMatchers.<Runnable>any(), eq((long) 300), eq(SECONDS));
            }
        }

        @Nested
        class NotFromGateway {
            @Test
            void shouldReturnFalse(@Mock final InetSocketAddress sender,
                                   @Mock final ByteBuf msg,
                                   @Mock final Supplier<InetAddress> defaultGatewaySupplier,
                                   @Mock final Supplier<Set<InetAddress>> interfaceSupplier) {
                assertFalse(new PcpPortMapping(new AtomicInteger(), 0, null, new byte[]{}, null, null, null, null, defaultGatewaySupplier, interfaceSupplier).acceptMessage(sender, msg));
            }
        }
    }

    @Nested
    class Fail {
        @Test
        void shouldDisposeAllTasks(@Mock final Future<?> timeoutGuard,
                                   @Mock final Future<?> refreshTask,
                                   @Mock final Runnable onFailure,
                                   @Mock final Supplier<InetAddress> defaultGatewaySupplier,
                                   @Mock final Supplier<Set<InetAddress>> interfaceSupplier) {
            new PcpPortMapping(new AtomicInteger(), 0, onFailure, new byte[]{}, null, timeoutGuard, refreshTask, null, defaultGatewaySupplier, interfaceSupplier).fail();

            verify(timeoutGuard).cancel(false);
            verify(refreshTask).cancel(false);
            verify(onFailure).run();
        }
    }
}
