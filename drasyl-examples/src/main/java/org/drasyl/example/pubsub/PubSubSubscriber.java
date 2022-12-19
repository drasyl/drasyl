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
package org.drasyl.example.pubsub;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.FutureListener;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.TraversingDrasylServerChannelInitializer;
import org.drasyl.handler.pubsub.PubSubCodec;
import org.drasyl.handler.pubsub.PubSubPublish;
import org.drasyl.handler.pubsub.PubSubSubscribe;
import org.drasyl.handler.pubsub.PubSubSubscribeHandler;
import org.drasyl.handler.pubsub.PubSubUnsubscribe;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;
import org.drasyl.util.EventLoopGroupUtil;

import java.io.File;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Scanner;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Subscribes to a pub/sub broker.
 */
@SuppressWarnings({
        "java:S106",
        "java:S109",
        "java:S110",
        "java:S134",
        "java:S138",
        "java:S1166",
        "java:S1188",
        "java:S1943",
        "java:S2093",
        "java:S2096",
        "java:S3776"
})
public class PubSubSubscriber {
    private static final String IDENTITY = System.getProperty("identity", "subscriber.identity");

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Please provide broker address as first argument.");
            System.exit(1);
        }
        final IdentityPublicKey broker = IdentityPublicKey.of(args[0]);

        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

        final EventLoopGroup group = new DefaultEventLoopGroup(1);
        final EventLoopGroup udpServerGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .handler(new TraversingDrasylServerChannelInitializer(identity, udpServerGroup, 0) {
                    @Override
                    protected void initChannel(final DrasylServerChannel ch) {
                        super.initChannel(ch);

                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new PubSubCodec());
                        p.addLast(new PubSubSubscribeHandler(broker));
                        p.addLast(new SimpleChannelInboundHandler<PubSubPublish>() {
                            @Override
                            protected void channelRead0(final ChannelHandlerContext ctx,
                                                        final PubSubPublish msg) {
                                System.out.println("Got publication for topic `" + msg.getTopic() + "`: " + new String(ByteBufUtil.getBytes(msg.getContent()), UTF_8));
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

            // begin to take user input
            final Scanner userInput = new Scanner(System.in);
            while (ch.isOpen()) {
                System.out.println("\nType \"subscribe <topic>\", \"unsubscribe <topic>\", or \"quit\": ");
                final String command = userInput.nextLine();

                // quit
                if (command.startsWith("quit")) {
                    System.out.println("Stop subscriber...");
                    ch.close().awaitUninterruptibly();
                }
                // subscribe
                else if (command.startsWith("subscribe")) {
                    final String[] parts = command.split(" ", 2);
                    if (parts.length < 2) {
                        System.out.println("No topic given.");
                    }
                    else {
                        final String topic = parts[1];

                        final PubSubSubscribe subscribe = PubSubSubscribe.of(topic);
                        ch.writeAndFlush(subscribe).addListener((FutureListener<Void>) future -> {
                            if (future.isSuccess()) {
                                System.out.println("Subscribed to topic `" + topic + "`.");
                            }
                            else {
                                System.err.println("Failed to subscribe to topic `" + topic + "`.");
                                future.cause().printStackTrace();
                            }
                        });
                    }
                }
                // unsubscribe
                else if (command.startsWith("unsubscribe")) {
                    final String[] parts = command.split(" ", 2);
                    if (parts.length < 2) {
                        System.out.println("No topic given.");
                    }
                    else {
                        final String topic = parts[1];

                        final PubSubUnsubscribe unsubscribe = PubSubUnsubscribe.of(topic);
                        ch.writeAndFlush(unsubscribe).addListener((FutureListener<Void>) future -> {
                            if (future.isSuccess()) {
                                System.out.println("Unsubscribed from topic `" + topic + "`.");
                            }
                            else {
                                System.err.println("Failed to unsubscribe from topic `" + topic + "`.");
                                future.cause().printStackTrace();
                            }
                        });
                    }
                }
            }
        }
        catch (final NoSuchElementException e) {
            // ignore
        }
        finally {
            udpServerGroup.shutdownGracefully();
            // give teardown messages of PubSubSubscribeHandler chance to be sent
            group.next().submit(() -> {
                // noop
            }).awaitUninterruptibly();
            group.shutdownGracefully();
        }
    }
}
