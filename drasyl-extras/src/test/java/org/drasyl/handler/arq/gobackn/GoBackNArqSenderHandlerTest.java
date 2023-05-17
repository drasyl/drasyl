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
package org.drasyl.handler.arq.gobackn;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.util.UnsignedInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.concurrent.CancellationException;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class GoBackNArqSenderHandlerTest {
    @Test
    void senderShouldActCorrectly() {
        final ChannelHandler handler = new GoBackNArqSenderHandler(1, Duration.ofMillis(100));
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final GoBackNArqData data0 = new GoBackNArqData(Unpooled.buffer());
        final GoBackNArqData data1 = new GoBackNArqData(Unpooled.buffer());

        // no DATA is pending --> write data0
        final ChannelFuture write0 = channel.writeOneOutbound(data0);
        channel.flush();
        assertFalse(write0.isDone());
        assertInstanceOf(GoBackNArqData.class, channel.readOutbound());

        // DATA is pending --> enqueue data1
        final ChannelFuture write1 = channel.writeOneOutbound(data1);
        channel.flush();
        assertFalse(write1.isDone());
        assertNull(channel.readOutbound());

        // got ack1 --> succeed data0, write data1
        channel.writeInbound(new GoBackNArqAck(UnsignedInteger.MIN_VALUE));
        assertTrue(write0.isSuccess());
        assertNull(channel.readInbound());
        assertInstanceOf(GoBackNArqData.class, channel.readOutbound());

        // timeout -> write data1 again
        await().untilAsserted(() -> {
            channel.runScheduledPendingTasks();
            assertInstanceOf(GoBackNArqData.class, channel.readOutbound());
        });

        // close channel -> fail data1
        channel.close();
        assertThat(write1.cause(), instanceOf(ClosedChannelException.class));
    }

    @Test
    void senderShouldStopTimer() {
        final ChannelHandler handler = new GoBackNArqSenderHandler(1, Duration.ofMillis(100));
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final GoBackNArqData data0 = new GoBackNArqData(Unpooled.buffer());

        // no DATA is pending --> write data0
        final ChannelFuture write0 = channel.writeOneOutbound(data0);
        channel.flush();
        assertFalse(write0.isDone());
        assertInstanceOf(GoBackNArqData.class, channel.readOutbound());

        // got ack1 --> succeed data0
        channel.writeInbound(new GoBackNArqAck(UnsignedInteger.MIN_VALUE));
        assertTrue(write0.isSuccess());
        assertNull(channel.readInbound());

        channel.runScheduledPendingTasks();

        await().timeout(Duration.ofMillis(200));

        // close channel -> fail data1
        channel.close();
        assertNull(channel.readOutbound());
    }

    @Test
    void senderShouldActCorrectlyOnOverflow() {
        final ChannelHandler handler = new GoBackNArqSenderHandler(2, Duration.ofMillis(100),
                UnsignedInteger.MAX_VALUE.decrement(), UnsignedInteger.MIN_VALUE, true);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final GoBackNArqData data0 = new GoBackNArqData(Unpooled.buffer());
        final GoBackNArqData data1 = new GoBackNArqData(Unpooled.buffer());

        // no DATA is pending --> write data0
        final ChannelFuture write0 = channel.writeOneOutbound(data0);
        channel.flush();
        assertFalse(write0.isDone());
        assertInstanceOf(GoBackNArqData.class, channel.readOutbound());

        // no DATA is pending --> write data0
        final ChannelFuture write1 = channel.writeOneOutbound(data1);
        channel.flush();
        assertFalse(write1.isDone());
        assertInstanceOf(GoBackNArqData.class, channel.readOutbound());

        // got ack1+2 --> succeed data0 and data1
        channel.writeInbound(new GoBackNArqAck(UnsignedInteger.MIN_VALUE));
        assertTrue(write0.isSuccess());
        assertTrue(write1.isSuccess());
        assertNull(channel.readInbound());
        assertNull(channel.readOutbound());

        // close channel -> fail data1
        channel.close();
    }

    @Test
    void senderShouldDropWrongAck() {
        final ChannelHandler handler = new GoBackNArqSenderHandler(1, Duration.ofMillis(100));
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final GoBackNArqData data0 = new GoBackNArqData(Unpooled.buffer());

        // no DATA is pending --> write data0
        final ChannelFuture write0 = channel.writeOneOutbound(data0);
        channel.flush();
        assertFalse(write0.isDone());
        assertInstanceOf(GoBackNArqData.class, channel.readOutbound());

        // got ack1 --> succeed data0, write data1
        channel.writeInbound(new GoBackNArqAck(UnsignedInteger.of(1)));
        assertFalse(write0.isDone());
        assertNull(channel.readInbound());
        assertNull(channel.readOutbound());
    }

    @Test
    void senderShouldSkipCanceledMessage() {
        final ChannelHandler handler = new GoBackNArqSenderHandler(1, Duration.ofMillis(100));
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final GoBackNArqData data0 = new GoBackNArqData(Unpooled.buffer());
        final GoBackNArqData data1 = new GoBackNArqData(Unpooled.buffer());

        // no DATA is pending --> write data0
        final ChannelFuture write0 = channel.writeOneOutbound(data0);
        channel.flush();
        assertFalse(write0.isDone());
        assertInstanceOf(GoBackNArqData.class, channel.readOutbound());

        // DATA is pending --> enqueue data1
        final ChannelFuture write1 = channel.writeOneOutbound(data1);
        channel.flush();
        assertFalse(write1.isDone());
        assertNull(channel.readOutbound());

        // cancel promise
        write0.cancel(true);

        // got ack1 --> write data1
        channel.writeInbound(new GoBackNArqAck(UnsignedInteger.MIN_VALUE));
        assertTrue(write0.isCancelled());
        assertThat(write0.cause(), instanceOf(CancellationException.class));
        assertNull(channel.readInbound());
        assertInstanceOf(GoBackNArqData.class, channel.readOutbound());

        // timeout -> write data1 again
        await().untilAsserted(() -> {
            channel.runScheduledPendingTasks();
            assertInstanceOf(GoBackNArqData.class, channel.readOutbound());
        });

        // close channel -> fail data1
        channel.close();
        assertThat(write1.cause(), instanceOf(ClosedChannelException.class));
    }
}

