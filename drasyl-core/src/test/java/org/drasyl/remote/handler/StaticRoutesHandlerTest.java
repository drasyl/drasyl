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
package org.drasyl.remote.handler;

import com.google.common.collect.ImmutableMap;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.DefaultEmbeddedPipeline;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaticRoutesHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock
    private PeersManager peersManager;

    @Test
    void shouldPopulateRoutesOnNodeUpEvent(@Mock final NodeUpEvent event,
                                           @Mock final IdentityPublicKey publicKey) {
        final InetSocketAddressWrapper address = new InetSocketAddressWrapper(22527);
        when(config.getRemoteStaticRoutes()).thenReturn(ImmutableMap.of(publicKey, address));

        try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, StaticRoutesHandler.INSTANCE)) {
            pipeline.processInbound(event).join();

            verify(peersManager).addPath(any(), eq(publicKey), any());
        }
    }

    @Test
    void shouldClearRoutesOnNodeDownEvent(@Mock final NodeDownEvent event,
                                          @Mock final IdentityPublicKey publicKey,
                                          @Mock final InetSocketAddressWrapper address) {
        when(config.getRemoteStaticRoutes()).thenReturn(ImmutableMap.of(publicKey, address));

        try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, StaticRoutesHandler.INSTANCE)) {
            pipeline.processInbound(event).join();

            verify(peersManager).removePath(any(), eq(publicKey), any());
        }
    }

    @Test
    void shouldClearRoutesOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event,
                                                        @Mock final IdentityPublicKey publicKey,
                                                        @Mock final InetSocketAddressWrapper address) {
        when(config.getRemoteStaticRoutes()).thenReturn(ImmutableMap.of(publicKey, address));

        try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, StaticRoutesHandler.INSTANCE)) {
            pipeline.processInbound(event).join();

            verify(peersManager).removePath(any(), eq(publicKey), any());
        }
    }

    @Test
    void shouldRouteOutboundMessageWhenStaticRouteIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
        final InetSocketAddressWrapper address = new InetSocketAddressWrapper(22527);
        final IdentityPublicKey publicKey = IdentityTestUtil.ID_2.getIdentityPublicKey();
        when(config.getRemoteStaticRoutes()).thenReturn(ImmutableMap.of(publicKey, address));

        final TestObserver<AddressedEnvelope<Address, Object>> outboundMessages;
        try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, StaticRoutesHandler.INSTANCE)) {
            outboundMessages = pipeline.outboundMessagesWithRecipient().test();

            pipeline.processOutbound(publicKey, message).join();

            outboundMessages.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue(new DefaultAddressedEnvelope<>(null, address, message));
        }
    }

    @Test
    void shouldPassthroughMessageWhenStaticRouteIsAbsent(@Mock final IdentityPublicKey publicKey,
                                                         @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
        when(config.getRemoteStaticRoutes()).thenReturn(ImmutableMap.of());

        final TestObserver<AddressedEnvelope<Address, Object>> outboundMessages;
        try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, StaticRoutesHandler.INSTANCE)) {
            outboundMessages = pipeline.outboundMessagesWithRecipient().test();

            pipeline.processOutbound(publicKey, message).join();

            outboundMessages.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue(new DefaultAddressedEnvelope<>(null, publicKey, message));
        }
    }
}
