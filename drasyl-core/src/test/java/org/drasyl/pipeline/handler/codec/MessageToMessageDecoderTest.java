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

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
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
class MessageToMessageDecoderTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;

    @Test
    void shouldCompleteExceptionallyOnException(@Mock final Address recipient) {
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, new MessageToMessageDecoder<>() {
            @Override
            protected void decode(final HandlerContext ctx,
                                  final Address sender,
                                  final Object msg, final List<Object> out) throws Exception {
                throw new Exception();
            }
        })) {
            assertThrows(ExecutionException.class, () -> pipeline.processInbound(recipient, new Object()).get());
        }
    }

    @Test
    void shouldCompleteExceptionallyOnEmptyEncodingResult(@Mock final Address recipient) {
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, new MessageToMessageDecoder<>() {
            @Override
            protected void decode(final HandlerContext ctx,
                                  final Address sender,
                                  final Object msg, final List<Object> out) {
                // do nothing
            }
        })) {
            assertThrows(ExecutionException.class, () -> pipeline.processInbound(recipient, new Object()).get());
        }
    }

    @Test
    void shouldPassEncodedResult(@Mock final Address recipient) {
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, new MessageToMessageDecoder<>() {
            @Override
            protected void decode(final HandlerContext ctx,
                                  final Address sender,
                                  final Object msg, final List<Object> out) {
                out.add("Hallo Welt");
            }
        })) {
            final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

            pipeline.processInbound(recipient, new Object());

            inboundMessages.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue("Hallo Welt");
        }
    }

    @Test
    void shouldCreateCombinedFutureOnMultiEncodingResult(@Mock final Address sender) {
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, new MessageToMessageDecoder<>() {
            @Override
            protected void decode(final HandlerContext ctx,
                                  final Address sender,
                                  final Object msg, final List<Object> out) {
                out.add(new Object());
                out.add(msg);
            }
        }, new SimpleInboundHandler<>() {
            private boolean firstWritten;

            @Override
            protected void matchedInbound(final HandlerContext ctx,
                                          final Address sender,
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
        })) {
            assertThrows(ExecutionException.class, () -> pipeline.processInbound(sender, new Object()).get());
        }
    }
}
