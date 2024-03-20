/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultChannelPromise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutgoingSegmentQueueTest {
    @Nested
    class Add {
        private ArrayDeque<Object> arrayDeque;
        @Mock
        private ChannelHandlerContext ctx;

        @BeforeEach
        void setUp() {
            arrayDeque = new ArrayDeque<>();
        }

        @Test
        void higherAcknowledgementsShouldReplaceSmallerOnes(@Mock(answer = RETURNS_DEEP_STUBS) final Segment seg1,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final Segment seg2,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            // <SEQ=2><ACK=2470><CTL=ACK><LEN=0>
            when(seg1.seq()).thenReturn(2L);
            when(seg1.ack()).thenReturn(2470L);
            when(seg1.isOnlyAck()).thenReturn(true);
            when(seg1.len()).thenReturn(0);
            final DefaultChannelPromise promise1 = new DefaultChannelPromise(channel);
            // <SEQ=2><ACK=3002><CTL=ACK>
            when(seg2.seq()).thenReturn(2L);
            when(seg2.ack()).thenReturn(3002L);
            when(seg2.isAck()).thenReturn(true);
            when(channel.eventLoop().inEventLoop()).thenReturn(true);
            final DefaultChannelPromise promise2 = new DefaultChannelPromise(channel);

            final OutgoingSegmentQueue queue = new OutgoingSegmentQueue(arrayDeque);
            queue.add(ctx, seg1, promise1);
            queue.add(ctx, seg2, promise2);

            // promise1 should listen on promise2
            promise2.setSuccess();
            assertTrue(promise1.isSuccess());
        }

        @Test
        void pushesShouldReplaceAcknowledgements(@Mock(answer = RETURNS_DEEP_STUBS) final Segment seg1,
                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Segment seg2,
                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Channel channel) {
            when(seg1.seq()).thenReturn(2L);
            when(seg1.ack()).thenReturn(2470L);
            when(seg1.isOnlyAck()).thenReturn(true);
            when(seg1.len()).thenReturn(0);
            when(seg2.seq()).thenReturn(2L);
            when(seg2.ack()).thenReturn(2470L);
            when(seg2.isPsh()).thenReturn(true);
            when(channel.eventLoop().inEventLoop()).thenReturn(true);
            final DefaultChannelPromise promise1 = new DefaultChannelPromise(channel);
            final DefaultChannelPromise promise2 = new DefaultChannelPromise(channel);

            final OutgoingSegmentQueue queue = new OutgoingSegmentQueue(arrayDeque);

            queue.add(ctx, seg1, promise1);
            queue.add(ctx, seg2, promise2);

            // promise1 should listen on promise2
            promise2.setSuccess();
            assertTrue(promise1.isSuccess());
        }
    }
}
