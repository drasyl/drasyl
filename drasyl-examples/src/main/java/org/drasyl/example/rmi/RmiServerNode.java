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

package org.drasyl.example.rmi;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.DefaultDrasylServerChannelInitializer;
import org.drasyl.handler.rmi.RmiCodec;
import org.drasyl.handler.rmi.RmiServerHandler;
import org.drasyl.identity.Identity;
import org.drasyl.node.identity.IdentityManager;
import org.drasyl.util.EventLoopGroupUtil;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

import static org.drasyl.channel.DrasylServerChannelConfig.UDP_BIND;

@SuppressWarnings({ "java:S106", "java:S110" })
public class RmiServerNode {
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

        final RmiServerHandler server = new RmiServerHandler();
        final MessengerService service = new MessengerServiceImpl();
        server.bind("MessengerService", service);
        System.out.println("Object `" + service + "` bound to `MessengerService` and can now be accessed by other nodes.");

        final EventLoopGroup group = new DefaultEventLoopGroup();
        final EventLoopGroup udpServerGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .option(UDP_BIND, new InetSocketAddress(22527))
                .handler(new DefaultDrasylServerChannelInitializer() {
                    @Override
                    protected void initChannel(final DrasylServerChannel ch) {
                        super.initChannel(ch);

                        final ChannelPipeline p = ch.pipeline();

                        p.addLast(new RmiCodec());
                        p.addLast(server);
                    }
                })
                .childHandler(new ChannelInitializer<DrasylChannel>() {
                    @Override
                    protected void initChannel(final DrasylChannel ch) {
                        // data plane channel
                        // do nothing
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
