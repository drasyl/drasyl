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
package org.drasyl.remote.handler;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.remote.handler.LocalNetworkDiscovery.Peer;
import org.drasyl.remote.protocol.InvalidMessageFormatException;
import org.drasyl.remote.protocol.Protocol;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.time.Duration.ofSeconds;
import static org.drasyl.remote.handler.UdpMulticastServer.MULTICAST_ADDRESS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalNetworkDiscoveryTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Map<CompressedPublicKey, Peer> peers;
    @Mock
    private Disposable pingDisposable;

    @Nested
    class EventHandling {
        @Test
        void shouldStartHeartbeatingOnNodeUpEvent(@Mock final NodeUpEvent event) {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(peers, null));
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(handler).startHeartbeat(any());
            }
        }

        @Test
        void shouldStopHeartbeatingAndClearRoutesOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event) {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(peers, pingDisposable));
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(handler).stopHeartbeat();
                verify(handler).clearRoutes(any());
            }
        }

        @Test
        void shouldStopHeartbeatingAndClearRoutesOnNodeDownEvent(@Mock final NodeDownEvent event) {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(peers, pingDisposable));
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(handler).stopHeartbeat();
                verify(handler).clearRoutes(any());
            }
        }
    }

    @Nested
    class StartHeartbeat {
        @Test
        void shouldScheduleHeartbeat(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx) {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(peers, null));

            handler.startHeartbeat(ctx);

            verify(ctx.independentScheduler()).schedulePeriodicallyDirect(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        }
    }

    @Nested
    class DoHeartbeat {
        @SuppressWarnings("unchecked")
        @Test
        void shouldRemoveStalePeersAndPingLocalNetworkPeers(@Mock final CompressedPublicKey publicKey,
                                                            @Mock final Peer peer,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx) {
            when(ctx.identity().getPublicKey()).thenReturn(CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"));
            when(ctx.identity().getProofOfWork()).thenReturn(ProofOfWork.of(42));
            when(peer.isStale(any())).thenReturn(true);

            final HashMap<CompressedPublicKey, Peer> peers = new HashMap<>(Map.of(publicKey, peer));
            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, pingDisposable);
            handler.doHeartbeat(ctx);

            verify(peer).isStale(any());
            verify(ctx.peersManager()).removePath(eq(publicKey), any());
            assertTrue(peers.isEmpty());
            verify(ctx).passOutbound(eq(MULTICAST_ADDRESS), any(RemoteEnvelope.class), any(CompletableFuture.class));
        }
    }

    @Nested
    class StopHeartbeat {
        @Test
        void shouldStopHeartbeat() {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(peers, pingDisposable));

            handler.stopHeartbeat();

            verify(pingDisposable).dispose();
        }
    }

    @Nested
    class ClearRoutes {
        @Test
        void shouldClearRoutes(@Mock final CompressedPublicKey publicKey,
                               @Mock final Peer peer,
                               @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx) {
            final HashMap<CompressedPublicKey, Peer> peers = new HashMap<>(Map.of(publicKey, peer));
            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, pingDisposable);
            handler.clearRoutes(ctx);

            verify(ctx.peersManager()).removePath(eq(publicKey), any());
            assertTrue(peers.isEmpty());
        }
    }

    @SuppressWarnings("rawtypes")
    @Nested
    class InboundMessageHandling {
        @Test
        void shouldHandleInboundPingFromOtherNodes(@Mock final InetSocketAddressWrapper sender,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                                   @Mock final Peer peer) {
            final CompressedPublicKey publicKey = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
            try (final RemoteEnvelope<Protocol.Discovery> msg = RemoteEnvelope.discovery(0, publicKey, ProofOfWork.of(6518542))) {
                when(peers.computeIfAbsent(any(), any())).thenReturn(peer);

                final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, pingDisposable);
                handler.matchedInbound(ctx, sender, msg, new CompletableFuture<>());

                verify(ctx.peersManager()).addPath(eq(publicKey), any());
            }
        }

        @Test
        void shouldIgnoreInboundPingFromItself(@Mock final InetSocketAddressWrapper sender,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx) throws InvalidMessageFormatException {
            final CompressedPublicKey publicKey = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
            when(ctx.identity().getPublicKey()).thenReturn(publicKey);
            try (final RemoteEnvelope<Protocol.Discovery> msg = RemoteEnvelope.discovery(0, publicKey, ProofOfWork.of(6518542))) {

                final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, pingDisposable);
                handler.matchedInbound(ctx, sender, msg, new CompletableFuture<>());

                verify(ctx.peersManager(), never()).addPath(eq(msg.getSender()), any());
            }
        }

        @SuppressWarnings("rawtypes")
        @Test
        void shouldPassthroughUnicastMessages(@Mock final InetSocketAddressWrapper sender,
                                              @Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope msg) {
            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, pingDisposable);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<AddressedEnvelope<Address, Object>> inboundMessages = pipeline.inboundMessagesWithSender().test();

                pipeline.processInbound(sender, msg).join();

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new DefaultAddressedEnvelope<>(sender, null, msg));
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "SuspiciousMethodCalls" })
    @Test
    void shouldRouteOutboundMessageWhenRouteIsPresent(@Mock final CompressedPublicKey recipient,
                                                      @Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope message,
                                                      @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
        when(peers.get(any())).thenReturn(peer);

        final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, pingDisposable);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
        final TestObserver<AddressedEnvelope<Address, Object>> outboundMessages = pipeline.outboundMessagesWithRecipient().test();

        pipeline.processOutbound(recipient, message).join();

        outboundMessages.awaitCount(1)
                .assertValueCount(1)
                .assertValue(new DefaultAddressedEnvelope<>(null, peer.getAddress(), message));
    }

    @SuppressWarnings("rawtypes")
    @Test
    void shouldPassthroughMessageWhenRouteIsAbsent(@Mock final CompressedPublicKey recipient,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope message) {

        final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, pingDisposable);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
        final TestObserver<AddressedEnvelope<Address, Object>> outboundMessages = pipeline.outboundMessagesWithRecipient().test();

        pipeline.processOutbound(recipient, message).join();

        outboundMessages.awaitCount(1)
                .assertValueCount(1)
                .assertValue(new DefaultAddressedEnvelope<>(null, recipient, message));
    }

    @Nested
    class PeerTest {
        @Nested
        class Getter {
            @Test
            void shouldReturnCorrectValues(@Mock final InetSocketAddressWrapper address) {
                final Peer peer = new Peer(address, 1337L);

                assertSame(address, peer.getAddress());
                assertEquals(1337L, peer.getLastInboundPingTime());
            }
        }

        @Nested
        class InboundPingOccurred {
            @Test
            void shouldUpdateTime(@Mock final InetSocketAddressWrapper address) {
                final Peer peer = new Peer(address, 1337L);

                peer.inboundPingOccurred();

                assertThat(peer.getLastInboundPingTime(), greaterThan(1337L));
            }
        }

        @Nested
        class IsStale {
            @Test
            void shouldReturnCorrectValue(@Mock final InetSocketAddressWrapper address,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx) {
                when(ctx.config().getRemotePingTimeout()).thenReturn(ofSeconds(60));

                assertFalse(new Peer(address, System.currentTimeMillis()).isStale(ctx));
                assertTrue(new Peer(address, 1337L).isStale(ctx));
            }
        }
    }
}
