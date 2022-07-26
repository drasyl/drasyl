package org.drasyl.example.chord;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.TraversingDrasylServerChannelInitializer;
import org.drasyl.handler.dht.chord.ChordAskPredecessor;
import org.drasyl.handler.dht.chord.ChordCodec;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.handler.dht.chord.ChordFixFingers;
import org.drasyl.handler.dht.chord.ChordJoinHandler;
import org.drasyl.handler.dht.chord.ChordStabilize;
import org.drasyl.handler.dht.chord.ChordTalker;
import org.drasyl.handler.dht.chord.ChordUtil;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

public class ChordNode {
    private static final String IDENTITY = System.getProperty("identity", "chord.identity");

    public static void main(final String[] args) throws IOException {
        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

        final long myId = ChordUtil.hashSocketAddress(identity.getAddress());
        final AtomicReference<IdentityPublicKey> predecessor = new AtomicReference<>();
        final ChordFingerTable fingerTable = new ChordFingerTable(identity.getIdentityPublicKey());
        System.out.println("My Address: " + identity.getAddress());
        System.out.println("My Id: " + ChordUtil.longTo8DigitHex(myId) + " (" + ChordUtil.chordPosition(myId) + ")");
        System.out.println();
        System.out.println("My Predecessor: " + predecessor.get());
        System.out.println("My Successor: " + fingerTable.get(1));
        System.out.println();

        final IdentityPublicKey contact = args.length > 0 ? IdentityPublicKey.of(args[0]) : null;

        final EventLoopGroup group = new NioEventLoopGroup(1);
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .handler(new TraversingDrasylServerChannelInitializer(identity, 50000 + (int) (myId % 10000)) {
                    @Override
                    protected void initChannel(final DrasylServerChannel ch) {
                        super.initChannel(ch);

                        final ChannelPipeline p = ch.pipeline();

                        p.addLast(new ChordCodec());
                        p.addLast(new ChordStabilize(predecessor, fingerTable));
                        p.addLast(new ChordFixFingers(fingerTable));
                        p.addLast(new ChordAskPredecessor(predecessor));
                        p.addLast(new ChordTalker(predecessor, fingerTable));
                        if (contact != null) {
                            p.addLast(new ChannelDuplexHandler() {
                                @Override
                                public void userEventTriggered(final ChannelHandlerContext ctx,
                                                               final Object evt) {
                                    ctx.fireUserEventTriggered(evt);
                                    if (evt instanceof AddPathAndSuperPeerEvent) {
                                        p.addAfter(p.context(ChordCodec.class).name(), null, new ChordJoinHandler(fingerTable, contact));

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
            final Channel ch = b.bind(identity.getAddress()).syncUninterruptibly().channel();

            // begin to take user input, "info" or "quit"
            final Scanner userinput = new Scanner(System.in);
            while(true) {
                System.out.println("\nType \"info\" to check this node's data or \n type \"quit\"to leave ring: ");
                String command = null;
                command = userinput.next();
                if (command.startsWith("quit")) {
//                    m_node.stopAllThreads();
//                    System.out.println("Leaving the ring...");
                    System.exit(0);
                }
                else if (command.startsWith("info")) {
                    System.out.println("==============================================================");
                    System.out.println();
                    System.out.println("LOCAL:\t\t" + identity.getAddress() + " " + ChordUtil.longTo8DigitHex(myId) + " (" + ChordUtil.chordPosition(myId) + ")");
                    System.out.println();
                    System.out.println("PREDECESSOR:\t" + predecessor.get() + " " + (predecessor.get() != null ? ChordUtil.longTo8DigitHex(ChordUtil.hashSocketAddress(predecessor.get())) + " (" + ChordUtil.chordPosition(ChordUtil.hashSocketAddress(predecessor.get())) + ")" : ""));
                    System.out.println();
                    System.out.println("FINGER TABLE:");
                    System.out.println(fingerTable);
                    System.out.println();
                    System.out.println("==============================================================");
                }
            }

//            ch.closeFuture().awaitUninterruptibly();
        }
        finally {
            group.shutdownGracefully();
        }
    }
}