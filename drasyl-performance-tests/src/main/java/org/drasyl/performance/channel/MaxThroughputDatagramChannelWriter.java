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
import io.netty.buffer.ByteBufHolder;
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
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;

import java.net.InetSocketAddress;
import java.util.function.UnaryOperator;

/**
 * Writes for 60 seconds as fast as possible to an empty {@link DatagramChannel} and prints the
 * write throughput. Results are used as a baseline to compare with other channels.
 *
 * @see MaxThroughputDrasylDatagramChannelWriter
 */
@SuppressWarnings({ "java:S106", "java:S3776", "java:S4507" })
public class MaxThroughputDatagramChannelWriter extends AbstractMaxThroughputWriter {
    private static final String CLAZZ_NAME = StringUtil.simpleClassName(MaxThroughputDatagramChannelWriter.class);
    private static final boolean KQUEUE = SystemPropertyUtil.getBoolean("kqueue", false);
    private static final boolean EPOLL = SystemPropertyUtil.getBoolean("epoll", false);
    private static final String HOST = SystemPropertyUtil.get("host", "127.0.0.1");
    private static final int PORT = SystemPropertyUtil.getInt("port", 12345);
    private static final int PACKET_SIZE = SystemPropertyUtil.getInt("packetsize", 1024);
    private static final int DURATION = SystemPropertyUtil.getInt("duration", 10);
    private EventLoopGroup group;

    private MaxThroughputDatagramChannelWriter() {
        super(DURATION);
    }

    @Override
    protected Channel setupChannel() throws InterruptedException {
        final Class<? extends DatagramChannel> channelClass;
        if (KQUEUE) {
            group = new KQueueEventLoopGroup();
            channelClass = KQueueDatagramChannel.class;
        }
        else if (EPOLL) {
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
    protected ByteBufHolder buildMsg() {
        final InetSocketAddress targetAddress = new InetSocketAddress(HOST, PORT);
        return new DatagramPacket(Unpooled.wrappedBuffer(new byte[PACKET_SIZE]), targetAddress);
    }

    @Override
    protected UnaryOperator<Object> getMsgDuplicator() {
        return o -> ((ByteBufHolder) o).retainedDuplicate();
    }

    @Override
    protected long bytesWritten(final WriteHandler<?> writeHandler) {
        return writeHandler.messagesWritten() * PACKET_SIZE;
    }

    @Override
    protected void teardownChannel() throws InterruptedException {
        group.shutdownGracefully().await();
    }

    @Override
    protected String className() {
        return CLAZZ_NAME;
    }

    public static void main(final String[] args) throws Exception {
        System.out.printf("%s : KQUEUE: %b%n", CLAZZ_NAME, KQUEUE);
        System.out.printf("%s : EPOLL: %b%n", CLAZZ_NAME, EPOLL);
        System.out.printf("%s : HOST: %s%n", CLAZZ_NAME, HOST);
        System.out.printf("%s : PORT: %d%n", CLAZZ_NAME, PORT);
        System.out.printf("%s : PACKET_SIZE: %d%n", CLAZZ_NAME, PACKET_SIZE);
        System.out.printf("%s : DURATION: %d%n", CLAZZ_NAME, DURATION);

        new MaxThroughputDatagramChannelWriter().run();
    }
}

