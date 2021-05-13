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
package org.drasyl.pipeline;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.pipeline.skeleton.HandlerAdapter;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbeddedPipelineTest {
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    private DrasylConfig config;

    @BeforeEach
    void setUp() {
        config = DrasylConfig.newBuilder()
                .networkId(1)
                .build();
    }

    @SuppressWarnings("rawtypes")
    @Test
    void shouldReturnInboundMessagesAndEvents(@Mock final IdentityPublicKey sender,
                                              @Mock final RemoteEnvelope msg) {
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager)) {
            final TestObserver<AddressedEnvelope<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessagesWithSender().test();
            final TestObserver<Object> outboundMessageTestObserver = pipeline.outboundMessages().test();
            final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

            pipeline.processInbound(sender, msg);

            inboundMessageTestObserver.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue(new DefaultAddressedEnvelope<>(sender, null, msg));
            eventTestObserver.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue(MessageEvent.of(sender, msg));
            outboundMessageTestObserver.assertNoValues();
        }
    }

    @Test
    void shouldReturnOutboundMessages(@Mock final IdentityPublicKey sender,
                                      @Mock final IdentityPublicKey recipient) {
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                peersManager,
                new HandlerAdapter(),
                new HandlerAdapter()
        )) {
            final TestObserver<Object> inboundMessageTestObserver = pipeline.inboundMessages().test();
            final TestObserver<Object> outboundMessageTestObserver = pipeline.outboundMessages().test();
            final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

            final byte[] msg = new byte[]{};
            pipeline.processOutbound(recipient, msg);

            outboundMessageTestObserver.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue(msg);
            inboundMessageTestObserver.assertNoValues();
            eventTestObserver.assertNoValues();
        }
    }
}
