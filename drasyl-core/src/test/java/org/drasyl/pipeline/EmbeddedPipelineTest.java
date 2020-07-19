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
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class EmbeddedPipelineTest {
    @Test
    void shouldReturnInboundMessagesAndEvents() {
        EmbeddedPipeline pipeline = new EmbeddedPipeline(new InboundHandlerAdapter(), new OutboundHandlerAdapter());
        TestObserver<ApplicationMessage> inboundMessageTestObserver = pipeline.inboundMessages().test();
        TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages().test();
        TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        ApplicationMessage msg = mock(ApplicationMessage.class);
        pipeline.processInbound(msg);

        inboundMessageTestObserver.awaitCount(1);
        inboundMessageTestObserver.assertValue(msg);
        eventTestObserver.awaitCount(1);
        eventTestObserver.assertValue(new MessageEvent(Pair.of(msg.getSender(), msg.getPayload())));
        outboundMessageTestObserver.assertNoValues();
    }

    @Test
    void shouldReturnOutboundMessages() {
        EmbeddedPipeline pipeline = new EmbeddedPipeline(new InboundHandlerAdapter(), new OutboundHandlerAdapter());
        TestObserver<ApplicationMessage> inboundMessageTestObserver = pipeline.inboundMessages().test();
        TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages().test();
        TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        ApplicationMessage msg = mock(ApplicationMessage.class);
        pipeline.processOutbound(msg);

        outboundMessageTestObserver.awaitCount(1);
        outboundMessageTestObserver.assertValue(msg);
        inboundMessageTestObserver.assertNoValues();
        eventTestObserver.assertNoValues();
    }
}