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
package org.drasyl.pipeline.skeleton;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.DefaultEmbeddedPipeline;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.remote.protocol.AcknowledgementMessage;
import org.drasyl.remote.protocol.DiscoveryMessage;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.remote.protocol.RemoteMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.util.concurrent.CompletableFuture;

@ExtendWith(MockitoExtension.class)
class SimpleDuplexRemoteMessageSkipLoopbackHandlerTest {
    @Mock
    private PeersManager peersManager;
    private DrasylConfig config;
    private Handler handler;
    private Nonce nonce;

    @BeforeEach
    void setUp() {
        config = DrasylConfig.newBuilder()
                .networkId(1)
                .build();
        handler = new SimpleDuplexRemoteMessageSkipLoopbackHandler<>() {
            @Override
            protected void filteredOutbound(final HandlerContext ctx,
                                            final Address recipient,
                                            final RemoteMessage msg,
                                            final CompletableFuture<Void> future) {
            }

            @Override
            protected void filteredInbound(final HandlerContext ctx,
                                           final Address sender,
                                           final RemoteMessage msg,
                                           final CompletableFuture<Void> future) {
            }
        };
        nonce = Nonce.randomNonce();
    }

    @Nested
    class Outbound {
        @Test
        void shouldSkipOnNullReceiver(@Mock final Address recipient) {
            try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final RemoteMessage msg = DiscoveryMessage.of(1, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork());

                final TestObserver<Object> testObserver = pipeline.outboundMessages().test();

                pipeline.processOutbound(recipient, msg);

                testObserver.awaitCount(1)
                        .assertValueCount(1);
            }
        }

        @Test
        void shouldNotSkipIfNotActive(@Mock final Address recipient) {
            handler = new SimpleDuplexRemoteMessageSkipLoopbackHandler<>(false) {
                @Override
                protected void filteredOutbound(final HandlerContext ctx,
                                                final Address recipient,
                                                final RemoteMessage msg,
                                                final CompletableFuture<Void> future) {
                }

                @Override
                protected void filteredInbound(final HandlerContext ctx,
                                               final Address sender,
                                               final RemoteMessage msg,
                                               final CompletableFuture<Void> future) {
                }
            };

            try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final RemoteMessage msg = DiscoveryMessage.of(1, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork());

                final TestObserver<Object> testObserver = pipeline.outboundMessages().test();

                pipeline.processOutbound(recipient, msg);

                testObserver.awaitCount(1)
                        .assertNoValues();
            }
        }

        @Test
        void shouldSkipIfMessageComesNotFromMe(@Mock final Address recipient) {
            try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final RemoteMessage msg = AcknowledgementMessage.of(1, IdentityTestUtil.ID_2.getIdentityPublicKey(), IdentityTestUtil.ID_2.getProofOfWork(), IdentityTestUtil.ID_1.getIdentityPublicKey(), nonce);

                final TestObserver<Object> testObserver = pipeline.outboundMessages().test();

                pipeline.processOutbound(recipient, msg);

                testObserver.awaitCount(1)
                        .assertValueCount(1);
            }
        }

        @Test
        void shouldSkipIfMessageIsForMe(@Mock final Address recipient) {
            try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final RemoteMessage msg = AcknowledgementMessage.of(1, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), IdentityTestUtil.ID_1.getIdentityPublicKey(), nonce);

                final TestObserver<Object> testObserver = pipeline.outboundMessages().test();

                pipeline.processOutbound(recipient, msg);

                testObserver.awaitCount(1)
                        .assertValueCount(1);
            }
        }
    }

    @Nested
    class Inbound {
        @Test
        void shouldSkipOnNullReceiver(@Mock final Address sender) {
            try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final RemoteMessage msg = DiscoveryMessage.of(1, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork());

                final TestObserver<Object> testObserver = pipeline.inboundMessages().test();

                pipeline.processInbound(sender, msg);

                testObserver.awaitCount(1)
                        .assertValueCount(1);
            }
        }

        @Test
        void shouldSkipIfMessageComesFromMe(@Mock final Address sender) {
            try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final RemoteMessage msg = AcknowledgementMessage.of(1, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), IdentityTestUtil.ID_2.getIdentityPublicKey(), nonce);

                final TestObserver<Object> testObserver = pipeline.inboundMessages().test();

                pipeline.processInbound(sender, msg);

                testObserver.awaitCount(1)
                        .assertValueCount(1);
            }
        }

        @Test
        void shouldSkipIfMessageIsNotForMe(@Mock final Address sender) {
            try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final RemoteMessage msg = AcknowledgementMessage.of(1, IdentityTestUtil.ID_2.getIdentityPublicKey(), IdentityTestUtil.ID_2.getProofOfWork(), IdentityTestUtil.ID_2.getIdentityPublicKey(), nonce);

                final TestObserver<Object> testObserver = pipeline.inboundMessages().test();

                pipeline.processInbound(sender, msg);

                testObserver.awaitCount(1)
                        .assertValueCount(1);
            }
        }
    }
}
