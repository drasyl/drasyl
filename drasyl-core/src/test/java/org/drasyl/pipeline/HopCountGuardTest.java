package org.drasyl.pipeline;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.codec.TypeValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HopCountGuardTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private TypeValidator inboundValidator;
    @Mock
    private TypeValidator outboundValidator;

    @Test
    void shouldPassMessagesThatHaveNotReachedTheirHopCountLimitAndIncrementHopCount(@Mock final CompressedPublicKey address,
                                                                                    @Mock final ApplicationMessage message) {
        when(config.getMessageHopLimit()).thenReturn((short) 2);
        when(message.getHopCount()).thenReturn((short) 1);

        final HopCountGuard handler = new HopCountGuard();
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Message> outboundMessages = pipeline.outboundMessages(Message.class).test();

        pipeline.processOutbound(address, message);

        outboundMessages.awaitCount(1).assertValueCount(1);
        outboundMessages.assertValue(m -> m instanceof ApplicationMessage);
        verify(message).incrementHopCount();
    }

    @Test
    void shouldDiscardMessagesThatHaveReachedTheirHopCountLimit(@Mock final CompressedPublicKey address,
                                                                @Mock final ApplicationMessage message) throws InterruptedException {
        when(config.getMessageHopLimit()).thenReturn((short) 1);
        when(message.getHopCount()).thenReturn((short) 1);

        final HopCountGuard handler = new HopCountGuard();
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Message> outboundMessages = pipeline.outboundMessages(Message.class).test();

        pipeline.processOutbound(address, message);

        outboundMessages.await(1, SECONDS);
        outboundMessages.assertNoValues();
    }
}