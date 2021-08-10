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

import io.netty.channel.ChannelInboundHandler;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
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
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    BiPredicate<SocketAddress, Object> consumeOutbound;
    @Mock
    BiPredicate<SocketAddress, Object> consumeInbound;
    @Mock
    LongAdder outboundMessages;
    @Mock
    LongAdder inboundMessages;
    @Mock
    Scheduler scheduler;
    @Mock
    PrintStream printStream;
    @Mock
    Disposable disposable;

    @Test
    void shouldPrintThroughputOnChannelActive() {
        when(scheduler.schedulePeriodicallyDirect(any(), eq(0L), eq(1_000L), eq(MILLISECONDS))).then(invocation -> {
            final Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return null;
        });

        final ChannelInboundHandler handler = new MessagesThroughputHandler(consumeOutbound, consumeInbound, outboundMessages, inboundMessages, scheduler, printStream, null);
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
        try {
            pipeline.pipeline().fireChannelActive();

            verify(printStream).printf(anyString(), any(), any(), any(), any());
        }
        finally {
            pipeline.drasylClose();
        }
    }

    @Test
    void shouldStopTaskOnChannelInactive() {
        final ChannelInboundHandler handler = new MessagesThroughputHandler(consumeOutbound, consumeInbound, outboundMessages, inboundMessages, scheduler, printStream, disposable);
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
        try {
            pipeline.pipeline().fireChannelInactive();

            verify(disposable).dispose();
        }
        finally {
            pipeline.drasylClose();
        }
    }

    @Test
    void shouldRecordOutboundMessage(@Mock final SocketAddress address) {
        final ChannelInboundHandler handler = new MessagesThroughputHandler(consumeOutbound, consumeInbound, outboundMessages, inboundMessages, scheduler, printStream, null);
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
        try {
            pipeline.writeAndFlush(new AddressedMessage<>(new Object(), address));

            verify(outboundMessages).increment();
            verify(inboundMessages, never()).increment();
        }
        finally {
            pipeline.drasylClose();
        }
    }

    @Test
    void shouldRecordInboundMessage(@Mock final SocketAddress address) {
        final ChannelInboundHandler handler = new MessagesThroughputHandler(consumeOutbound, consumeInbound, outboundMessages, inboundMessages, scheduler, printStream, null);
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
        try {
            pipeline.pipeline().fireChannelRead(new AddressedMessage<>(new Object(), address));
        }
        finally {
            pipeline.drasylClose();
        }

        verify(outboundMessages, never()).increment();
        verify(inboundMessages).increment();
    }

    @Test
    void shouldConsumeMatchingOutboundMessage(@Mock final SocketAddress address) {
        final ChannelInboundHandler handler = new MessagesThroughputHandler((myAddress, msg) -> true, consumeInbound, outboundMessages, inboundMessages, scheduler, printStream, null);
        final TestObserver<Object> observable;
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
        try {
            pipeline.writeAndFlush(new AddressedMessage<>(new Object(), address));

            assertNull(pipeline.readOutbound());
        }
        finally {
            pipeline.drasylClose();
        }
    }

    @Test
    void shouldConsumeMatchingInboundMessage(@Mock final SocketAddress address) {
        final ChannelInboundHandler handler = new MessagesThroughputHandler(consumeOutbound, (myAddress, msg) -> true, outboundMessages, inboundMessages, scheduler, printStream, null);
        final TestObserver<Object> observable;
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
        try {
            pipeline.pipeline().fireChannelRead(new AddressedMessage<>(new Object(), address));

            assertNull(pipeline.readInbound());
        }
        finally {
            pipeline.drasylClose();
        }
    }
}
