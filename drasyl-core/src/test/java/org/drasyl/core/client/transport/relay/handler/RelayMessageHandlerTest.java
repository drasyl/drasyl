package org.drasyl.core.client.transport.relay.handler;

import org.drasyl.core.client.transport.InboundMessageEnvelope;
import org.drasyl.core.client.transport.direct.messages.AkkaMessage;
import org.drasyl.core.common.message.ApplicationMessage;
import org.drasyl.core.common.message.Message;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.core.node.identity.Identity;
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

        assertThat(channel.readInbound(), instanceOf(ApplicationMessage.class));
    }

    @Test
    public void shouldHandleInboundMessage() {
        Identity fromAddress = Identity.of("foo");
        Identity toAddress = Identity.of("bar");
        byte[] blob = new byte[0];
        Message message = new ApplicationMessage(fromAddress, toAddress, blob);

        channel.writeInbound(message);
        channel.flush();

        assertThat(channel.readOutbound(), instanceOf(InboundMessageEnvelope.class));
    }
}