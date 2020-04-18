package org.drasyl.core.client.transport.handler;

import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.ExtendedActorSystem;
import org.drasyl.core.client.P2PActorRef;
import org.drasyl.core.client.P2PActorRefProvider;
import org.drasyl.core.client.transport.InboundMessageEnvelope;
import org.drasyl.core.client.transport.OutboundMessageEnvelope;
import org.drasyl.core.client.transport.direct.messages.AkkaMessage;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

@Ignore
public class EnvelopeMessageHandlerTest {

    private ExtendedActorSystem system;
    private P2PActorRefProvider provider;
    private Address defaultAddress;
    private EnvelopeMessageHandler handler;
    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        system = mock(ExtendedActorSystem.class);
        provider = mock(P2PActorRefProvider.class);
        defaultAddress = new Address("bud", "my-system");
        handler = new EnvelopeMessageHandler(system, provider, defaultAddress);
        channel = new EmbeddedChannel(handler);

    }

    @After
    public void tearDown() {
    }

    @Test
    public void shouldHandleOutboundMessage() {
        Object message = "Hallo Welt";
        ActorRef sender = mock(ActorRef.class);
        P2PActorRef recipient = mock(P2PActorRef.class);
        OutboundMessageEnvelope messageEnvelope = new OutboundMessageEnvelope(message, sender, recipient);

        channel.writeOutbound(messageEnvelope);
        channel.flush();

        assertThat(channel.readInbound(), instanceOf(AkkaMessage.class));
    }

    @Test
    public void shouldHandleInboundMessage() {
        byte[] blob = new byte[0];
        AkkaMessage message = new AkkaMessage(
                blob, "foo", "bar");

        channel.writeInbound(message);
        channel.flush();

        assertThat(channel.readOutbound(), instanceOf(InboundMessageEnvelope.class));
    }
}