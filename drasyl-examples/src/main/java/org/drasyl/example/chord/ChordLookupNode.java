package org.drasyl.example.chord;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
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
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

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
    private static final Logger LOG = LoggerFactory.getLogger(ChordLookupNode.class);
    private static final String IDENTITY = System.getProperty("identity", "chord-query.identity");

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

        final EventLoopGroup group = new NioEventLoopGroup(1);
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .handler(new RelayOnlyDrasylServerChannelInitializer(identity) {
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

            while (ch.isOpen()) {
                // begin to take user input
                final Scanner userInput = new Scanner(System.in);
                System.out.println("\nPlease enter your search key (or type \"quit\" to leave): ");
                String command = null;
                command = userInput.nextLine();

                // quit
                if (command.startsWith("quit")) {
                    ch.close().awaitUninterruptibly();
                }

                // search
                else if (command.length() > 0) {
                    final long hash = chordId(command);
                    LOG.error("SUBMIT!");
                    System.out.println("String `" + command + "` results in hash " + ChordUtil.chordIdHex(hash) + " (" + chordIdPosition(hash) + ")");
                    group.next().submit(() -> {
                        LOG.error("WRITE!");
                        ch.write(ChordLookup.of(contact, hash)).addListener((ChannelFutureListener) future -> {
                            LOG.error("WRITTEN!");
                            if (future.cause() != null) {
                                future.cause().printStackTrace();
                            }
                        });
                    });
                }
            }
        }
        finally {
            group.shutdownGracefully();
        }
    }
}
