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
package org.drasyl.pipeline.handler.codec;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class MessageToMessageEncoderTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;

    @Test
    void shouldCompleteExceptionallyOnException(@Mock final Address recipient) {
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, new MessageToMessageEncoder<>() {
            @Override
            protected void encode(final HandlerContext ctx,
                                  final Address recipient,
                                  final Object msg, final List<Object> out) throws Exception {
                throw new Exception();
            }
        })) {
            assertThrows(ExecutionException.class, () -> pipeline.processOutbound(recipient, new Object()).get());
        }
    }

    @Test
    void shouldCompleteExceptionallyOnEmptyEncodingResult(@Mock final Address recipient) {
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, new MessageToMessageEncoder<>() {
            @Override
            protected void encode(final HandlerContext ctx,
                                  final Address recipient,
                                  final Object msg, final List<Object> out) {
                // do nothing
            }
        })) {
            assertThrows(ExecutionException.class, () -> pipeline.processOutbound(recipient, new Object()).get());
        }
    }

    @Test
    void shouldPassEncodedResult(@Mock final Address recipient) {
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, new MessageToMessageEncoder<>() {
            @Override
            protected void encode(final HandlerContext ctx,
                                  final Address recipient,
                                  final Object msg, final List<Object> out) {
                out.add("Hallo Welt");
            }
        })) {
            final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

            pipeline.processOutbound(recipient, new Object());

            outboundMessages.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue("Hallo Welt");
        }
    }

    @Test
    void shouldCreateCombinedFutureOnMultiEncodingResult(@Mock final Address recipient) {
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, new SimpleOutboundHandler<>() {
            private boolean firstWritten;

            @Override
            protected void matchedOutbound(final HandlerContext ctx,
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
            protected void encode(final HandlerContext ctx,
                                  final Address recipient,
                                  final Object msg, final List<Object> out) {
                out.add(new Object());
                out.add(msg);
            }
        })) {
            assertThrows(ExecutionException.class, () -> pipeline.processOutbound(recipient, new Object()).get());
        }
    }
}
