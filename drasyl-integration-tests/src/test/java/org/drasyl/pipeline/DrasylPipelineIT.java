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

import com.google.protobuf.ByteString;
import com.goterl.lazysodium.utils.SessionPair;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
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
import org.drasyl.remote.handler.crypto.AgreementId;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.remote.protocol.ArmedMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

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

        identity1 = IdentityTestUtil.ID_1;
        identity2 = IdentityTestUtil.ID_2;

        payload = new byte[]{
                0x01,
                0x02,
                0x03
        };

        final DrasylConfig config = DrasylConfig.newBuilder()
                .networkId(0)
                .identityProofOfWork(identity1.getProofOfWork())
                .identityPublicKey(identity1.getIdentityPublicKey())
                .identitySecretKey(identity1.getIdentitySecretKey())
                .remoteExposeEnabled(false)
                .remoteSuperPeerEnabled(false)
                .remoteLocalHostDiscoveryEnabled(false)
                .remoteBindHost(createInetAddress("127.0.0.1"))
                .remoteBindPort(0)
                .remoteLocalNetworkDiscoveryEnabled(false)
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
    void passMessageThroughThePipeline() throws IOException, CryptoException {
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
                super.onInbound(ctx, identity2.getIdentityPublicKey(), newPayload, future);
            }
        });

        final SessionPair sessionPair = Crypto.INSTANCE.generateSessionKeyPair(identity2.getKeyAgreementKeyPair(), identity1.getKeyAgreementPublicKey());
        final ArmedMessage message = ApplicationMessage.of(0, identity2.getIdentityPublicKey(), identity2.getProofOfWork(), identity1.getIdentityPublicKey(), byte[].class.getName(), ByteString.EMPTY)
                .setAgreementId(AgreementId.of(identity1.getKeyAgreementPublicKey(), identity2.getKeyAgreementPublicKey()))
                .arm(sessionPair);
        pipeline.processInbound(message.getSender(), message);

        events.awaitCount(1).assertValueCount(1);
        events.assertValue(MessageEvent.of(identity2.getIdentityPublicKey(), newPayload));
    }

    @Test
    void passEventThroughThePipeline() throws ExecutionException, InterruptedException, IOException, CryptoException {
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

        final SessionPair sessionPair = Crypto.INSTANCE.generateSessionKeyPair(identity2.getKeyAgreementKeyPair(), identity1.getKeyAgreementPublicKey());
        final ArmedMessage message = ApplicationMessage.of(0, identity2.getIdentityPublicKey(), identity2.getProofOfWork(), identity1.getIdentityPublicKey(), byte[].class.getName(), ByteString.copyFromUtf8("Hallo Welt"))
                .setAgreementId(AgreementId.of(identity1.getKeyAgreementPublicKey(), identity2.getKeyAgreementPublicKey()))
                .arm(sessionPair);
        pipeline.processInbound(message.getSender(), message);

        events.awaitCount(3).assertValueCount(3);
        events.assertValueAt(1, MessageEvent.of(message.getSender(), "Hallo Welt".getBytes()));
        events.assertValueAt(2, testEvent);
    }

    @Test
    void exceptionShouldPassThroughThePipeline() throws IOException, CryptoException {
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

        final SessionPair sessionPair = Crypto.INSTANCE.generateSessionKeyPair(identity1.getKeyAgreementKeyPair(), identity2.getKeyAgreementPublicKey());
        final ArmedMessage message = ApplicationMessage.of(0, identity2.getIdentityPublicKey(), identity2.getProofOfWork(), identity1.getIdentityPublicKey(), byte[].class.getName(), ByteString.EMPTY)
                .setAgreementId(AgreementId.of(identity1.getKeyAgreementPublicKey(), identity2.getKeyAgreementPublicKey()))
                .arm(sessionPair);
        pipeline.processInbound(message.getSender(), message);

        exceptions.awaitCount(1).assertValueCount(1);
        exceptions.assertValue(exception);
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
                super.onOutbound(ctx, identity2.getIdentityPublicKey(), newPayload, future);
            }
        });

        final CompletableFuture<Void> future = pipeline.processOutbound(identity1.getIdentityPublicKey(), payload);

        outbounds.awaitCount(2).assertValueCount(2); // the second one comes from the key exchange
        future.join();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertFalse(future.isCompletedExceptionally());
    }

    @Test
    void shouldNotPassthroughsMessagesWithDoneFuture() {
        final TestObserver<Object> outbounds = outboundMessages.test();
        final CompletableFuture<Void> future;
        final ApplicationMessage msg = ApplicationMessage.of(0, identity1.getIdentityPublicKey(), identity1.getProofOfWork(), identity2.getIdentityPublicKey(), byte[].class.getName(), ByteString.copyFrom(payload));
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

        pipeline.processOutbound(identity2.getIdentityPublicKey(), msg).join();

        outbounds.assertNoValues();
    }

    @Test
    void shouldNotPassthroughsMessagesWithExceptionallyFuture() {
        final TestObserver<Object> outbounds = outboundMessages.test();
        final ApplicationMessage msg = ApplicationMessage.of(0, identity1.getIdentityPublicKey(), identity1.getProofOfWork(), identity2.getIdentityPublicKey(), byte[].class.getName(), ByteString.copyFrom(payload));
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

        assertThrows(CompletionException.class, pipeline.processOutbound(identity2.getIdentityPublicKey(), msg)::join);
        outbounds.assertNoValues();
    }
}
