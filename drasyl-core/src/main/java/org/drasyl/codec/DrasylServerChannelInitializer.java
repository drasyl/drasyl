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
package org.drasyl.codec;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.drasyl.util.EventLoopGroupUtil;

import static org.drasyl.util.NettyUtil.getBestDatagramChannel;

class DrasylServerChannelInitializer extends ChannelInitializer<Channel> {
    @Override
    protected void initChannel(final Channel ch) throws Exception {
        ch.pipeline().addFirst(new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx,
                                        final DatagramPacket msg) throws Exception {
                System.out.println("NettyCodecExample.channelRead0");
                final Channel channel = new DrasylChannel(ctx.channel());

                ctx.fireChannelRead(channel);
            }
        });
        ch.pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(final ChannelHandlerContext ctx,
                              final Object msg,
                              final ChannelPromise promise) throws Exception {
                super.write(ctx, msg, promise);
            }
        });
        ch.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
            private Channel channel;

            @Override
            public void channelRead(final ChannelHandlerContext ctx,
                                    final Object msg) throws Exception {
                if (channel != null) {
                    System.out.println("NettyCodecExample.channelRead");
                    channel.read();
                }

                super.channelRead(ctx, msg);
            }

            @Override
            public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
                super.channelInactive(ctx);

                channel.close().awaitUninterruptibly();
            }

            @Override
            public void channelActive(final ChannelHandlerContext ctx) throws Exception {
                System.out.println("NettyCodecExample.channelActive");

                final Bootstrap bootstrap = new Bootstrap()
                        .group(EventLoopGroupUtil.getInstanceBest())
                        .channel(getBestDatagramChannel())
                        .option(ChannelOption.SO_BROADCAST, false)
//                                        .option(ChannelOption.AUTO_READ, false)
                        .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                            @Override
                            public void channelReadComplete(final ChannelHandlerContext ctx) throws Exception {
                                ctx.fireChannelReadComplete();
                            }

                            @Override
                            protected void channelRead0(final ChannelHandlerContext channelCtx,
                                                        final DatagramPacket packet) {
                                System.out.println("Datagram received :" + packet);
                                ctx.fireChannelRead(packet);
                            }
                        });
                final ChannelFuture future1 = bootstrap.bind(9888);
                future1.awaitUninterruptibly();

                System.out.println("future1.isSuccess() = " + future1.isSuccess());

                channel = future1.channel();

                super.channelActive(ctx);
            }
        });
    }
}
