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
package org.drasyl.example.chord;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.RelayOnlyDrasylServerChannelInitializer;
import org.drasyl.handler.codec.OverlayMessageToEnvelopeMessageCodec;
import org.drasyl.handler.dht.chord.ChordLookup;
import org.drasyl.handler.dht.chord.ChordLookupHandler;
import org.drasyl.handler.dht.chord.ChordResponse;
import org.drasyl.handler.dht.chord.ChordUtil;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.handler.rmi.RmiCodec;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;
import org.drasyl.util.EventLoopGroupUtil;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdPosition;

/**
 * Node that lookups in the Chord cycle.
 */
@SuppressWarnings({ "java:S106", "java:S110", "java:S2093" })
public class ChordLookupNode {
    private static final String IDENTITY = System.getProperty("identity", "chord-query.identity");

    @SuppressWarnings({ "java:S1943", "java:S2096" })
    public static void main(final String[] args) throws IOException {
        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

        final IdentityPublicKey contact = args.length > 0 ? IdentityPublicKey.of(args[0]) : null;

        if (contact == null) {
            System.err.println("Please provide ChordCircleNode address as first argument.");
            System.exit(1);
        }
        else {
            System.out.println("My contact node: " + contact);
        }

        final EventLoopGroup group = new DefaultEventLoopGroup(1);
        final EventLoopGroup udpServerGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .handler(new RelayOnlyDrasylServerChannelInitializer(identity, udpServerGroup) {
                    @Override
                    protected void initChannel(final DrasylServerChannel ch) {
                        super.initChannel(ch);

                        final ChannelPipeline p = ch.pipeline();

                        final RmiClientHandler client = new RmiClientHandler();
                        p.addLast(new ChordLookupHandler(client));
                        p.addLast(new SimpleChannelInboundHandler<ChordResponse>() {
                            @Override
                            protected void channelRead0(final ChannelHandlerContext ctx,
                                                        final ChordResponse msg) {
                                System.out.println("Hash " + ChordUtil.chordIdHex(msg.getId()) + " (" + chordIdPosition(msg.getId()) + ") belongs to node " + msg.getAddress() + " (" + chordIdPosition(msg.getAddress()) + ")");
                            }
                        });

                        p.addLast(new OverlayMessageToEnvelopeMessageCodec());
                        p.addLast(new RmiCodec());
                        p.addLast(client);
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
                System.out.println("\nPlease enter your search key (or type \"quit\" to leave): ");
                final String command = userInput.nextLine();

                // quit
                if (command.startsWith("quit")) {
                    System.out.println("Quit node...");
                    ch.close().awaitUninterruptibly();
                    System.exit(0);
                }
                // search
                else if (command.length() > 0) {
                    final long hash = chordId(command);
                    System.out.println("String `" + command + "` results in hash " + ChordUtil.chordIdHex(hash) + " (" + chordIdPosition(hash) + ")");
                    ch.write(ChordLookup.of(contact, hash)).addListener((ChannelFutureListener) future -> {
                        if (future.cause() != null) {
                            future.cause().printStackTrace();
                        }
                    });
                }
            }
        }
        finally {
            udpServerGroup.shutdownGracefully();
            group.shutdownGracefully();
        }
    }
}
