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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.openjdk.jmh.annotations.Param;

import java.net.PortUnreachableException;

public class DatagramChannelReadBenchmark extends AbstractChannelReadBenchmark {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 12345;
    @Param({ "3" })
    protected int writeThreads;
    @Param({ "1024" })
    private int packetSize;
    @Param({ "nio", "kqueue", "epoll" })
    private String channelImpl;
    private EventLoopGroup writeGroup;
    private EventLoopGroup readGroup;

    @Override
    protected ChannelGroup setupWriteChannels() throws Exception {
        final ByteBuf msg = Unpooled.wrappedBuffer(new byte[packetSize]);

        final Class<? extends DatagramChannel> channelClass;
        if ("kqueue".equals(channelImpl)) {
            writeGroup = new KQueueEventLoopGroup(writeThreads);
            channelClass = KQueueDatagramChannel.class;
        }
        else if ("epoll".equals(channelImpl)) {
            writeGroup = new EpollEventLoopGroup(writeThreads);
            channelClass = EpollDatagramChannel.class;
        }
        else {
            writeGroup = new NioEventLoopGroup(writeThreads);
            channelClass = NioDatagramChannel.class;
        }

        final Bootstrap writeBootstrap = new Bootstrap()
                .group(writeGroup)
                .channel(channelClass)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) throws Exception {
                        ch.pipeline().addLast(new WriteHandler<>(msg.retain()));
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void exceptionCaught(final ChannelHandlerContext ctx,
                                                        final Throwable cause) {
                                if (!(cause instanceof PortUnreachableException)) {
                                    ctx.fireExceptionCaught(cause);
                                }
                            }
                        });
                    }
                });

        final ChannelGroup writeChannels = new DefaultChannelGroup(writeGroup.next());
        for (int i = 0; i < writeThreads; i++) {
            writeChannels.add(writeBootstrap.connect(HOST, PORT).sync().channel());
        }
        return writeChannels;
    }

    @Override
    protected Channel setupReadChannel() {
        final Class<? extends DatagramChannel> channelClass;
        if ("kqueue".equals(channelImpl)) {
            readGroup = new KQueueEventLoopGroup(1);
            channelClass = KQueueDatagramChannel.class;
        }
        else if ("epoll".equals(channelImpl)) {
            readGroup = new EpollEventLoopGroup(1);
            channelClass = EpollDatagramChannel.class;
        }
        else {
            readGroup = new NioEventLoopGroup(1);
            channelClass = NioDatagramChannel.class;
        }

        final Bootstrap readBootstrap = new Bootstrap()
                .group(readGroup)
                .channel(channelClass)
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                        if (msg instanceof DatagramPacket) {
                            ((DatagramPacket) msg).content().release();
                            receivedMsgs.incrementAndGet();
                        }
                    }
                });
        return readBootstrap.bind(HOST, PORT).channel();
    }

    @Override
    protected void teardownChannel() throws InterruptedException {
        writeGroup.shutdownGracefully().await();
        readGroup.shutdownGracefully().await();
    }
}
