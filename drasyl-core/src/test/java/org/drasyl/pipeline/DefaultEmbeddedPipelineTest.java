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

import io.netty.channel.ChannelHandlerAdapter;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.remote.protocol.RemoteMessage;
import org.drasyl.util.FutureUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultEmbeddedPipelineTest {
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

    @Test
    void shouldReturnInboundMessagesAndEvents(@Mock final IdentityPublicKey sender,
                                              @Mock final RemoteMessage msg) {
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager);
        try {
            final TestObserver<AddressedEnvelope<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessagesWithSender().test();
            final TestObserver<Object> outboundMessageTestObserver = pipeline.drasylOutboundMessages().test();
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
        finally {
            pipeline.drasylClose();
        }
    }

    @Test
    void shouldReturnOutboundMessages(@Mock final IdentityPublicKey sender,
                                      @Mock final IdentityPublicKey recipient) {
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(
                config,
                identity,
                peersManager,
                new ChannelHandlerAdapter() {
                },
                new ChannelHandlerAdapter() {
                }
        );
        try {
            final TestObserver<Object> inboundMessageTestObserver = pipeline.drasylInboundMessages().test();
            final TestObserver<Object> outboundMessageTestObserver = pipeline.drasylOutboundMessages().test();
            final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

            final byte[] msg = new byte[]{};
            FutureUtil.toFuture(pipeline.processOutbound(recipient, msg));

            outboundMessageTestObserver.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue(msg);
            inboundMessageTestObserver.assertNoValues();
            eventTestObserver.assertNoValues();
        }
        finally {
            pipeline.drasylClose();
        }
    }
}
