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
package org.drasyl.example.connection;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.TraversingDrasylServerChannelInitializer;
import org.drasyl.handler.connection.ConnectionHandshakeCodec;
import org.drasyl.handler.connection.ConnectionHandshakeCompleted;
import org.drasyl.handler.connection.ConnectionHandshakeException;
import org.drasyl.handler.connection.ConnectionHandshakeHandler;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Scanner;

/**
 * This node will contact {@link ConnectionServer} and initiates a connection handshake.
 */
@SuppressWarnings({
        "common-java:DuplicatedBlocks",
        "java:S106",
        "java:S110",
        "java:S138",
        "java:S1188",
        "java:S1943",
        "java:S2093",
        "java:S2096",
        "java:S3776"
})
public class ConnectionClient {
    private static final String IDENTITY = System.getProperty("identity", "connection-client.identity");

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Please provide " + ConnectionServer.class.getSimpleName() + " address as first argument.");
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

                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx,
                                                           final Object evt) {
                                ctx.fireUserEventTriggered(evt);

                                if (evt instanceof AddPathAndSuperPeerEvent) {
                                    // create channel to server
                                    final DrasylChannel childChannel = new DrasylChannel((DrasylServerChannel) ctx.channel(), server);
                                    ctx.fireChannelRead(childChannel);

                                    ctx.pipeline().remove(ctx.name());
                                }
                            }
                        });
                    }
                })
                .childHandler(new ChannelInitializer<DrasylChannel>() {
                    @Override
                    protected void initChannel(final DrasylChannel ch) {
                        final ChannelPipeline p = ch.pipeline();

                        p.addLast(new ConnectionHandshakeCodec());
                        p.addLast(new ConnectionHandshakeHandler(Duration.ofSeconds(10), true));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx,
                                                           final Object evt) {
                                if (evt instanceof ConnectionHandshakeCompleted) {
                                    // handshake succeeded
                                    System.out.println("Handshake with " + ctx.channel().remoteAddress() + " succeeded.");

                                    // add your connection-related handler here
                                }
                                else {
                                    ctx.fireUserEventTriggered(evt);
                                }
                            }

                            @Override
                            public void exceptionCaught(final ChannelHandlerContext ctx,
                                                        final Throwable cause) {
                                if (cause instanceof ConnectionHandshakeException) {
                                    // handshake failed
                                    cause.printStackTrace();
                                }
                                else {
                                    ctx.fireExceptionCaught(cause);
                                }
                            }
                        });
                    }
                });

        try {
            final Channel ch = b.bind(identity.getAddress()).syncUninterruptibly().channel();
            Runtime.getRuntime().addShutdownHook(new Thread(ch::close));

            final Scanner userInput = new Scanner(System.in);
            while (ch.isOpen()) {
                System.out.println("\nType \"close\" to close the connection or type \"quit\" to shutdown the client without graceful connection teardown: ");
                final String command = userInput.next();
                if (command.startsWith("close")) {
                    System.out.println("Close the connection...");
                    ch.close().awaitUninterruptibly();
                    System.exit(0);
                }
                else if (command.startsWith("quit")) {
                    System.out.println("Quit without gracefully tear down connection...");
                    System.exit(0);
                }
            }
        }
        finally {
            udpServerGroup.shutdownGracefully();
            group.shutdownGracefully();
        }
    }
}
