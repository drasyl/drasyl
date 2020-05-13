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
package org.drasyl.core.server.handler;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.core.common.message.*;
import org.drasyl.core.models.CompressedPublicKey;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.drasyl.core.common.message.StatusMessage.Code.STATUS_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class JoinHandlerTest {
    private ChannelHandlerContext ctx;
    private ScheduledFuture timeoutFuture;
    private ChannelPromise promise;
    private Message msg;
    private CompressedPublicKey publicKey;
    private EventExecutor eventExecutor;
    private ChannelFuture channelFuture;

    @BeforeEach
    void setUp() {
        ctx = mock(ChannelHandlerContext.class);
        promise = mock(ChannelPromise.class);
        timeoutFuture = mock(ScheduledFuture.class);
        msg = new LeaveMessage();
        publicKey = mock(CompressedPublicKey.class);
        eventExecutor = mock(EventExecutor.class);
        channelFuture = mock(ChannelFuture.class);

        when(ctx.writeAndFlush(any(Message.class))).thenReturn(channelFuture);
    }

    // FIXME: fix test
    @Ignore
    void channelActiveShouldThrowExceptionAndCloseChannelOnTimeout() throws Exception {
        when(ctx.executor()).thenReturn(eventExecutor);
        when(eventExecutor.schedule(any(Runnable.class), any(), any(TimeUnit.class))).then(invocation -> {
            Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return mock(ScheduledFuture.class);
        });

        JoinHandler handler = new JoinHandler(new AtomicBoolean(false), 1L, timeoutFuture);

        handler.channelActive(ctx);

        verify(ctx).writeAndFlush(any(ConnectionExceptionMessage.class));
        verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
        verify(ctx).close();
    }

    @Test
    void closeShouldCloseChannelAndCancelTimeoutTask() throws Exception {
        JoinHandler handler = new JoinHandler(new AtomicBoolean(false), 1L, timeoutFuture);

        handler.close(ctx, promise);

        verify(timeoutFuture).cancel(true);
        verify(ctx).close(promise);
    }

    @Test
    void channelWrite0ShouldPassThroughMessageIfAuthenticated() throws Exception {
        JoinHandler handler = new JoinHandler(new AtomicBoolean(true), 1L, timeoutFuture);

        handler.channelWrite0(ctx, msg, promise);

        verify(ctx).write(any(LeaveMessage.class), eq(promise));
    }

    @Test
    void channelWrite0ShouldPassThroughUnrestrictedMessageIfNotAuthenticated() throws Exception {
        JoinHandler handler = new JoinHandler(1L);

        handler.channelWrite0(ctx, msg, promise);

        verify(ctx).write(any(LeaveMessage.class), eq(promise));
    }

    @Test
    void channelWrite0ShouldBlockNonUnrestrictedMessageIfNotAuthenticated() {
        JoinHandler handler = new JoinHandler(new AtomicBoolean(false), 1L, timeoutFuture);
        msg = new RequestClientsStocktakingMessage();

        assertThrows(IllegalStateException.class, () -> handler.channelWrite0(ctx, msg, promise));

        verify(ctx, never()).write(any(Message.class), eq(promise));
    }

    @Test
    void channelRead0ShouldReplyWithStatusForbiddenForNonJoinMessageIfNotAuthenticated() throws Exception {
        JoinHandler handler = new JoinHandler(new AtomicBoolean(false), 1L, timeoutFuture);

        handler.channelRead0(ctx, msg);

        verify(ctx).writeAndFlush(new StatusMessage(STATUS_FORBIDDEN, msg.getId()));
        verify(ctx, never()).fireChannelRead(any(Message.class));
    }

    @Test
    void channelRead0ShouldAuthenticateIfNotAuthenticatedAndJoinMessageReceived() throws Exception {
        JoinHandler handler = new JoinHandler(new AtomicBoolean(false), 1L, timeoutFuture);
        msg = mock(JoinMessage.class);

        handler.channelRead0(ctx, msg);

        verify(ctx, never()).writeAndFlush(any(Message.class));
        verify(timeoutFuture).cancel(true);
        verify(ctx).fireChannelRead(any(JoinMessage.class));
        assertTrue(handler.authenticated.get());
    }

    @Test
    void channelRead0ShouldReplyWithExceptionIfAuthenticatedAndJoinMessageReceived() throws Exception {
        JoinHandler handler = new JoinHandler(new AtomicBoolean(true), 1L, timeoutFuture);
        msg = new JoinMessage(publicKey, Set.of());

        handler.channelRead0(ctx, msg);

        verify(ctx).writeAndFlush(any(MessageExceptionMessage.class));
        verify(timeoutFuture, never()).cancel(true);
        verify(ctx, never()).fireChannelRead(any(JoinMessage.class));
        assertTrue(handler.authenticated.get());
    }

    @Test
    void channelRead0ShouldPassThroughNonJoinMessageIfAuthenticated() throws Exception {
        JoinHandler handler = new JoinHandler(new AtomicBoolean(true), 1L, timeoutFuture);

        handler.channelRead0(ctx, msg);

        verify(ctx, never()).writeAndFlush(any(Message.class));
        verify(timeoutFuture, never()).cancel(true);
        verify(ctx, never()).fireChannelRead(any(JoinMessage.class));
        assertTrue(handler.authenticated.get());
    }
}