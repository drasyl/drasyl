package city.sane.akka.p2p.transport.relay.handler;

import city.sane.akka.p2p.transport.InboundMessageEnvelope;
import city.sane.akka.p2p.transport.direct.messages.AkkaMessage;
import org.drasyl.core.common.messages.ForwardableMessage;
import org.drasyl.core.common.messages.Message;
import org.drasyl.core.common.models.SessionUID;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

@Ignore
public class RelayMessageHandlerTest {

    private RelayMessageHandler handler;

    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        initMocks(this);
        handler = new RelayMessageHandler();
        channel = new EmbeddedChannel(handler);

    }

    @After
    public void tearDown() {
    }

    @Test
    public void shouldHandleOutboundMessage() {
        String fromSystem = "foo";
        String toSystem = "bar";
        byte[] blob = new byte[] {1,2,3};

        AkkaMessage akkaMessage = new AkkaMessage(
                blob, fromSystem, toSystem);

        channel.writeOutbound(akkaMessage);
        channel.flush();

        assertThat(channel.readInbound(), instanceOf(ForwardableMessage.class));
    }

    @Test
    public void shouldHandleInboundMessage() {
        SessionUID fromAddress = SessionUID.of("foo");
        SessionUID toAddress = SessionUID.of("bar");
        byte[] blob = new byte[0];
        Message message = new ForwardableMessage(fromAddress, toAddress, blob);

        channel.writeInbound(message);
        channel.flush();

        assertThat(channel.readOutbound(), instanceOf(InboundMessageEnvelope.class));
    }
}