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
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ChunkedMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class SimpleOutboundHandlerTest {
    @Test
    void shouldTriggerOnMatchedMessage() {
        SimpleOutboundHandler<ChunkedMessage> handler = new SimpleOutboundHandler<>() {
            @Override
            protected void matchedWrite(HandlerContext ctx,
                                        ChunkedMessage msg,
                                        CompletableFuture<Void> future) {
                // Emit this message as inbound message to test
                ctx.pipeline().processInbound(msg);
            }
        };

        EmbeddedPipeline pipeline = new EmbeddedPipeline(handler);
        TestObserver<ApplicationMessage> inboundMessageTestObserver = pipeline.inboundMessages().test();
        TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages().test();

        ChunkedMessage msg = mock(ChunkedMessage.class);
        pipeline.processOutbound(msg);

        inboundMessageTestObserver.awaitCount(1);
        inboundMessageTestObserver.assertValue(msg);
        outboundMessageTestObserver.assertNoValues();
    }

    @Test
    void shouldPassthroughsNotMatchingMessage() {
        SimpleOutboundHandler<ChunkedMessage> handler = new SimpleOutboundHandler<>(ChunkedMessage.class) {
            @Override
            protected void matchedWrite(HandlerContext ctx,
                                        ChunkedMessage msg,
                                        CompletableFuture<Void> future) {
                // Emit this message as inbound message to test
                ctx.pipeline().processInbound(msg);
            }
        };

        EmbeddedPipeline pipeline = new EmbeddedPipeline(handler);
        TestObserver<ApplicationMessage> inboundMessageTestObserver = pipeline.inboundMessages().test();
        TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages().test();

        ApplicationMessage msg = mock(ApplicationMessage.class);
        pipeline.processOutbound(msg);

        outboundMessageTestObserver.awaitCount(1);
        outboundMessageTestObserver.assertValue(msg);
        inboundMessageTestObserver.assertNoValues();
    }
}