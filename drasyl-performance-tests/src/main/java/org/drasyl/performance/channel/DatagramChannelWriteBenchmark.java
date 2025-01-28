/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.performance.channel;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.openjdk.jmh.annotations.Param;

import java.net.InetSocketAddress;
import java.util.function.Function;

public class DatagramChannelWriteBenchmark extends AbstractChannelWriteBenchmark {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 12345;
    @Param({ "1024" })
    private int packetSize;
    @Param({ "nio", "kqueue", "epoll" })
    private String channelImpl;
    private EventLoopGroup group;

    @Override
    protected Channel setupChannel() throws Exception {
        final Class<? extends DatagramChannel> channelClass;
        if ("kqueue".equals(channelImpl)) {
            group = new KQueueEventLoopGroup();
            channelClass = KQueueDatagramChannel.class;
        }
        else if ("epoll".equals(channelImpl)) {
            group = new EpollEventLoopGroup();
            channelClass = EpollDatagramChannel.class;
        }
        else {
            group = new NioEventLoopGroup();
            channelClass = NioDatagramChannel.class;
        }

        return new Bootstrap()
                .group(group)
                .channel(channelClass)
                .handler(new ChannelInboundHandlerAdapter())
                .bind(0)
                .sync()
                .channel();
    }

    @Override
    protected Object buildMsg() {
        final InetSocketAddress targetAddress = new InetSocketAddress(HOST, PORT);
        return new DatagramPacket(Unpooled.wrappedBuffer(new byte[packetSize]), targetAddress);
    }

    @Override
    protected Function<Object, Object> getMsgDuplicator() {
        return new Function<Object, Object>() {
            @Override
            public Object apply(final Object o) {
                return ((DatagramPacket) o).retainedDuplicate();
            }
        };
    }

    @Override
    protected void teardownChannel() throws Exception {
        group.shutdownGracefully().await();
    }
}
