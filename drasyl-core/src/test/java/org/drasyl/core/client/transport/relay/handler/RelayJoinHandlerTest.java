package org.drasyl.core.client.transport.relay.handler;

import org.drasyl.core.common.messages.Welcome;
import org.drasyl.core.common.messages.NodeServerException;
import org.drasyl.core.common.messages.Join;
import org.drasyl.core.common.messages.Response;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RelayJoinHandlerTest {
    private Duration duration;
    private Request<Join> joinRequest;
    private Welcome welcome;
    private NodeServerException NodeServerException;
    private RelayJoinHandler handler;
    private EmbeddedChannel channel;
    private Response response;

    @Before
    public void setUp() {
        joinRequest = mock(Request.class);
        duration = Duration.ofSeconds(1);
        welcome = mock(Welcome.class);
        NodeServerException = mock(NodeServerException.class);
        response = mock(Response.class);
        handler = new RelayJoinHandler(joinRequest, duration, CompletableFuture.completedFuture(null));
    }

    @Test
    public void shouldInitiateJoinWhenConnectionIsEstablished() {
        channel = new EmbeddedChannel(handler);

        assertEquals(joinRequest, channel.readOutbound());
    }

    @Test(timeout = 5 * 1000L)
    public void shouldConfirmJoinOnConfirmationMessage() throws InterruptedException {
        when(joinRequest.getResponse()).thenReturn(completedFuture(welcome));

        channel = new EmbeddedChannel(handler);
        channel.writeInbound(response);
        channel.flush();

        assertTrue(handler.joinFuture().isDone());
        assertTrue(handler.joinFuture().isSuccess());
    }

    @Test(timeout = 5 * 1000L)
    public void shouldFailJoinOnException() {
        when(joinRequest.getResponse()).thenReturn(completedFuture(NodeServerException));

        channel = new EmbeddedChannel(handler);
        channel.writeInbound(response);
        channel.flush();

        assertTrue(handler.joinFuture().isDone());
        assertFalse(handler.joinFuture().isSuccess());
    }

    @Test(timeout = 5 * 1000L)
    public void shouldFailJoinOnTimeout() throws InterruptedException {
        when(joinRequest.getResponse()).thenReturn(new CompletableFuture<>());

        channel = new EmbeddedChannel(handler);

        // wait for join timeout
        Thread.sleep(duration.toMillis() + 100);

        channel.runScheduledPendingTasks();

        assertTrue(handler.joinFuture().isDone());
        assertFalse(handler.joinFuture().isSuccess());
    }
}