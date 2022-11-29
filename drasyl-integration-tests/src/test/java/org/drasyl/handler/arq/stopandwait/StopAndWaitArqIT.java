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
package org.drasyl.handler.arq.stopandwait;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import org.junit.jupiter.api.Test;
import test.DropEveryNthInboundMessageHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StopAndWaitArqIT {
    @Test
    void shouldWorkOnUnreliableLink() throws Exception {
        final CountDownLatch latch = new CountDownLatch(5);
        final List<Object> received = new ArrayList<>(5);

        // server
        final EventLoopGroup group = new DefaultEventLoopGroup();
        final LocalAddress serverAddress = new LocalAddress("StopAndWaitArqHandlerIT");
        final Channel serverChannel = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(group)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();

                        p.addLast(new DropEveryNthInboundMessageHandler(3));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            private Object prevMsg;

                            @Override
                            public void channelRead(final ChannelHandlerContext ctx,
                                                    final Object msg) {
                                if (prevMsg == null) {
                                    prevMsg = msg;
                                    ctx.fireChannelRead(prevMsg);
                                }
                                else {
                                    // replay previous message
                                    ctx.fireChannelRead(prevMsg);
                                    ctx.pipeline().remove(this);
                                }
                            }
                        });
                        p.addLast(new StopAndWaitArqCodec());
                        p.addLast(new StopAndWaitArqHandler(10));
                        p.addLast(new ByteToStopAndWaitArqDataCodec());
                        p.addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                            @Override
                            protected void channelRead0(final ChannelHandlerContext ctx,
                                                        final ByteBuf msg) {
                                latch.countDown();
                                received.add(msg.readByte());
                            }
                        });
                    }
                })
                .bind(serverAddress).sync().channel();

        // client
        final Channel clientChannel = new Bootstrap()
                .channel(LocalChannel.class)
                .group(group)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();

                        p.addLast(new DropEveryNthInboundMessageHandler(3));
                        p.addLast(new StopAndWaitArqCodec());
                        p.addLast(new StopAndWaitArqHandler(10));
                        p.addLast(new ByteToStopAndWaitArqDataCodec());
                    }
                })
                .connect(serverAddress).sync().channel();

        try {
            // initiate handshake
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{ 0 })).sync();
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{ 1 })).sync();
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{ 2 })).sync();
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{ 3 })).sync();
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{ 4 })).sync();

            // wait for completion
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(List.of((byte) 0, (byte) 1, (byte) 2, (byte) 3, (byte) 4), received);
        }
        finally {
            clientChannel.close().sync();
            serverChannel.close().sync();
            group.shutdownGracefully().sync();
        }
    }
}
