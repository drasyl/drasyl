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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.TraversingDrasylServerChannelInitializer;
import org.drasyl.handler.codec.OverlayMessageToEnvelopeMessageCodec;
import org.drasyl.handler.dht.chord.ChordHousekeepingHandler;
import org.drasyl.handler.dht.chord.ChordJoinHandler;
import org.drasyl.handler.dht.chord.ChordUtil;
import org.drasyl.handler.dht.chord.LocalChordNode;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.handler.rmi.RmiCodec;
import org.drasyl.handler.rmi.RmiServerHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;
import org.drasyl.util.EventLoopGroupUtil;

import java.io.File;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Scanner;

import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdPosition;
import static org.drasyl.handler.dht.chord.LocalChordNode.BIND_NAME;

/**
 * Node that is part of the Chord ring.
 */
@SuppressWarnings({ "java:S106", "java:S110", "java:S2093" })
public class ChordCircleNode {
    private static final String IDENTITY = System.getProperty("identity", "chord.identity");

    @SuppressWarnings({ "java:S138", "java:S1166", "java:S1188", "java:S1943", "java:S2096" })
    public static void main(final String[] args) throws IOException {
        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

        final long myId = chordId(identity.getAddress());
        System.out.println("My Address : " + identity.getAddress());
        System.out.println("My Id      : " + ChordUtil.chordIdHex(myId) + " (" + chordIdPosition(myId) + ")");
        System.out.println();

        final DrasylAddress contact = args.length > 0 ? IdentityPublicKey.of(args[0]) : null;

        final RmiClientHandler client = new RmiClientHandler();
        final LocalChordNode localNode = new LocalChordNode(identity.getIdentityPublicKey(), client);

        final EventLoopGroup group = new DefaultEventLoopGroup();
        final EventLoopGroup udpServerGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);
        final PeersManager peersManager = new PeersManager();
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .handler(new TraversingDrasylServerChannelInitializer(identity, udpServerGroup, 50000 + (int) (myId % 10000), peersManager) {
                    @Override
                    protected void initChannel(final DrasylServerChannel ch) {
                        super.initChannel(ch);

                        final ChannelPipeline p = ch.pipeline();

                        final RmiServerHandler server = new RmiServerHandler();
                        p.addLast(new OverlayMessageToEnvelopeMessageCodec());
                        p.addLast(new RmiCodec());
                        p.addLast(client);
                        p.addLast(server);

                        server.bind(BIND_NAME, localNode);
                        p.addLast(new ChordHousekeepingHandler(localNode));

                        if (contact != null) {
                            p.addLast(new ChannelDuplexHandler() {
                                @Override
                                public void userEventTriggered(final ChannelHandlerContext ctx,
                                                               final Object evt) {
                                    ctx.fireUserEventTriggered(evt);
                                    if (evt instanceof AddPathAndSuperPeerEvent) {
                                        p.addLast(new ChordJoinHandler(contact, localNode));
                                        ctx.pipeline().remove(ctx.name());
                                    }
                                }
                            });
                        }
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

            // begin to take user input
            final Scanner userInput = new Scanner(System.in);
            while (ch.isOpen()) {
                System.out.println("\nType \"info\" to check this node's data or \n type \"quit\" to leave ring: ");
                final String command = userInput.next();

                // quit
                if (command.startsWith("quit")) {
                    System.out.println("Leaving the ring...");
                    ch.close().awaitUninterruptibly();
                    System.exit(0);
                }
                // info
                else if (command.startsWith("info")) {
                    System.out.println("==============================================================");
                    System.out.println();
                    System.out.print(localNode);
                    System.out.println();
                    System.out.println("==============================================================");
                }
            }
        }
        catch (final NoSuchElementException e) {
            // ignore
        }
        finally {
            udpServerGroup.shutdownGracefully();
            group.shutdownGracefully();
        }
    }
}
