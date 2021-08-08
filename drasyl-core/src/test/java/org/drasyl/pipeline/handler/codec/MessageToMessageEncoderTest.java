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
package org.drasyl.pipeline.handler.codec;

import io.netty.channel.ChannelHandlerContext;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.util.FutureUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;

@ExtendWith(MockitoExtension.class)
class MessageToMessageEncoderTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;

    @Test
    void shouldCompleteExceptionallyOnException(@Mock final Address recipient) {
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, new MessageToMessageEncoder<>() {
            @Override
            protected void encode(final ChannelHandlerContext ctx,
                                  final Address recipient,
                                  final Object msg, final List<Object> out) throws Exception {
                throw new Exception();
            }
        });
        try {
            assertThrows(ExecutionException.class, () -> FutureUtil.toFuture(pipeline.processOutbound(recipient, new Object())).get());
        }
        finally {
            pipeline.drasylClose();
        }
    }

    @Test
    void shouldCompleteExceptionallyOnEmptyEncodingResult(@Mock final Address recipient) {
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, new MessageToMessageEncoder<>() {
            @Override
            protected void encode(final ChannelHandlerContext ctx,
                                  final Address recipient,
                                  final Object msg, final List<Object> out) {
                // do nothing
            }
        });
        try {
            assertThrows(ExecutionException.class, () -> FutureUtil.toFuture(pipeline.processOutbound(recipient, new Object())).get());
        }
        finally {
            pipeline.drasylClose();
        }
    }

    @Test
    void shouldPassEncodedResult(@Mock final Address recipient) {
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, new MessageToMessageEncoder<>() {
            @Override
            protected void encode(final ChannelHandlerContext ctx,
                                  final Address recipient,
                                  final Object msg, final List<Object> out) {
                out.add("Hallo Welt");
            }
        });
        try {
            final TestObserver<Object> outboundMessages = pipeline.drasylOutboundMessages().test();

            pipeline.processOutbound(recipient, new Object());

            outboundMessages.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue("Hallo Welt");
        }
        finally {
            pipeline.drasylClose();
        }
    }

    @Test
    void shouldCreateCombinedFutureOnMultiEncodingResult(@Mock final Address recipient) {
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, new SimpleOutboundHandler<>() {
            private boolean firstWritten;

            @Override
            protected void matchedOutbound(final ChannelHandlerContext ctx,
                                           final Address recipient,
                                           final Object msg,
                                           final CompletableFuture<Void> future) {
                if (!firstWritten) {
                    firstWritten = true;
                    future.complete(null);
                }
                else {
                    future.completeExceptionally(new Exception());
                }
            }
        }, new MessageToMessageEncoder<>() {
            @Override
            protected void encode(final ChannelHandlerContext ctx,
                                  final Address recipient,
                                  final Object msg, final List<Object> out) {
                out.add(new Object());
                out.add(msg);
            }
        });
        try {
            assertThrows(ExecutionException.class, () -> FutureUtil.toFuture(pipeline.processOutbound(recipient, new Object())).get());
        }
        finally {
            pipeline.drasylClose();
        }
    }
}
