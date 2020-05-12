package org.drasyl.core.client.transport.relay.handler;

import org.drasyl.core.common.message.WelcomeMessage;
import org.drasyl.core.common.message.JoinMessage;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class RelayRequestHandlerTest {
    private Request request;
    private JoinMessage requestMessage;
    private WelcomeMessage responseMessage;
    private RelayRequestHandler handler;
    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        request = mock(Request.class);
        requestMessage = mock(JoinMessage.class);
        responseMessage = mock(WelcomeMessage.class);
        handler = new RelayRequestHandler();
        channel = new EmbeddedChannel(handler);
    }

    @Test
    public void shouldCompleteRequestOnExpectedResponseMessage() {
        when(requestMessage.getId()).thenReturn("123");
        when(request.getMessage()).thenReturn(requestMessage);
        when(responseMessage.getCorrespondingId()).thenReturn("123");

        channel.writeOutbound(request);
        channel.writeInbound(responseMessage);

        verify(request).completeRequest(responseMessage);
    }

    @Test
    public void shouldDiscardUnexpectedResponseMessage() {
        when(requestMessage.getId()).thenReturn("123");
        when(request.getMessage()).thenReturn(requestMessage);
        when(responseMessage.getCorrespondingId()).thenReturn("456");

        channel.writeOutbound(request);
        channel.writeInbound(responseMessage);

        assertNull(channel.readInbound());
        assertFalse(request.completed());
    }
}