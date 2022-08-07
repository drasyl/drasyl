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
package org.drasyl.handler.rmi;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.SucceededFuture;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RmiIT {
    @Test
    void shouldPerformInvocation() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);

        // server
        final RmiServerHandler server = new RmiServerHandler();
        final EventLoopGroup group = new DefaultEventLoopGroup();
        final LocalAddress serverAddress = new LocalAddress("RmiIT");
        final Channel serverChannel = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(group)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new FlipEnvelopeAddressesHandler());
                        p.addLast(new RmiCodec());
                        p.addLast(server);
                    }
                })
                .bind(serverAddress).sync().channel();

        // client
        final RmiClientHandler client = new RmiClientHandler();
        final Channel clientChannel = new Bootstrap()
                .channel(LocalChannel.class)
                .group(group)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new FlipEnvelopeAddressesHandler());
                        p.addLast(new RmiCodec());
                        p.addLast(client);
                    }
                })
                .connect(serverAddress).sync().channel();

        try {
            server.bind("MyService", new MyServiceImpl());
            MyService stub = client.lookup("MyService", MyService.class, serverAddress);

            stub.doNothing();
            final Future<Integer> additionFuture = stub.doAddition(4, 2).addListener((FutureListener<Integer>) f -> latch.countDown());
            final Future<String> whoAmIFuture = stub.whoAmI().addListener((FutureListener<String>) f -> latch.countDown());

            assertTrue(latch.await(5, TimeUnit.SECONDS));

            assertTrue(additionFuture.isSuccess());
            assertEquals(4 + 2, additionFuture.getNow());

            assertTrue(whoAmIFuture.isSuccess());
            assertThat(whoAmIFuture.getNow(), startsWith("local:"));
        }
        finally {
            server.unbind("MyService");
            clientChannel.close().sync();
            serverChannel.close().sync();
            group.shutdownGracefully().sync();
        }
    }

    @Test
    void shouldFailWhenBindingDoesNotExist() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        // server
        final RmiServerHandler server = new RmiServerHandler();
        final EventLoopGroup group = new DefaultEventLoopGroup();
        final LocalAddress serverAddress = new LocalAddress("RmiIT");
        final Channel serverChannel = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(group)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new FlipEnvelopeAddressesHandler());
                        p.addLast(new RmiCodec());
                        p.addLast(server);
                    }
                })
                .bind(serverAddress).sync().channel();

        // client
        final RmiClientHandler client = new RmiClientHandler();
        final Channel clientChannel = new Bootstrap()
                .channel(LocalChannel.class)
                .group(group)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new FlipEnvelopeAddressesHandler());
                        p.addLast(new RmiCodec());
                        p.addLast(client);
                    }
                })
                .connect(serverAddress).sync().channel();

        try {
            MyService stub = client.lookup("MyService", MyService.class, serverAddress);

            final Future<Integer> additionFuture = stub.doAddition(4, 2).addListener((FutureListener<Integer>) f -> latch.countDown());

            assertTrue(latch.await(5, TimeUnit.SECONDS));

            assertThat(additionFuture.cause(), instanceOf(RmiException.class));
        }
        finally {
            server.unbind("MyService");
            clientChannel.close().sync();
            serverChannel.close().sync();
            group.shutdownGracefully().sync();
        }
    }

    interface MyService {
        void doNothing();

        Future<Integer> doAddition(final int a, final int b);

        Future<String> whoAmI();
    }

    static class MyServiceImpl implements MyService {
        @RmiCaller
        private LocalAddress caller;

        @Override
        public void doNothing() {
            // do internal magic here...
        }

        @Override
        public Future<Integer> doAddition(int a, int b) {
            System.out.println("Got call from `" + caller + "`");
            return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, a + b);
        }

        @Override
        public Future<String> whoAmI() {
            return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, caller != null ? caller.toString() : null);
        }
    }

    private static class FlipEnvelopeAddressesHandler extends SimpleChannelInboundHandler<AddressedEnvelope<?, SocketAddress>> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx,
                                    AddressedEnvelope<?, SocketAddress> msg) {
            ctx.fireChannelRead(new DefaultAddressedEnvelope<>(msg.content(), msg.sender(), msg.recipient()));
        }
    }
}
