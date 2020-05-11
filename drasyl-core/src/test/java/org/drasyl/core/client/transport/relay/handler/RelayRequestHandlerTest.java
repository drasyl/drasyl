package org.drasyl.core.client.transport.relay.handler;

import org.drasyl.core.common.message.WelcomeMessage;
import org.drasyl.core.common.message.JoinMessage;
import org.drasyl.core.common.message.ResponseMessage;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class RelayRequestHandlerTest {
    private Request request;
    private JoinMessage requestMessage;
    private ResponseMessage response;
    private WelcomeMessage responseMessage;
    private RelayRequestHandler handler;
    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        request = mock(Request.class);
        requestMessage = mock(JoinMessage.class);
        response = mock(ResponseMessage.class);
        responseMessage = mock(WelcomeMessage.class);
        handler = new RelayRequestHandler();
        channel = new EmbeddedChannel(handler);
    }

    @Test
    public void shouldCompleteRequestOnExpectedResponseMessage() {
        when(requestMessage.getId()).thenReturn("123");
        when(request.getMessage()).thenReturn(requestMessage);
        when(response.getCorrespondingId()).thenReturn("123");
        when(response.getMessage()).thenReturn(responseMessage);

        channel.writeOutbound(request);
        channel.writeInbound(response);

        verify(request).completeRequest(responseMessage);
    }

    @Test
    public void shouldDiscardUnexpectedResponseMessage() {
        when(requestMessage.getId()).thenReturn("123");
        when(request.getMessage()).thenReturn(requestMessage);
        when(response.getCorrespondingId()).thenReturn("456");
        when(response.getMessage()).thenReturn(responseMessage);

        channel.writeOutbound(request);
        channel.writeInbound(response);

        assertNull(channel.readInbound());
        assertFalse(request.completed());
    }
}