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
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.HandlerAdapter;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import static org.drasyl.util.network.NetworkUtil.createInetAddress;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DrasylPipelineIT {
    private PublishSubject<Event> receivedEvents;
    private PublishSubject<Object> outboundMessages;
    private Pipeline pipeline;
    private Identity identity1;
    private Identity identity2;
    private byte[] payload;

    @BeforeEach
    void setup() {
        receivedEvents = PublishSubject.create();
        outboundMessages = PublishSubject.create();

        identity1 = Identity.of(169092, "030a59784f88c74dcd64258387f9126739c3aeb7965f36bb501ff01f5036b3d72b", "0f1e188d5e3b98daf2266d7916d2e1179ae6209faa7477a2a66d4bb61dab4399");
        identity2 = Identity.of(26778671, "0236fde6a49564a0eaa2a7d6c8f73b97062d5feb36160398c08a5b73f646aa5fe5", "093d1ee70518508cac18eaf90d312f768c14d43de9bfd2618a2794d8df392da0");

        payload = new byte[]{
                0x01,
                0x02,
                0x03
        };

        final DrasylConfig config = DrasylConfig.newBuilder()
                .networkId(0)
                .identityProofOfWork(identity1.getProofOfWork())
                .identityPublicKey(identity1.getPublicKey())
                .identityPrivateKey(identity1.getPrivateKey())
                .remoteExposeEnabled(false)
                .remoteSuperPeerEnabled(false)
                .remoteLocalHostDiscoveryEnabled(false)
                .remoteBindHost(createInetAddress("127.0.0.1"))
                .remoteBindPort(0)
                .remoteTcpFallbackEnabled(false)
                .build();

        final PeersManager peersManager = new PeersManager(receivedEvents::onNext, identity1);
        pipeline = new DrasylPipeline(receivedEvents::onNext, config, identity1, peersManager);
        pipeline.addFirst("outboundMessages", new SimpleOutboundHandler<>() {
            @Override
            protected void matchedOutbound(final HandlerContext ctx,
                                           final Address recipient,
                                           final Object msg,
                                           final CompletableFuture<Void> future) {
                if (!future.isDone()) {
                    outboundMessages.onNext(msg);
                    future.complete(null);
                }
            }
        });
    }

    @AfterEach
    void tearDown() throws ExecutionException, InterruptedException {
        pipeline.processInbound(NodeDownEvent.of(Node.of(identity1))).get();
    }

    @Test
    void passMessageThroughThePipeline() throws IOException {
        final TestObserver<Event> events = receivedEvents.test();

        final byte[] newPayload = new byte[]{
                0x05
        };

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("handler" + i, new HandlerAdapter()));

        pipeline.addLast("msgChanger", new HandlerAdapter() {
            @Override
            public void onInbound(final HandlerContext ctx,
                                  final Address sender,
                                  final Object msg,
                                  final CompletableFuture<Void> future) throws Exception {
                super.onInbound(ctx, identity2.getPublicKey(), newPayload, future);
            }
        });

        try (final RemoteEnvelope<Application> message = RemoteEnvelope.application(0, identity2.getPublicKey(), identity2.getProofOfWork(), identity1.getPublicKey(), byte[].class.getName(), new byte[]{}).armAndRelease(identity2.getPrivateKey())) {
            pipeline.processInbound(message.getSender(), message);

            events.awaitCount(1).assertValueCount(1);
            events.assertValue(MessageEvent.of(identity2.getPublicKey(), newPayload));
        }
    }

    @Test
    void passEventThroughThePipeline() throws ExecutionException, InterruptedException, IOException {
        final TestObserver<Event> events = receivedEvents.test();

        final Event testEvent = new Event() {
        };

        // we need to start the node, otherwise LoopbackInboundMessageSinkHandler will drop our message
        pipeline.processInbound(NodeUpEvent.of(Node.of(identity1))).get();
        IntStream.range(0, 10).forEach(i -> pipeline.addLast("handler" + i, new HandlerAdapter()));

        pipeline.addLast("eventProducer", new HandlerAdapter() {
            @Override
            public void onInbound(final HandlerContext ctx,
                                  final Address sender,
                                  final Object msg,
                                  final CompletableFuture<Void> future) throws Exception {
                super.onInbound(ctx, sender, msg, future);
                ctx.passEvent(testEvent, new CompletableFuture<>());
            }
        });

        try (final RemoteEnvelope<Application> message = RemoteEnvelope.application(0, identity2.getPublicKey(), identity2.getProofOfWork(), identity1.getPublicKey(), byte[].class.getName(), "Hallo Welt".getBytes()).armAndRelease(identity2.getPrivateKey())) {
            pipeline.processInbound(message.getSender(), message);

            events.awaitCount(3);
            events.assertValueAt(1, MessageEvent.of(message.getSender(), "Hallo Welt".getBytes()));
            events.assertValueAt(2, testEvent);
        }
    }

    @Test
    void exceptionShouldPassThroughThePipeline() throws IOException {
        final PublishSubject<Throwable> receivedExceptions = PublishSubject.create();
        final TestObserver<Throwable> exceptions = receivedExceptions.test();

        final RuntimeException exception = new RuntimeException("Error!");

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("handler" + i, new HandlerAdapter()));

        pipeline.addFirst("exceptionCatcher", new HandlerAdapter() {
            @Override
            public void onException(final HandlerContext ctx, final Exception cause) {
                exceptions.onNext(cause);
                super.onException(ctx, cause);
            }
        });

        pipeline.addFirst("exceptionProducer", new HandlerAdapter() {
            @Override
            public void onInbound(final HandlerContext ctx,
                                  final Address sender,
                                  final Object msg,
                                  final CompletableFuture<Void> future) throws Exception {
                super.onInbound(ctx, sender, msg, future);
                throw exception;
            }
        });

        try (final RemoteEnvelope<Application> message = RemoteEnvelope.application(0, identity2.getPublicKey(), identity2.getProofOfWork(), identity1.getPublicKey(), byte[].class.getName(), new byte[]{}).armAndRelease(identity2.getPrivateKey())) {
            pipeline.processInbound(message.getSender(), message);

            exceptions.awaitCount(1).assertValueCount(1);
            exceptions.assertValue(exception);
        }
    }

    @Test
    void passOutboundThroughThePipeline() {
        final TestObserver<Object> outbounds = outboundMessages.test();

        final byte[] newPayload = new byte[]{
                0x05
        };

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("handler" + i, new HandlerAdapter()));

        pipeline.addLast("outboundChanger", new HandlerAdapter() {
            @Override
            public void onOutbound(final HandlerContext ctx,
                                   final Address recipient,
                                   final Object msg,
                                   final CompletableFuture<Void> future) throws Exception {
                super.onOutbound(ctx, identity2.getPublicKey(), newPayload, future);
            }
        });

        final CompletableFuture<Void> future = pipeline.processOutbound(identity1.getPublicKey(), payload);

        outbounds.awaitCount(1).assertValueCount(1);
        outbounds.assertValue(m -> m instanceof RemoteEnvelope);
        future.join();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertFalse(future.isCompletedExceptionally());
    }

    @Test
    void shouldNotPassthroughsMessagesWithDoneFuture() {
        final TestObserver<Object> outbounds = outboundMessages.test();
        final CompletableFuture<Void> future;
        try (final RemoteEnvelope<Application> msg = RemoteEnvelope.application(0, identity1.getPublicKey(), identity1.getProofOfWork(), identity2.getPublicKey(), byte[].class.getName(), payload)) {
            IntStream.range(0, 10).forEach(i -> pipeline.addLast("handler" + i, new HandlerAdapter()));

            pipeline.addLast("outbound", new HandlerAdapter() {
                @Override
                public void onOutbound(final HandlerContext ctx,
                                       final Address recipient,
                                       final Object msg,
                                       final CompletableFuture<Void> future) throws Exception {
                    future.complete(null);
                    super.onOutbound(ctx, recipient, msg, future);
                }
            });

            pipeline.processOutbound(identity2.getPublicKey(), msg).join();

            outbounds.assertNoValues();
        }
    }

    @Test
    void shouldNotPassthroughsMessagesWithExceptionallyFuture() {
        final TestObserver<Object> outbounds = outboundMessages.test();
        try (final RemoteEnvelope<Application> msg = RemoteEnvelope.application(0, identity1.getPublicKey(), identity1.getProofOfWork(), identity2.getPublicKey(), byte[].class.getName(), payload)) {
            IntStream.range(0, 10).forEach(i -> pipeline.addLast("handler" + i, new HandlerAdapter()));

            pipeline.addLast("outbound", new HandlerAdapter() {
                @Override
                public void onOutbound(final HandlerContext ctx,
                                       final Address recipient,
                                       final Object msg,
                                       final CompletableFuture<Void> future) throws Exception {
                    future.completeExceptionally(new Exception("Error!"));
                    super.onOutbound(ctx, recipient, msg, future);
                }
            });

            assertThrows(CompletionException.class, pipeline.processOutbound(identity2.getPublicKey(), msg)::join);
            outbounds.assertNoValues();
        }
    }
}
