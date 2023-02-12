package org.drasyl.handler.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutgoingSegmentQueueTest {
    private SendBuffer sendBuffer;
    private int mss;
    private TransmissionControlBlock tcb;

    @Nested
    class Add {

    }

    @Nested
    class AddAndFlush {
    }

    @Nested
    class Flush {
        @Test
        void shouldFlushSegmentsToChannel(@Mock(answer = RETURNS_DEEP_STUBS) final Segment seg,
                                          @Mock final ChannelPromise writePromise,
                                          @Mock final ChannelPromise ackPromise,
                                          @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final OutgoingSegmentQueue queue = new OutgoingSegmentQueue();

            queue.flush(ctx, tcb);

            verify(ctx).write(seg.copy(), writePromise);
            verify(ctx).flush();
        }

        @Test
        void shouldApplyRetransmissionTimeout() {
            // TODO
        }
    }
}
