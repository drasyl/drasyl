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

package org.drasyl.example.libdrasyl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.rs.RustDrasylChannel;
import org.drasyl.channel.rs.RustDrasylServerChannel;
import org.drasyl.identity.Identity;
import org.drasyl.node.identity.IdentityManager;
import org.drasyl.util.EventLoopGroupUtil;

import java.io.File;
import java.io.IOException;

import static org.drasyl.channel.rs.RustDrasylServerChannelConfig.UDP_PORT;

@SuppressWarnings({ "java:S106", "java:S110" })
public class LibdrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "rmi-server.identity");

    public static void main(final String[] args) throws IOException {
        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

        System.out.println("My address = " + identity.getAddress());

        final EventLoopGroup group = new DefaultEventLoopGroup();
        final EventLoopGroup udpServerGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(RustDrasylServerChannel.class)
                .option(UDP_PORT, 22528)
                .handler(new ChannelInitializer<RustDrasylServerChannel>() {
                    @Override
                    protected void initChannel(RustDrasylServerChannel ch) {
                        // do nothing
                    }
                })
                .childHandler(new ChannelInitializer<RustDrasylChannel>() {
                    @Override
                    protected void initChannel(final RustDrasylChannel ch) {
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                            @Override
                            protected void channelRead0(final ChannelHandlerContext ctx,
                                                        final ByteBuf msg) {
                                ch.writeAndFlush(msg.retain());
                            }
                        });
                    }
                });

        try {
            final Channel ch = b.bind(identity).syncUninterruptibly().channel();
            Runtime.getRuntime().addShutdownHook(new Thread(ch::close));
            ch.closeFuture().awaitUninterruptibly();
        }
        finally {
            udpServerGroup.shutdownGracefully();
            group.shutdownGracefully();
        }
    }
}
