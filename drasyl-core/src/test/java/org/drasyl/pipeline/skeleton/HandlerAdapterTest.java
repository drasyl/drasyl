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
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.channel.MigrationHandlerContext;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.remote.protocol.RemoteMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class HandlerAdapterTest {
    @Mock
    private MigrationHandlerContext ctx;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private CompletableFuture<Void> future;

    @Test
    void shouldDoNothing() {
        final HandlerAdapter adapter = new HandlerAdapter() {
            @Override
            public void onAdded(final MigrationHandlerContext ctx) {
                super.onAdded(ctx);
            }

            @Override
            public void onRemoved(final MigrationHandlerContext ctx) {
                super.onRemoved(ctx);
            }
        };

        adapter.onAdded(ctx);
        adapter.onRemoved(ctx);

        verifyNoInteractions(ctx);
    }

    @Nested
    class Outbound {
        @Test
        void shouldPassthroughsOnWrite(@Mock final IdentityPublicKey recipient,
                                       @Mock final Object msg) throws Exception {
            final HandlerAdapter handlerAdapter = new HandlerAdapter();

            handlerAdapter.onOutbound(ctx, recipient, msg, future);

            verify(ctx).passOutbound(recipient, msg, future);
        }
    }

    @Nested
    class Inbound {
        @Test
        void shouldPassthroughsOnRead(@Mock final IdentityPublicKey sender,
                                      @Mock final Object msg) throws Exception {
            final HandlerAdapter handlerAdapter = new HandlerAdapter();

            handlerAdapter.onInbound(ctx, sender, msg, future);

            verify(ctx).fireChannelRead(any());
        }

        @Test
        void shouldPassthroughsOnEventTriggered(@Mock final Event event) {
            final HandlerAdapter handlerAdapter = new HandlerAdapter();

            handlerAdapter.onEvent(ctx, event, future);

            verify(ctx).passEvent(event, future);
        }

        @Test
        void shouldPassthroughsOnExceptionCaught(@Mock final Exception exception) {
            final HandlerAdapter handlerAdapter = new HandlerAdapter();

            handlerAdapter.onException(ctx, exception);

            verify(ctx).fireExceptionCaught(exception);
        }

        @Test
        void shouldPassthroughsOnEventTriggeredWithMultipleHandler(@Mock final Event event) {
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, IntStream.rangeClosed(1, 10).mapToObj(i -> new HandlerAdapter()).toArray(HandlerAdapter[]::new));
            try {
                final TestObserver<Event> events = pipeline.inboundEvents().test();

                pipeline.processInbound(event);

                events.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(event);
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldPassthroughsOnReadWithMultipleHandler(@Mock final IdentityPublicKey sender,
                                                         @Mock final RemoteMessage msg) {
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, IntStream.rangeClosed(1, 10).mapToObj(i -> new HandlerAdapter()).toArray(HandlerAdapter[]::new));
            try {
                final TestObserver<AddressedEnvelope<Address, Object>> inboundMessages = pipeline.inboundMessagesWithSender().test();

                pipeline.processInbound(sender, msg);

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new DefaultAddressedEnvelope<>(sender, null, msg));
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }
}
