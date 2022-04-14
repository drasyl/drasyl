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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StopAndWaitArqIT {
    @Test
    void shouldHandleUnreliableLink() throws Exception {
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

                        p.addLast(new ChannelInboundHandlerAdapter() {
                            private int i = 0;

                            @Override
                            public void channelRead(ChannelHandlerContext ctx,
                                                    Object msg) throws Exception {
                                if (!(i++ % 2 == 0)) {
                                    // drop every 2nd message
                                    super.channelRead(ctx, msg);
                                }
                            }
                        });
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            private Object prevMsg;

                            @Override
                            public void channelRead(ChannelHandlerContext ctx,
                                                    Object msg) throws Exception {
                                if (prevMsg == null) {
                                    prevMsg = msg;
                                    super.channelRead(ctx, msg);
                                }
                                else {
                                    // replay previous message
                                    super.channelRead(ctx, prevMsg);
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
