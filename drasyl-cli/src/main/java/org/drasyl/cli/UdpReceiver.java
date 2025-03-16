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
package org.drasyl.cli;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.LongAdder;

import static io.netty.channel.unix.UnixChannelOption.SO_REUSEPORT;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class UdpReceiver {
    public static void main(String[] args) {
        final InetSocketAddress address = new InetSocketAddress("0.0.0.0", 10_000);
        final int count = 2;

        final EventLoopGroup group = new KQueueEventLoopGroup();
        try {
            for (int i = 0; i < count; i++) {
                final Channel channel = new Bootstrap()
                        .option(SO_REUSEPORT, true)
                        .group(group)
                        .channel(KQueueDatagramChannel.class)
                        .handler(new ChannelInitializer<>() {
                            @Override
                            protected void initChannel(Channel ch) {
                                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                    private final LongAdder adder = new LongAdder();

                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                        super.channelActive(ctx);

                                        ctx.channel().eventLoop().scheduleAtFixedRate(() -> {
                                            System.out.println(ctx.channel() + " " + adder.sum());
                                        }, 5_000, 5_000, MILLISECONDS);
                                    }

                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx,
                                                            Object msg) {
                                        adder.increment();
                                        ReferenceCountUtil.release(msg);
                                    }
                                });
                            }
                        })
                        .bind(address).syncUninterruptibly().channel();
                System.out.println("UdpReceiver.main " + channel);
            }

            while (true) {
            }
        }
        finally {
            group.shutdownGracefully().awaitUninterruptibly();
        }
    }
}
