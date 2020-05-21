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
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.time.Duration.ofMillis;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class NodeServerJoinGuardTest {
    private ChannelHandlerContext ctx;
    private ScheduledFuture<?> timeoutFuture;
    private ChannelPromise promise;
    private Message<?> msg;
    private CompressedPublicKey publicKey;
    private EventExecutor eventExecutor;
    private ChannelFuture channelFuture;
    private Throwable cause;

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

        NodeServerJoinGuard handler = new NodeServerJoinGuard(new AtomicBoolean(false), ofMillis(1), timeoutFuture);

        handler.channelActive(ctx);

        verify(ctx).writeAndFlush(any(ConnectionExceptionMessage.class));
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
        verify(ctx).close();
    }

    @Test
    void closeShouldCloseChannelAndCancelTimeoutTask() throws Exception {
        NodeServerJoinGuard handler = new NodeServerJoinGuard(new AtomicBoolean(false), ofMillis(1), timeoutFuture);

        handler.close(ctx, promise);

        verify(timeoutFuture).cancel(true);
        verify(ctx).close(promise);
    }

    @Test
    void channelWrite0ShouldPassThroughMessageIfAuthenticated() throws Exception {
        NodeServerJoinGuard handler = new NodeServerJoinGuard(new AtomicBoolean(true), ofMillis(1), timeoutFuture);

        handler.channelWrite0(ctx, msg, promise);

        verify(ctx).write(any(QuitMessage.class), eq(promise));
    }

    @Test
    void channelWrite0ShouldPassThroughUnrestrictedMessageIfNotAuthenticated() throws Exception {
        NodeServerJoinGuard handler = new NodeServerJoinGuard(ofMillis(1));

        handler.channelWrite0(ctx, msg, promise);

        verify(ctx).write(any(QuitMessage.class), eq(promise));
    }

    @Test
    void channelWrite0ShouldBlockNonUnrestrictedMessageIfNotAuthenticated() {
        NodeServerJoinGuard handler = new NodeServerJoinGuard(new AtomicBoolean(false), ofMillis(1), timeoutFuture);
        msg = new RequestClientsStocktakingMessage();

        assertThrows(IllegalStateException.class, () -> handler.channelWrite0(ctx, msg, promise));

        verify(ctx, never()).write(any(Message.class), eq(promise));
    }

    @Test
    void channelRead0ShouldReplyWithStatusForbiddenForNonJoinMessageIfNotAuthenticated() throws Exception {
        NodeServerJoinGuard handler = new NodeServerJoinGuard(new AtomicBoolean(false), ofMillis(1), timeoutFuture);

        handler.channelRead0(ctx, msg);

        verify(ctx).writeAndFlush(new StatusMessage(STATUS_FORBIDDEN, msg.getId()));
        verify(ctx, never()).fireChannelRead(any(Message.class));
    }

    @Test
    void channelRead0ShouldAuthenticateIfNotAuthenticatedAndJoinMessageReceived() throws Exception {
        NodeServerJoinGuard handler = new NodeServerJoinGuard(new AtomicBoolean(false), ofMillis(1), timeoutFuture);
        msg = mock(JoinMessage.class);

        handler.channelRead0(ctx, msg);

        verify(ctx, never()).writeAndFlush(any(Message.class));
        verify(timeoutFuture).cancel(true);
        verify(ctx).fireChannelRead(any(JoinMessage.class));
        assertTrue(handler.authenticated.get());
    }

    @Test
    void channelRead0ShouldReplyWithExceptionIfAuthenticatedAndJoinMessageReceived() throws Exception {
        NodeServerJoinGuard handler = new NodeServerJoinGuard(new AtomicBoolean(true), ofMillis(1), timeoutFuture);
        msg = new JoinMessage(publicKey, Set.of());

        handler.channelRead0(ctx, msg);

        verify(ctx).writeAndFlush(new StatusMessage(STATUS_FORBIDDEN, msg.getId()));
        verify(timeoutFuture, never()).cancel(true);
        verify(ctx, never()).fireChannelRead(any(JoinMessage.class));
        assertTrue(handler.authenticated.get());
    }

    @Test
    void channelRead0ShouldPassThroughNonJoinMessageIfAuthenticated() throws Exception {
        NodeServerJoinGuard handler = new NodeServerJoinGuard(new AtomicBoolean(true), ofMillis(1), timeoutFuture);

        handler.channelRead0(ctx, msg);

        verify(ctx, never()).writeAndFlush(any(Message.class));
        verify(timeoutFuture, never()).cancel(true);
        verify(ctx, never()).fireChannelRead(any(JoinMessage.class));
        assertTrue(handler.authenticated.get());
    }

    @Test
    void exceptionCaughtShouldWriteExceptionToChannelAndThenCloseItIfNotAuthenticated() throws Exception {
        NodeServerJoinGuard handler = new NodeServerJoinGuard(new AtomicBoolean(false), ofMillis(1), timeoutFuture);
        handler.exceptionCaught(ctx, cause);

        verify(ctx).writeAndFlush(any(ConnectionExceptionMessage.class));
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
    }

    @Test
    void exceptionCaughtShouldNotWriteExceptionToChannelAndNotCloseItIfAuthenticated() throws Exception {
        NodeServerJoinGuard handler = new NodeServerJoinGuard(new AtomicBoolean(true), ofMillis(1), timeoutFuture);
        handler.exceptionCaught(ctx, cause);

        verify(ctx, never()).writeAndFlush(any(ConnectionExceptionMessage.class));
        verify(channelFuture, never()).addListener(ChannelFutureListener.CLOSE);
    }
}