/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.sntp;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.RandomUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SNTPClient {
    public static final int NTP_PORT = 123;
    public static final List<SocketAddress> NTP_SERVERS = List.of(
            InetSocketAddress.createUnresolved("pool.ntp.org", NTP_PORT),
            InetSocketAddress.createUnresolved("time.apple.com", NTP_PORT),
            InetSocketAddress.createUnresolved("time.google.com", NTP_PORT),
            InetSocketAddress.createUnresolved("time.cloudflare.com", NTP_PORT)
    );

    public static void main(final String[] args) {
        SNTPClient.getOffset()
                .completeOnTimeout(null, 3, TimeUnit.SECONDS)
                .whenComplete((v, e) -> System.out.println(v.longValue() + " ms"));
    }

    public static CompletableFuture<Long> getOffset() {
        return getOffset(NTP_SERVERS.get(RandomUtil.randomInt(NTP_SERVERS.size() - 1)));
    }

    public static CompletableFuture<Long> getOffset(final SocketAddress server) {
        final CompletableFuture<Long> result = new CompletableFuture<>();

        final EventLoopGroup group = EventLoopGroupUtil.getBestEventLoopGroup(1);
        final Bootstrap b = new Bootstrap()
                .group(group)
                .channel(EventLoopGroupUtil.getBestDatagramChannel())
                .handler(new ChannelInitializer<DatagramChannel>() {
                    protected void initChannel(final DatagramChannel channel) {
                        final AtomicLong responseTime = new AtomicLong();
                        channel.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                            @Override
                            protected void channelRead0(final ChannelHandlerContext ctx,
                                                        final DatagramPacket msg) {
                                final long time = System.currentTimeMillis();
                                responseTime.set(time);
                                ctx.fireChannelRead(msg.retain());
                            }
                        });
                        channel.pipeline().addLast(new SNTPCodec());
                        channel.pipeline().addLast(new SNTPHandler(result, responseTime));
                    }
                });
        
        final Channel channel = b.connect(server).channel();

        result.whenComplete((value, error) -> {
            if (channel.isOpen()) {
                channel.close();
            }
            if (!group.isShuttingDown()) {
                group.shutdownGracefully();
            }
        });

        return result;
    }
}
