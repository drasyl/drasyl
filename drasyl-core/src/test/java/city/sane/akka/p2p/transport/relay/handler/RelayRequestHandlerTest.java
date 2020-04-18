package city.sane.akka.p2p.transport.relay.handler;

import city.sane.relay.common.messages.Welcome;
import city.sane.relay.common.messages.Join;
import city.sane.relay.common.messages.Response;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class RelayRequestHandlerTest {
    private Request request;
    private Join requestMessage;
    private Response response;
    private Welcome responseMessage;
    private RelayRequestHandler handler;
    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        request = mock(Request.class);
        requestMessage = mock(Join.class);
        response = mock(Response.class);
        responseMessage = mock(Welcome.class);
        handler = new RelayRequestHandler();
        channel = new EmbeddedChannel(handler);
    }

    @Test
    public void shouldCompleteRequestOnExpectedResponseMessage() {
        when(requestMessage.getMessageID()).thenReturn("123");
        when(request.getMessage()).thenReturn(requestMessage);
        when(response.getMsgID()).thenReturn("123");
        when(response.getMessage()).thenReturn(responseMessage);

        channel.writeOutbound(request);
        channel.writeInbound(response);

        verify(request, times(1)).completeRequest(responseMessage);
    }

    @Test
    public void shouldDiscardUnexpectedResponseMessage() {
        when(requestMessage.getMessageID()).thenReturn("123");
        when(request.getMessage()).thenReturn(requestMessage);
        when(response.getMsgID()).thenReturn("456");
        when(response.getMessage()).thenReturn(responseMessage);

        channel.writeOutbound(request);
        channel.writeInbound(response);

        assertNull(channel.readInbound());
        assertFalse(request.completed());
    }
}