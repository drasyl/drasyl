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

import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrasylPipelineIT {
    private PublishSubject<Event> receivedEvents;
    private PublishSubject<ApplicationMessage> outboundMessages;
    private DrasylPipeline pipeline;
    private Identity identity1;
    private Identity identity2;
    private byte[] payload;

    @BeforeEach
    void setup() throws CryptoException {
        receivedEvents = PublishSubject.create();
        outboundMessages = PublishSubject.create();

        identity1 = Identity.of(169092, "030a59784f88c74dcd64258387f9126739c3aeb7965f36bb501ff01f5036b3d72b", "0f1e188d5e3b98daf2266d7916d2e1179ae6209faa7477a2a66d4bb61dab4399");
        identity2 = Identity.of(26778671, "0236fde6a49564a0eaa2a7d6c8f73b97062d5feb36160398c08a5b73f646aa5fe5", "093d1ee70518508cac18eaf90d312f768c14d43de9bfd2618a2794d8df392da0");

        payload = new byte[]{
                0x01,
                0x02,
                0x03
        };

        DrasylConfig config = DrasylConfig.newBuilder()
                .identityProofOfWork(identity1.getProofOfWork())
                .identityPublicKey(identity1.getPublicKey())
                .identityPrivateKey(identity1.getPrivateKey())
                .build();

        pipeline = new DrasylPipeline(receivedEvents::onNext, outboundMessages::onNext, config, identity1);
    }

    @Test
    void passMessageThroughThePipeline() {
        TestObserver<Event> events = receivedEvents.test();

        byte[] newPayload = new byte[]{
                0x05
        };

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("inboundHandler" + i, new InboundHandlerAdapter()));
        IntStream.range(0, 10).forEach(i -> pipeline.addLast("outboundHandler" + i, new OutboundHandlerAdapter()));

        pipeline.addLast("msgChanger", new InboundHandlerAdapter() {
            @Override
            public void read(HandlerContext ctx, CompressedPublicKey sender, Object msg) {
                super.read(ctx, identity2.getPublicKey(), newPayload);
            }
        });

        ApplicationMessage msg = new ApplicationMessage(identity1.getPublicKey(), identity2.getPublicKey(), payload, payload.getClass());

        pipeline.processInbound(msg);

        events.awaitCount(1);
        events.assertValue(new MessageEvent(Pair.of(identity2.getPublicKey(), newPayload)));
    }

    @Test
    void passEventThroughThePipeline() {
        TestObserver<Event> events = receivedEvents.test();

        Event testEvent = new Event() {
        };

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("inboundHandler" + i, new InboundHandlerAdapter()));
        IntStream.range(0, 10).forEach(i -> pipeline.addLast("outboundHandler" + i, new OutboundHandlerAdapter()));

        pipeline.addLast("eventProducer", new InboundHandlerAdapter() {
            @Override
            public void read(HandlerContext ctx, CompressedPublicKey sender, Object msg) {
                super.read(ctx, sender, msg);
                ctx.fireEventTriggered(testEvent);
            }
        });

        ApplicationMessage msg = new ApplicationMessage(identity1.getPublicKey(), identity2.getPublicKey(), payload, payload.getClass());

        pipeline.processInbound(msg);

        events.awaitCount(2);
        events.assertValueAt(0, new MessageEvent(Pair.of(msg.getSender(), payload)));
        events.assertValueAt(1, testEvent);
    }

    @Test
    void exceptionShouldPassThroughThePipeline() {
        PublishSubject<Throwable> receivedExceptions = PublishSubject.create();
        TestObserver<Throwable> exceptions = receivedExceptions.test();

        RuntimeException exception = new RuntimeException("Error!");
        RxJavaPlugins.setErrorHandler(e -> {
            assertThat(e, instanceOf(UndeliverableException.class));
            assertThat(e.getCause(), instanceOf(PipelineException.class));
            assertThat(e.getCause().getCause(), instanceOf(RuntimeException.class));
            assertEquals(exception.getMessage(), e.getCause().getCause().getMessage());
        });

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("inboundHandler" + i, new InboundHandlerAdapter()));
        IntStream.range(0, 10).forEach(i -> pipeline.addLast("outboundHandler" + i, new OutboundHandlerAdapter()));

        pipeline.addLast("exceptionProducer", new InboundHandlerAdapter() {
            @Override
            public void read(HandlerContext ctx, CompressedPublicKey sender, Object msg) {
                super.read(ctx, sender, msg);
                throw exception;
            }
        });

        pipeline.addLast("exceptionCatcher", new InboundHandlerAdapter() {
            @Override
            public void exceptionCaught(HandlerContext ctx, Exception cause) {
                exceptions.onNext(cause);
                super.exceptionCaught(ctx, cause);
            }
        });

        ApplicationMessage msg = new ApplicationMessage(identity1.getPublicKey(), identity2.getPublicKey(), payload, payload.getClass());

        pipeline.processInbound(msg);

        exceptions.awaitCount(1);
        exceptions.assertValue(exception);
    }

    @Test
    void passOutboundThroughThePipeline() {
        TestObserver<ApplicationMessage> outbounds = outboundMessages.test();

        byte[] newPayload = new byte[]{
                0x05
        };

        ApplicationMessage newMsg = new ApplicationMessage(identity1.getPublicKey(), identity2.getPublicKey(), newPayload, newPayload.getClass());

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("inboundHandler" + i, new InboundHandlerAdapter()));
        IntStream.range(0, 10).forEach(i -> pipeline.addLast("outboundHandler" + i, new OutboundHandlerAdapter()));

        pipeline.addLast("outboundChanger", new OutboundHandlerAdapter() {
            @Override
            public void write(HandlerContext ctx,
                              CompressedPublicKey recipient,
                              Object msg,
                              CompletableFuture<Void> future) {
                super.write(ctx, identity2.getPublicKey(), newPayload, future);
            }
        });

        CompletableFuture<Void> future = pipeline.processOutbound(identity1.getPublicKey(), payload);

        outbounds.awaitCount(1);
        outbounds.assertValue(newMsg);
        future.join();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertFalse(future.isCompletedExceptionally());
    }

    @Test
    void shouldNotPassthroughsMessagesWithDoneFuture() {
        TestObserver<ApplicationMessage> outbounds = outboundMessages.test();
        ApplicationMessage msg = new ApplicationMessage(identity1.getPublicKey(), identity2.getPublicKey(), payload, payload.getClass());

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("inboundHandler" + i, new InboundHandlerAdapter()));
        IntStream.range(0, 10).forEach(i -> pipeline.addLast("outboundHandler" + i, new OutboundHandlerAdapter()));

        pipeline.addLast("outbound", new OutboundHandlerAdapter() {
            @Override
            public void write(HandlerContext ctx,
                              CompressedPublicKey recipient,
                              Object msg,
                              CompletableFuture<Void> future) {
                future.complete(null);
                super.write(ctx, recipient, msg, future);
            }
        });

        CompletableFuture<Void> future = pipeline.processOutbound(identity2.getPublicKey(), msg);

        outbounds.awaitCount(1);
        outbounds.assertNoValues();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertFalse(future.isCompletedExceptionally());
    }

    @Test
    void shouldNotPassthroughsMessagesWithExceptionallyFuture() {
        TestObserver<ApplicationMessage> outbounds = outboundMessages.test();
        ApplicationMessage msg = new ApplicationMessage(identity1.getPublicKey(), identity2.getPublicKey(), payload, payload.getClass());

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("inboundHandler" + i, new InboundHandlerAdapter()));
        IntStream.range(0, 10).forEach(i -> pipeline.addLast("outboundHandler" + i, new OutboundHandlerAdapter()));

        pipeline.addLast("outbound", new OutboundHandlerAdapter() {
            @Override
            public void write(HandlerContext ctx,
                              CompressedPublicKey recipient,
                              Object msg,
                              CompletableFuture<Void> future) {
                future.completeExceptionally(new Exception("Error!"));
                super.write(ctx, recipient, msg, future);
            }
        });

        CompletableFuture<Void> future = pipeline.processOutbound(identity2.getPublicKey(), msg);

        outbounds.awaitCount(1);
        outbounds.assertNoValues();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void shouldNotPassthroughsMessagesWithNotAllowedType() {
        TestObserver<ApplicationMessage> outbounds = outboundMessages.test();
        StringBuilder msg = new StringBuilder();

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("inboundHandler" + i, new InboundHandlerAdapter()));
        IntStream.range(0, 10).forEach(i -> pipeline.addLast("outboundHandler" + i, new OutboundHandlerAdapter()));

        pipeline.addLast("outbound", new OutboundHandlerAdapter() {
            @Override
            public void write(HandlerContext ctx,
                              CompressedPublicKey recipient,
                              Object msg,
                              CompletableFuture<Void> future) {
                super.write(ctx, recipient, msg, future);
            }
        });

        CompletableFuture<Void> future = pipeline.processOutbound(identity2.getPublicKey(), msg);

        outbounds.awaitCount(1);
        outbounds.assertNoValues();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertTrue(future.isCompletedExceptionally());
        assertThrows(CompletionException.class, future::join);
    }
}
