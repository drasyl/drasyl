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
package org.drasyl.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlowReadAwareHandlerTest {
    @BeforeEach
    void setUp() {
        System.setProperty("org.drasyl.channel.handler.slowReadThreshold", "1.0");
    }

    @AfterEach
    void tearDown() {
        System.setProperty("org.drasyl.channel.handler.slowReadThreshold", "0.0");
    }

    @Test
    void shouldAddMeasurementHandlers() throws InterruptedException {
        final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        channel.pipeline().addLast(new SlowReadAwareHandler());

        final List<String> names = new ArrayList<>();
        final Iterator<Entry<String, ChannelHandler>> iterator = channel.pipeline().iterator();
        while (iterator.hasNext()) {
            names.add(iterator.next().getKey());
        }

        assertEquals(List.of(
                "SlowReadAwareHandler$SlowReadAwareBeforeHandler#0",
                "ChannelInboundHandlerAdapter#0",
                "SlowReadAwareHandler$SlowReadAwareAfterHandler#0"
        ), names);
    }
}
