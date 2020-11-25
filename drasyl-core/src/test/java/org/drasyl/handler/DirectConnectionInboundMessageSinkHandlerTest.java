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
package org.drasyl.handler;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.HandlerContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectConnectionInboundMessageSinkHandlerTest {
    @Nested
    class MatchedWrite {
        @Mock(answer = RETURNS_DEEP_STUBS)
        private PeerChannelGroup channelGroup2;
        @InjectMocks
        private DirectConnectionInboundMessageSinkHandler underTest;

        @Test
        void shouldPassMessageIfWriteAndFlushSucceeded(@Mock final HandlerContext ctx,
                                                       @Mock final CompressedPublicKey sender,
                                                       @Mock(answer = RETURNS_DEEP_STUBS) final Message msg,
                                                       @Mock final CompletableFuture<Void> future,
                                                       @Mock final Future<?> writeAndFlushFuture) {
            when(writeAndFlushFuture.isSuccess()).thenReturn(true);
            when(channelGroup2.writeAndFlush(any(CompressedPublicKey.class), any(Object.class)).addListener(any())).then(invocation -> {
                @SuppressWarnings("unchecked") final GenericFutureListener<Future<?>> listener = invocation.getArgument(0, GenericFutureListener.class);
                listener.operationComplete(writeAndFlushFuture);
                return null;
            });

            underTest.matchedRead(ctx, sender, msg, future);

            verify(future).complete(null);
        }

        @Test
        void shouldPassMessageIfWriteAndFlushFails(@Mock final HandlerContext ctx,
                                                   @Mock final CompressedPublicKey sender,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final Message msg,
                                                   @Mock final CompletableFuture<Void> future,
                                                   @Mock final Future<?> writeAndFlushFuture) {
            when(writeAndFlushFuture.isSuccess()).thenReturn(false);
            when(channelGroup2.writeAndFlush(any(CompressedPublicKey.class), any(Object.class)).addListener(any())).then(invocation -> {
                @SuppressWarnings("unchecked") final GenericFutureListener<Future<?>> listener = invocation.getArgument(0, GenericFutureListener.class);
                listener.operationComplete(writeAndFlushFuture);
                return null;
            });

            underTest.matchedRead(ctx, sender, msg, future);

            verify(ctx).fireRead(sender, msg, future);
        }
    }
}