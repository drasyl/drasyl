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
package org.drasyl.handler.remote.portmapper;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.UdpServer.UdpServerBound;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortMapperTest {
    @Nested
    class EventHandling {
        @Test
        void shouldStartFirstMethodOnPortEvent(@Mock final PortMapping method,
                                               @Mock final UdpServerBound event) {
            when(event.getBindAddress()).thenReturn(new InetSocketAddress(12345));

            final ArrayList<PortMapping> methods = new ArrayList<>(List.of(method));

            final PortMapper handler = new PortMapper(methods, 0, null);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireUserEventTriggered(event);

                verify(method).start(any(), anyInt(), any());
            }
            finally {
                channel.checkException();
                channel.close();
            }
        }

        @Test
        void shouldStopCurrentMethodOnChannalInactive(@Mock final PortMapping method) {
            final ArrayList<PortMapping> methods = new ArrayList<>(List.of(method));

            final PortMapper handler = new PortMapper(methods, 0, null);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireChannelInactive();

                verify(method).stop(any());
            }
            finally {
                channel.checkException();
                channel.close();
            }
        }
    }

    @Nested
    class MessageHandling {
        @Test
        void shouldConsumeMethodMessages(@Mock final PortMapping method,
                                         @Mock final InetSocketAddress sender,
                                         @Mock final ByteBuf msg) {
            when(method.acceptMessage(eq(sender), any())).thenReturn(true);

            final ArrayList<PortMapping> methods = new ArrayList<>(List.of(method));

            final PortMapper handler = new PortMapper(methods, 0, null);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireChannelRead(new InetAddressedMessage<>(msg, null, sender));

                assertNull(channel.readInbound());
            }
            finally {
                channel.checkException();
                channel.close();
            }
        }

        @Test
        void shouldPassThroughNonMethodMessages(@Mock final PortMapping method,
                                                @Mock final InetSocketAddress sender,
                                                @Mock final ByteBuf msg) {
            final ArrayList<PortMapping> methods = new ArrayList<>(List.of(method));

            final PortMapper handler = new PortMapper(methods, 0, null);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireChannelRead(new InetAddressedMessage<>(msg, null, sender));

                final ReferenceCounted actual = channel.readInbound();
                assertEquals(new InetAddressedMessage<>(msg, null, sender), actual);

                actual.release();
            }
            finally {
                channel.checkException();
                channel.close();
            }
        }
    }

    @Nested
    class MethodCycling {
        @Test
        void shouldCycleToNextMethodOnFailure(@Mock final PortMapping method1,
                                              @Mock final PortMapping method2,
                                              @Mock final UdpServerBound event) {
            when(event.getBindAddress()).thenReturn(new InetSocketAddress(12345));
            doAnswer(invocation -> {
                final Runnable onFailure = invocation.getArgument(2, Runnable.class);
                onFailure.run();
                return null;
            }).when(method1).start(any(), anyInt(), any());

            final ArrayList<PortMapping> methods = new ArrayList<>(List.of(method1, method2));

            final PortMapper handler = new PortMapper(methods, 0, null);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireUserEventTriggered(event);

                verify(method2).start(any(), anyInt(), any());
            }
            finally {
                channel.checkException();
                channel.close();
            }
        }
    }
}
