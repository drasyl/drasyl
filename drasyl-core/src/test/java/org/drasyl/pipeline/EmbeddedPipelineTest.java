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
package org.drasyl.pipeline;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.handler.AddressedEnvelopeHandler;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.pipeline.serialization.SerializedApplicationMessage;
import org.drasyl.pipeline.skeleton.HandlerAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void shouldReturnInboundMessagesAndEvents() {
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager);
        final TestObserver<AddressedEnvelope<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessagesWithRecipient().test();
        final TestObserver<SerializedApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages(SerializedApplicationMessage.class).test();
        final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        final CompressedPublicKey sender = mock(CompressedPublicKey.class);
        final SerializedApplicationMessage msg = mock(SerializedApplicationMessage.class);

        when(msg.getSender()).thenReturn(sender);

        pipeline.processInbound(msg.getSender(), msg);

        inboundMessageTestObserver.awaitCount(1)
                .assertValueCount(1)
                .assertValue(new DefaultAddressedEnvelope<>(sender, null, msg));
        eventTestObserver.awaitCount(1)
                .assertValueCount(1)
                .assertValue(new MessageEvent(sender, msg));
        outboundMessageTestObserver.assertNoValues();
        pipeline.close();
    }

    @Test
    void shouldReturnOutboundMessages() {
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                peersManager,
                AddressedEnvelopeHandler.INSTANCE,
                new HandlerAdapter(),
                new HandlerAdapter()
        );
        final TestObserver<Object> inboundMessageTestObserver = pipeline.inboundMessages().test();
        final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages(ApplicationMessage.class).test();
        final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        final CompressedPublicKey sender = mock(CompressedPublicKey.class);
        final CompressedPublicKey recipient = mock(CompressedPublicKey.class);
        when(identity.getPublicKey()).thenReturn(sender);
        final byte[] msg = new byte[]{};
        pipeline.processOutbound(recipient, msg);

        outboundMessageTestObserver.awaitCount(1)
                .assertValueCount(1)
                .assertValue(new ApplicationMessage(sender, recipient, msg));
        inboundMessageTestObserver.assertNoValues();
        eventTestObserver.assertNoValues();
        pipeline.close();
    }
}
