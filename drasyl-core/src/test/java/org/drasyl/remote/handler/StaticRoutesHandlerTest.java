package org.drasyl.remote.handler;

import com.google.protobuf.MessageLite;
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
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.util.TypeReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
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

    @Test
    void shouldPopulateRoutesOnNodeUpEvent(@Mock final NodeUpEvent event,
                                           @Mock final CompressedPublicKey publicKey) {
        final InetSocketAddressWrapper address = new InetSocketAddressWrapper(22527);
        when(config.getRemoteStaticRoutes()).thenReturn(Map.of(publicKey, address));

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, StaticRoutesHandler.INSTANCE);

        pipeline.processInbound(event).join();

        verify(peersManager, timeout(1_000)).addPath(eq(publicKey), any());
    }

    @Test
    void shouldClearRoutesOnNodeDownEvent(@Mock final NodeDownEvent event,
                                          @Mock final CompressedPublicKey publicKey,
                                          @Mock final InetSocketAddressWrapper address) {
        when(config.getRemoteStaticRoutes()).thenReturn(Map.of(publicKey, address));

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, StaticRoutesHandler.INSTANCE);

        pipeline.processInbound(event).join();

        verify(peersManager, timeout(1_000)).removePath(eq(publicKey), any());
    }

    @Test
    void shouldClearRoutesOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event,
                                                        @Mock final CompressedPublicKey publicKey,
                                                        @Mock final InetSocketAddressWrapper address) {
        when(config.getRemoteStaticRoutes()).thenReturn(Map.of(publicKey, address));

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, StaticRoutesHandler.INSTANCE);

        pipeline.processInbound(event).join();

        verify(peersManager, timeout(1_000)).removePath(eq(publicKey), any());
    }

    @Test
    void shouldRouteOutboundMessageWhenStaticRouteIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final SerializedApplicationMessage message) {
        final InetSocketAddressWrapper address = new InetSocketAddressWrapper(22527);
        final CompressedPublicKey publicKey = CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
        when(config.getRemoteStaticRoutes()).thenReturn(Map.of(publicKey, address));
        when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"));
        when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(1));
        when(message.getType()).thenReturn(byte[].class.getName());
        when(message.getContent()).thenReturn(new byte[0]);

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, StaticRoutesHandler.INSTANCE);
        final TestObserver<IntermediateEnvelope<? extends MessageLite>> outboundMessages = pipeline.outboundMessages(new TypeReference<IntermediateEnvelope<? extends MessageLite>>() {
        }).test();

        pipeline.processOutbound(publicKey, message).join();

        outboundMessages.awaitCount(1)
                .assertValueCount(1);
    }

    @Test
    void shouldPassthroughMessageWhenStaticRouteIsAbsent(@Mock final CompressedPublicKey publicKey,
                                                         @Mock(answer = RETURNS_DEEP_STUBS) final SerializedApplicationMessage message) {
        when(config.getRemoteStaticRoutes()).thenReturn(Map.of());

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, StaticRoutesHandler.INSTANCE);
        final TestObserver<SerializedApplicationMessage> outboundMessages = pipeline.outboundMessages(SerializedApplicationMessage.class).test();

        pipeline.processOutbound(publicKey, message).join();

        outboundMessages.awaitCount(1);
    }
}
