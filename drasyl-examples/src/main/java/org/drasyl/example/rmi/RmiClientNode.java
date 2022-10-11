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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.FutureListener;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.TraversingDrasylServerChannelInitializer;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.handler.rmi.RmiCodec;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;

import java.io.File;
import java.io.IOException;

@SuppressWarnings({ "java:S106", "java:S110", "java:S125", "java:S1188", "java:S2096" })
public class RmiClientNode {
    private static final String IDENTITY = System.getProperty("identity", "rmi-client.identity");

    public static void main(final String[] args) throws IOException {
        // server address
        if (args.length != 1) {
            System.err.println("Please provide RmiClientServer address as first argument.");
            System.exit(1);
        }
        final DrasylAddress server = IdentityPublicKey.of(args[0]);

        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

        System.out.println("My address = " + identity.getAddress());

        final RmiClientHandler client = new RmiClientHandler();
        final MessengerService service = client.lookup("MessengerService", MessengerService.class, server);
        System.out.println("Created stub for remote object of type `" + MessengerService.class.getSimpleName() + "` bound to name `MessengerService` at node `" + server + "`.");

        final EventLoopGroup group = new DefaultEventLoopGroup();
        final NioEventLoopGroup udpServerGroup = new NioEventLoopGroup(1);
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .handler(new TraversingDrasylServerChannelInitializer(identity, udpServerGroup, 22528) {
                    @Override
                    protected void initChannel(final DrasylServerChannel ch) {
                        super.initChannel(ch);

                        final ChannelPipeline p = ch.pipeline();

                        p.addLast(new RmiCodec());
                        p.addLast(client);

                        p.addLast(new ChannelDuplexHandler() {
                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx,
                                                           final Object evt) {
                                ctx.fireUserEventTriggered(evt);

                                if (evt instanceof AddPathAndSuperPeerEvent) {
                                    // wait for connection to super peer
                                    System.out.print("Perform remote method invocation...");
//                                    service.trigger();
                                    service.sendMessage("Client Message").addListener((FutureListener<String>) future -> {
                                        System.out.println("done!");
                                        if (future.isSuccess()) {
                                            System.out.println("Succeeded: " + future.getNow());
                                        }
                                        else {
                                            System.err.println("Errored:");
                                            future.cause().printStackTrace();
                                        }
                                    });

                                    ctx.pipeline().remove(ctx.name());
                                }
                            }
                        });
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
            final Channel ch = b.bind(identity.getAddress()).syncUninterruptibly().channel();
            Runtime.getRuntime().addShutdownHook(new Thread(ch::close));
            ch.closeFuture().awaitUninterruptibly();
        }
        finally {
            udpServerGroup.shutdownGracefully();
            group.shutdownGracefully();
        }
    }
}
