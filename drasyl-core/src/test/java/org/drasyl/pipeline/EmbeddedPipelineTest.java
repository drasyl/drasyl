/*
 * Copyright (c) 2020.
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
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.pipeline.codec.DefaultCodec;
import org.drasyl.pipeline.codec.ObjectHolder;
import org.drasyl.pipeline.codec.ObjectHolder2ApplicationMessageHandler;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddedPipelineTest {
    @Mock
    private Identity identity;
    private DrasylConfig config;

    @BeforeEach
    void setUp() {
        config = DrasylConfig.newBuilder().build();
    }

    @Test
    void shouldReturnInboundMessagesAndEvents() {
        EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, TypeValidator.of(config), ObjectHolder2ApplicationMessageHandler.INSTANCE, DefaultCodec.INSTANCE, new HandlerAdapter(), new HandlerAdapter());
        TestObserver<Pair<CompressedPublicKey, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
        TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages().test();
        TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        CompressedPublicKey sender = mock(CompressedPublicKey.class);
        ApplicationMessage msg = mock(ApplicationMessage.class);

        when(msg.getSender()).thenReturn(sender);
        doReturn(String.class.getName()).when(msg).getHeader(ObjectHolder.CLASS_KEY_NAME);
        when(msg.getPayload()).thenReturn(new byte[]{
                34,
                72,
                101,
                108,
                108,
                111,
                32,
                87,
                111,
                114,
                108,
                100,
                34
        });

        pipeline.processInbound(msg);

        inboundMessageTestObserver.awaitCount(1);
        inboundMessageTestObserver.assertValue(Pair.of(sender, "Hello World"));
        eventTestObserver.awaitCount(1);
        eventTestObserver.assertValue(new MessageEvent(Pair.of(sender, "Hello World")));
        outboundMessageTestObserver.assertNoValues();
    }

    @Test
    void shouldReturnOutboundMessages() {
        EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, TypeValidator.of(config), ObjectHolder2ApplicationMessageHandler.INSTANCE, DefaultCodec.INSTANCE, new HandlerAdapter(), new HandlerAdapter());
        TestObserver<Pair<CompressedPublicKey, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
        TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages().test();
        TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        CompressedPublicKey sender = mock(CompressedPublicKey.class);
        CompressedPublicKey recipient = mock(CompressedPublicKey.class);
        when(identity.getPublicKey()).thenReturn(sender);
        byte[] msg = new byte[]{};
        pipeline.processOutbound(recipient, msg);

        outboundMessageTestObserver.awaitCount(1);
        outboundMessageTestObserver.assertValue(new ApplicationMessage(sender, recipient, Map.of(ObjectHolder.CLASS_KEY_NAME, msg.getClass().getName()), msg));
        inboundMessageTestObserver.assertNoValues();
        eventTestObserver.assertNoValues();
    }
}