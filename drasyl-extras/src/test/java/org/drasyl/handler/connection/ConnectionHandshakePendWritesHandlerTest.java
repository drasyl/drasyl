/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
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
