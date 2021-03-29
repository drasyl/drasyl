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
package org.drasyl.pipeline.skeleton;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.HandlerMask;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class SimpleInboundHandlerTest {
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private DrasylConfig config;

    @Test
    void shouldTriggerOnMatchedMessage(@Mock final Address sender) {
        final SimpleInboundEventAwareHandler<byte[], Event, Address> handler = new SimpleInboundHandler<>() {
            @Override
            protected void matchedInbound(final HandlerContext ctx,
                                          final Address sender,
                                          final byte[] msg,
                                          final CompletableFuture<Void> future) {
                ctx.passInbound(sender, new String(msg), future);
            }
        };

        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            final TestObserver<AddressedEnvelope<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessagesWithSender().test();

            pipeline.processInbound(sender, "Hallo Welt".getBytes());

            inboundMessageTestObserver.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue(new DefaultAddressedEnvelope<>(sender, null, "Hallo Welt"));
        }
    }

    @Test
    void shouldPassthroughsNotMatchingMessage(@Mock final Address sender) {
        final SimpleInboundHandler<byte[], Address> handler = new SimpleInboundHandler<>() {
            @Override
            protected void matchedInbound(final HandlerContext ctx,
                                          final Address sender,
                                          final byte[] msg,
                                          final CompletableFuture<Void> future) {
                ctx.passInbound(sender, new String(msg), future);
            }
        };

        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            final TestObserver<AddressedEnvelope<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessagesWithSender().test();

            pipeline.processInbound(sender, 1337).join();

            inboundMessageTestObserver.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue(new DefaultAddressedEnvelope<>(sender, null, 1337));
        }
    }

    @Test
    void shouldReturnCorrectHandlerMask() {
        assertEquals(HandlerMask.ON_INBOUND_MASK, HandlerMask.mask(SimpleInboundHandler.class));
    }

    @Test
    void shouldReturnCorrectHandlerMaskForEventAwareHandler() {
        final int mask = HandlerMask.ON_INBOUND_MASK
                | HandlerMask.ON_EVENT_MASK;

        assertEquals(mask, HandlerMask.mask(SimpleInboundEventAwareHandler.class));
    }
}
