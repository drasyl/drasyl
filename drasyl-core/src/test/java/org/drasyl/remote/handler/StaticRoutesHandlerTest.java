package org.drasyl.remote.handler;

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
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.serialization.SerializedApplicationMessage;
import org.drasyl.remote.protocol.AddressedIntermediateEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasKey;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaticRoutesHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    private final Map<CompressedPublicKey, InetSocketAddressWrapper> routes = new HashMap<>();

    @Test
    void shouldPopulateRoutesOnNodeUpEvent(@Mock final NodeUpEvent event,
                                           @Mock final CompressedPublicKey publicKey,
                                           @Mock final InetSocketAddress address) {
        when(config.getRemoteStaticRoutes()).thenReturn(Map.of(publicKey, address));

        final StaticRoutesHandler handler = new StaticRoutesHandler(routes);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);

        pipeline.processInbound(event).join();

        await().atMost(ofSeconds(5)).untilAsserted(() -> assertThat(routes, hasKey(publicKey)));

        verify(peersManager).addPath(eq(publicKey), any());
    }

    @Test
    void shouldClearRoutesOnNodeDownEvent(@Mock final NodeDownEvent event,
                                          @Mock final CompressedPublicKey publicKey,
                                          @Mock final InetSocketAddressWrapper address) {
        routes.put(publicKey, address);

        final StaticRoutesHandler handler = new StaticRoutesHandler(routes);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);

        pipeline.processInbound(event).join();

        assertTrue(routes.isEmpty());
    }

    @Test
    void shouldClearRoutesOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event,
                                                        @Mock final CompressedPublicKey publicKey,
                                                        @Mock final InetSocketAddressWrapper address) {
        routes.put(publicKey, address);

        final StaticRoutesHandler handler = new StaticRoutesHandler(routes);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);

        pipeline.processInbound(event).join();

        assertTrue(routes.isEmpty());
    }

    @SuppressWarnings("rawtypes")
    @Test
    void shouldRouteOutboundMessageWhenStaticRouteIsPresent(@Mock final InetSocketAddressWrapper address,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final SerializedApplicationMessage message) {
        final CompressedPublicKey publicKey = CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
        routes.put(publicKey, address);
        when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"));
        when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(1));
        when(message.getRecipient()).thenReturn(publicKey);
        when(message.getType()).thenReturn(byte[].class.getName());
        when(message.getContent()).thenReturn(new byte[0]);

        final StaticRoutesHandler handler = new StaticRoutesHandler(routes);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
        final TestObserver<AddressedIntermediateEnvelope> outboundMessages = pipeline.outboundMessages(AddressedIntermediateEnvelope.class).test();

        pipeline.processOutbound(publicKey, message).join();

        outboundMessages.awaitCount(1)
                .assertValueAt(0, m -> m.getRecipient().equals(address));
    }

    @Test
    void shouldPassthroughMessageWhenStaticRouteIsAbsent(@Mock final CompressedPublicKey publicKey,
                                                         @Mock(answer = RETURNS_DEEP_STUBS) final SerializedApplicationMessage message) {
        final StaticRoutesHandler handler = new StaticRoutesHandler(routes);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
        final TestObserver<SerializedApplicationMessage> outboundMessages = pipeline.outboundMessages(SerializedApplicationMessage.class).test();

        pipeline.processOutbound(publicKey, message).join();

        outboundMessages.awaitCount(1);
    }
}
