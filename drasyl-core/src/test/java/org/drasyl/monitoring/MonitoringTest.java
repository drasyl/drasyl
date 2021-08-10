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
package org.drasyl.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCounted;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.event.Event;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.remote.protocol.RemoteMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    private final Map<String, Counter> counters = new HashMap<>();
    @Mock
    private Function<ChannelHandlerContext, MeterRegistry> registrySupplier;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private MeterRegistry registry;

    @Nested
    class StartMonitoring {
        @Test
        void shouldStartDiscoveryOnChannelActive(@Mock final NodeUpEvent event,
                                                 @Mock(answer = RETURNS_DEEP_STUBS) final MeterRegistry registry) {
            when(registrySupplier.apply(any())).thenReturn(registry);

            final Monitoring handler = new Monitoring(counters, registrySupplier, null);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.pipeline().fireChannelActive();

                verify(registrySupplier).apply(any());
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class StopDiscovery {
        @Test
        void shouldStopDiscoveryOnChannelInactive(@Mock final NodeUnrecoverableErrorEvent event) {
            final Monitoring handler = new Monitoring(counters, registrySupplier, registry);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.pipeline().fireChannelInactive();

                verify(registry).close();
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class MessagePassing {
        @Test
        void shouldPassthroughAllEvents(@Mock final Event event) {
            final Monitoring handler = spy(new Monitoring(counters, registrySupplier, registry));
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<Object> inboundEvents = pipeline.events().test();

                pipeline.pipeline().fireUserEventTriggered(event);

                inboundEvents.awaitCount(1)
                        .assertValue(event);
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldPassthroughInboundMessages(@Mock final SocketAddress sender,
                                              @Mock final RemoteMessage message) {
            final Monitoring handler = spy(new Monitoring(counters, registrySupplier, registry));
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(message, sender));

                final ReferenceCounted actual = pipeline.readInbound();
                assertEquals(new AddressedMessage<>(message, sender), actual);

                actual.release();
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldPassthroughOutboundMessages(@Mock final SocketAddress recipient,
                                               @Mock final RemoteMessage message) {
            final Monitoring handler = spy(new Monitoring(counters, registrySupplier, registry));
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.writeAndFlush(new AddressedMessage<>(message, recipient));

                final ReferenceCounted actual = pipeline.readOutbound();
                assertEquals(new AddressedMessage<>(message, recipient), actual);

                actual.release();
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }
}
