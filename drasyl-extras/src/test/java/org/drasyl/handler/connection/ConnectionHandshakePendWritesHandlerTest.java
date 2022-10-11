package org.drasyl.handler.connection;

import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.channels.ClosedChannelException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ConnectionHandshakePendWritesHandlerTest {
    @Test
    void shouldPendWritesWhenHandshakeIsNotCompleted(@Mock final ConnectionHandshakeCompleted event) {
        final EmbeddedChannel channel = new EmbeddedChannel(new ConnectionHandshakePendWritesHandler());

        final ChannelFuture written = channel.writeAndFlush("Hello");
        assertNull(channel.readOutbound());
        assertFalse(written.isDone());

        channel.pipeline().fireUserEventTriggered(event);
        assertEquals("Hello", channel.readOutbound());
        assertTrue(written.isSuccess());
    }

    @Test
    void shouldFailPendingWriteIfChannelIsClosed() {
        final EmbeddedChannel channel = new EmbeddedChannel(new ConnectionHandshakePendWritesHandler());
        final ChannelFuture written = channel.writeAndFlush("Hello");

        channel.close();
        assertNull(channel.readOutbound());
        assertThat(written.cause(), instanceOf(ClosedChannelException.class));
    }

    @Test
    void shouldFailPendingWriteIfHandlerIsRemovedFromPipeline() {
        final ConnectionHandshakePendWritesHandler handler = new ConnectionHandshakePendWritesHandler();
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        final ChannelFuture written = channel.writeAndFlush("Hello");

        channel.pipeline().remove(handler);

        assertThat(written.cause(), instanceOf(Exception.class));
    }
}
