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
package org.drasyl.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintStream;
import java.net.SocketAddress;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiPredicate;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagesThroughputHandlerTest {
    @Mock
    BiPredicate<SocketAddress, Object> consumeOutbound;
    @Mock
    BiPredicate<SocketAddress, Object> consumeInbound;
    @Mock
    LongAdder outboundMessages;
    @Mock
    LongAdder inboundMessages;
    @Mock(answer = RETURNS_DEEP_STUBS)
    EventLoopGroup eventLoopGroup;
    @Mock
    PrintStream printStream;
    @Mock
    ScheduledFuture<?> disposable;

    @Test
    void shouldPrintThroughputOnChannelActive() {
        when(eventLoopGroup.scheduleAtFixedRate(any(), eq(0L), eq(1_000L), eq(MILLISECONDS))).then(invocation -> {
            final Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return null;
        });

        final ChannelInboundHandler handler = new MessagesThroughputHandler(consumeOutbound, consumeInbound, outboundMessages, inboundMessages, eventLoopGroup, printStream, null);
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
        try {
            verify(printStream).printf(anyString(), any(), any(), any(), any());
        }
        finally {
            pipeline.close();
        }
    }

    @Test
    void shouldStopTaskOnChannelInactive(@Mock final ChannelHandlerContext ctx) {
        final MessagesThroughputHandler handler = new MessagesThroughputHandler(consumeOutbound, consumeInbound, outboundMessages, inboundMessages, eventLoopGroup, printStream, disposable);

        handler.channelInactive(ctx);

        verify(disposable).cancel(false);
    }

    @Test
    void shouldRecordOutboundMessage(@Mock final SocketAddress address) {
        final ChannelInboundHandler handler = new MessagesThroughputHandler(consumeOutbound, consumeInbound, outboundMessages, inboundMessages, eventLoopGroup, printStream, null);
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
        try {
            pipeline.writeAndFlush(new AddressedMessage<>(new Object(), address));

            verify(outboundMessages).increment();
            verify(inboundMessages, never()).increment();
        }
        finally {
            pipeline.releaseOutbound();
            pipeline.close();
        }
    }

    @Test
    void shouldRecordInboundMessage(@Mock final SocketAddress address) {
        final ChannelInboundHandler handler = new MessagesThroughputHandler(consumeOutbound, consumeInbound, outboundMessages, inboundMessages, eventLoopGroup, printStream, null);
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
        try {
            pipeline.pipeline().fireChannelRead(new AddressedMessage<>(new Object(), address));
        }
        finally {
            pipeline.releaseInbound();
            pipeline.close();
        }

        verify(outboundMessages, never()).increment();
        verify(inboundMessages).increment();
    }

    @Test
    void shouldConsumeMatchingOutboundMessage(@Mock final SocketAddress address) {
        final ChannelInboundHandler handler = new MessagesThroughputHandler((myAddress, msg) -> true, consumeInbound, outboundMessages, inboundMessages, eventLoopGroup, printStream, null);
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
        try {
            pipeline.writeAndFlush(new AddressedMessage<>(new Object(), address));

            assertNull(pipeline.readOutbound());
        }
        finally {
            pipeline.close();
        }
    }

    @Test
    void shouldConsumeMatchingInboundMessage(@Mock final SocketAddress address) {
        final ChannelInboundHandler handler = new MessagesThroughputHandler(consumeOutbound, (myAddress, msg) -> true, outboundMessages, inboundMessages, eventLoopGroup, printStream, null);
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
        try {
            pipeline.pipeline().fireChannelRead(new AddressedMessage<>(new Object(), address));

            assertNull(pipeline.readInbound());
        }
        finally {
            pipeline.close();
        }
    }
}
