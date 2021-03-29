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

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaticRoutesHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private PeersManager peersManager;

    @Test
    void shouldPopulateRoutesOnNodeUpEvent(@Mock final NodeUpEvent event,
                                           @Mock final CompressedPublicKey publicKey) {
        final InetSocketAddressWrapper address = new InetSocketAddressWrapper(22527);
        when(config.getRemoteStaticRoutes()).thenReturn(Map.of(publicKey, address));

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, StaticRoutesHandler.INSTANCE);

        pipeline.processInbound(event).join();

        verify(peersManager, timeout(1_000)).addPath(eq(publicKey), any());
    }

    @Test
    void shouldClearRoutesOnNodeDownEvent(@Mock final NodeDownEvent event,
                                          @Mock final CompressedPublicKey publicKey,
                                          @Mock final InetSocketAddressWrapper address) {
        when(config.getRemoteStaticRoutes()).thenReturn(Map.of(publicKey, address));

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, StaticRoutesHandler.INSTANCE);

        pipeline.processInbound(event).join();

        verify(peersManager, timeout(1_000)).removePath(eq(publicKey), any());
    }

    @Test
    void shouldClearRoutesOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event,
                                                        @Mock final CompressedPublicKey publicKey,
                                                        @Mock final InetSocketAddressWrapper address) {
        when(config.getRemoteStaticRoutes()).thenReturn(Map.of(publicKey, address));

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, StaticRoutesHandler.INSTANCE);

        pipeline.processInbound(event).join();

        verify(peersManager, timeout(1_000)).removePath(eq(publicKey), any());
    }

    @SuppressWarnings("rawtypes")
    @Test
    void shouldRouteOutboundMessageWhenStaticRouteIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope message) {
        final InetSocketAddressWrapper address = new InetSocketAddressWrapper(22527);
        final CompressedPublicKey publicKey = CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
        when(config.getRemoteStaticRoutes()).thenReturn(Map.of(publicKey, address));
        when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"));
        when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(1));

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, StaticRoutesHandler.INSTANCE);
        final TestObserver<RemoteEnvelope> outboundMessages = pipeline.outboundMessages(RemoteEnvelope.class).test();

        pipeline.processOutbound(publicKey, message).join();

        outboundMessages.awaitCount(1)
                .assertValueCount(1);
    }

    @SuppressWarnings("rawtypes")
    @Test
    void shouldPassthroughMessageWhenStaticRouteIsAbsent(@Mock final CompressedPublicKey publicKey,
                                                         @Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope message) {
        when(config.getRemoteStaticRoutes()).thenReturn(Map.of());

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, StaticRoutesHandler.INSTANCE);
        final TestObserver<RemoteEnvelope> outboundMessages = pipeline.outboundMessages(RemoteEnvelope.class).test();

        pipeline.processOutbound(publicKey, message).join();

        outboundMessages.awaitCount(1);
    }
}
