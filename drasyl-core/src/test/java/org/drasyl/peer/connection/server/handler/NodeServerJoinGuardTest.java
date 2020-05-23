/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.peer.connection.server.handler;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static java.time.Duration.ofMillis;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NodeServerJoinGuardTest {
    private EmbeddedChannel channel;
    private ChannelHandlerContext ctx;
    private ScheduledFuture<?> timeoutFuture;
    private ChannelPromise promise;
    private Message<?> msg;
    private CompressedPublicKey publicKey;
    private EventExecutor eventExecutor;
    private ChannelFuture channelFuture;
    private Throwable cause;
    private ApplicationMessage applicationMessage;
    private JoinMessage joinMessage;

    @BeforeEach
    void setUp() {
        ctx = mock(ChannelHandlerContext.class);
        promise = mock(ChannelPromise.class);
        timeoutFuture = mock(ScheduledFuture.class);
        msg = new QuitMessage();
        publicKey = mock(CompressedPublicKey.class);
        eventExecutor = mock(EventExecutor.class);
        channelFuture = mock(ChannelFuture.class);
        cause = mock(Throwable.class);

        when(ctx.writeAndFlush(any(Message.class))).thenReturn(channelFuture);
        applicationMessage = mock(ApplicationMessage.class);
        joinMessage = mock(JoinMessage.class);
    }

    // FIXME: fix test
    @Disabled("Muss implementiert werden")
    @Test
    void channelActiveShouldThrowExceptionAndCloseChannelOnTimeout() throws Exception {
        when(ctx.executor()).thenReturn(eventExecutor);
        when(eventExecutor.schedule(any(Runnable.class), any(), any(TimeUnit.class))).then(invocation -> {
            Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return mock(ScheduledFuture.class);
        });

        NodeServerJoinGuard handler = new NodeServerJoinGuard(ofMillis(1), timeoutFuture);

        handler.channelActive(ctx);

        verify(ctx).writeAndFlush(any(ConnectionExceptionMessage.class));
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
        verify(ctx).close();
    }

    @Test
    void closeShouldCloseChannelAndCancelTimeoutTask() throws Exception {
        NodeServerJoinGuard handler = new NodeServerJoinGuard(ofMillis(1), timeoutFuture);

        handler.close(ctx, promise);

        verify(timeoutFuture).cancel(true);
        verify(ctx).close(promise);
    }

    @Test
    void shouldPassThroughOutgoingJoinMessage() {
        NodeServerJoinGuard handler = new NodeServerJoinGuard(ofMillis(1));
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeOutbound(joinMessage);

        assertEquals(joinMessage, channel.readOutbound());
    }

    @Test
    void shouldDenyNonUnrestrictedMessages() {
        when(applicationMessage.getId()).thenReturn("123");

        NodeServerJoinGuard handler = new NodeServerJoinGuard(ofMillis(1), timeoutFuture);
        channel = new EmbeddedChannel(handler);

        channel.writeInbound(applicationMessage);

        assertEquals(new StatusMessage(STATUS_FORBIDDEN, "123"), channel.readOutbound());
        assertNull(channel.readInbound());
    }

    @Test
    void channelRead0ShouldReplyWithStatusForbiddenForNonJoinMessage() {
        NodeServerJoinGuard handler = new NodeServerJoinGuard(ofMillis(1), timeoutFuture);

        handler.channelRead0(ctx, msg);

        verify(ctx).writeAndFlush(new StatusMessage(STATUS_FORBIDDEN, msg.getId()));
        verify(ctx, never()).fireChannelRead(any(Message.class));
    }

    @Test
    void shouldPassJoinMessage() {
        NodeServerJoinGuard handler = new NodeServerJoinGuard(ofMillis(1), timeoutFuture);
        channel = new EmbeddedChannel(handler);

        channel.writeInbound(joinMessage);

        assertEquals(joinMessage, channel.readInbound());
    }

    @Test
    void exceptionCaughtShouldWriteExceptionToChannelAndThenCloseIt() {
        NodeServerJoinGuard handler = new NodeServerJoinGuard(ofMillis(1), timeoutFuture);
        handler.exceptionCaught(ctx, cause);

        verify(ctx).writeAndFlush(any(ConnectionExceptionMessage.class));
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
    }
}