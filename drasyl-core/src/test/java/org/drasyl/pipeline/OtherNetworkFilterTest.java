package org.drasyl.pipeline;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtherNetworkFilterTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private TypeValidator inboundValidator;
    @Mock
    private TypeValidator outboundValidator;

    @Test
    void shouldPassAllEvents(@Mock final Event event) {
        final OtherNetworkFilter handler = new OtherNetworkFilter();
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Event> inboundEvents = pipeline.inboundEvents().test();

        pipeline.processInbound(event);

        inboundEvents.awaitCount(1).assertValueCount(1);
        inboundEvents.assertValue(event);
    }

    @Test
    void shouldDropMessagesFromOtherNetworks(@Mock(answer = RETURNS_DEEP_STUBS) final Message message) throws InterruptedException {
        when(config.getNetworkId()).thenReturn(123);
        when(message.getNetworkId()).thenReturn(456);

        final OtherNetworkFilter handler = new OtherNetworkFilter();
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<CompressedPublicKey, Object>> inboundMessages = pipeline.inboundMessages().test();

        pipeline.processInbound(message);

        inboundMessages.await(1, SECONDS);
        inboundMessages.assertNoValues();
    }

    @Test
    void shouldPassMessagesFromSameNetwork(@Mock(answer = RETURNS_DEEP_STUBS) final Message message) {
        when(config.getNetworkId()).thenReturn(123);
        when(message.getNetworkId()).thenReturn(123);

        final OtherNetworkFilter handler = new OtherNetworkFilter();
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<CompressedPublicKey, Object>> inboundMessages = pipeline.inboundMessages().test();

        pipeline.processInbound(message);

        inboundMessages.awaitCount(1).assertValueCount(1);
        inboundMessages.assertValue(Pair.of(message.getSender(), message));
    }
}