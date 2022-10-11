/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.traffic;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.EventExecutor;
import org.drasyl.handler.traffic.OutboundMessagesThrottlingHandler.RateLimitedQueue;
import org.drasyl.util.TokenBucket;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboundMessagesThrottlingHandlerTest {
    @Test
    void shouldEnqueueWrite(@Mock final RateLimitedQueue queue,
                            @Mock final ChannelHandlerContext ctx,
                            @Mock final Object msg,
                            @Mock final ChannelPromise promise) {
        final OutboundMessagesThrottlingHandler handler = new OutboundMessagesThrottlingHandler(queue);
        handler.write(ctx, msg, promise);

        verify(queue).add(eq(ctx), any());
    }

    @Nested
    class RateLimitedQueueTest {
        @Mock
        private TokenBucket tokenBucket;

        @Nested
        class Add {
            @Test
            void shouldEnqueueAndStartConsumer(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final EventExecutor eventExecutor,
                                               @Mock final Runnable value) {
                when(ctx.executor()).thenReturn(eventExecutor);
                doAnswer(new Answer() {
                    @Override
                    public Object answer(final InvocationOnMock invocation) throws Throwable {
                        invocation.getArgument(0, Runnable.class).run();
                        return null;
                    }
                }).when(eventExecutor).execute(any());

                final RateLimitedQueue rateLimitedQueue = new RateLimitedQueue(new LinkedList<>(), tokenBucket, new AtomicBoolean(false));
                rateLimitedQueue.add(ctx, value);

                verify(value).run();
            }
        }
    }
}
