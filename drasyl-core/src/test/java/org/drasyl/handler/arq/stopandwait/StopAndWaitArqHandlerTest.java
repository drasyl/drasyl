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
package org.drasyl.handler.arq.stopandwait;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.channels.ClosedChannelException;

import static org.awaitility.Awaitility.await;
import static org.drasyl.handler.arq.stopandwait.StopAndWaitArqAck.STOP_AND_WAIT_ACK_1;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StopAndWaitArqHandlerTest {
    @Test
    void senderShouldFollowStopAndWaitProtocol() {
        final ChannelHandler handler = new StopAndWaitArqHandler(100);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final StopAndWaitArqData data0 = new StopAndWaitArqData(false, Unpooled.buffer());
        final StopAndWaitArqData data1 = new StopAndWaitArqData(true, Unpooled.buffer());

        // no DATA is pending -> write data0
        final ChannelFuture write0 = channel.writeOneOutbound(data0);
        channel.flush();
        assertFalse(write0.isDone());
        assertEquals(data0, channel.readOutbound());

        // DATA is pending -> enqueue data1
        final ChannelFuture write1 = channel.writeOneOutbound(data1);
        channel.flush();
        assertFalse(write1.isDone());
        assertNull(channel.readOutbound());

        // got ack1 -> succeed data0, write data1
        channel.writeInbound(STOP_AND_WAIT_ACK_1);
        assertTrue(write0.isSuccess());
        assertNull(channel.readInbound());
        assertEquals(data1, channel.readOutbound());

        // timeout -> write data1 again
        await().untilAsserted(() -> {
            channel.runScheduledPendingTasks();
            assertEquals(data1, channel.readOutbound());
        });

        // close channel -> fail data1
        channel.close();
        data0.release();
        data1.release();
        data1.release();
        assertThat(write1.cause(), instanceOf(ClosedChannelException.class));
    }

    @Test
    void receiverShouldFollowStopAndWaitProtocol() {
        final ChannelHandler handler = new StopAndWaitArqHandler(100);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final StopAndWaitArqData data0 = new StopAndWaitArqData(false, Unpooled.buffer());

        // write expected DATA -> pass through data0, write ack1
        channel.pipeline().fireChannelRead(data0);
        assertEquals(data0, channel.readInbound());
        assertEquals(STOP_AND_WAIT_ACK_1, channel.readOutbound());
        assertEquals(1, data0.refCnt());

        // write unexpected DATA -> drop data0, write ack1
        channel.pipeline().fireChannelRead(data0);
        assertNull(channel.readInbound());
        assertEquals(STOP_AND_WAIT_ACK_1, channel.readOutbound());
        assertEquals(0, data0.refCnt());
    }
}
