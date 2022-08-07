package org.drasyl.example.chord;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.channel.TraversingDrasylServerChannelInitializer;
import org.drasyl.handler.dht.chord.ChordLookup;
import org.drasyl.handler.dht.chord.ChordQueryHandler;
import org.drasyl.handler.dht.chord.ChordResponse;
import org.drasyl.handler.dht.chord.ChordUtil;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.handler.rmi.RmiCodec;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdPosition;

@SuppressWarnings({ "java:S106", "java:S110", "java:S2093" })
public class ChordQuery {
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
            System.out.println("\nInvalid input. Now exit.\n");
            System.exit(1);
        }
        else {
            System.out.println("My contact node: " + contact);
        }

        final EventLoopGroup group = new NioEventLoopGroup(1);
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .handler(new TraversingDrasylServerChannelInitializer(identity, 60_000) {
                    @Override
                    protected void initChannel(final DrasylServerChannel ch) {
                        super.initChannel(ch);

                        final ChannelPipeline p = ch.pipeline();

                        p.addLast(new ChordQueryHandler());
                        p.addLast(new SimpleChannelInboundHandler<ChordResponse>() {
                            @Override
                            protected void channelRead0(final ChannelHandlerContext ctx,
                                                        final ChordResponse msg) {
                                System.out.println("Hash " + ChordUtil.chordIdHex(msg.getId()) + " (" + chordIdPosition(msg.getId()) + ") belongs to node " + msg.getAddress() + " (" + chordIdPosition(msg.getAddress()) + ")");
                            }
                        });

                        p.addLast(new MessageToMessageCodec<OverlayAddressedMessage<?>, AddressedEnvelope<?, ?>>() {
                            @Override
                            protected void encode(ChannelHandlerContext ctx,
                                                  AddressedEnvelope<?, ?> msg,
                                                  List<Object> out) {
                                out.add(new OverlayAddressedMessage<>(msg.content(), (DrasylAddress) msg.recipient(), (DrasylAddress) msg.sender()).retain());
                            }

                            @Override
                            protected void decode(ChannelHandlerContext ctx,
                                                  OverlayAddressedMessage<?> msg,
                                                  List<Object> out) {
                                out.add(new DefaultAddressedEnvelope<>(msg.content(), msg.recipient(), msg.sender()).retain());
                            }
                        });
                        p.addLast(new RmiCodec());
                        p.addLast(new RmiClientHandler());
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
                    final String[] s = command.split(" ");
                    final long hash = chordId(s[1]);
                    System.out.println("String `" + s[1] + "` results in hash " + ChordUtil.chordIdHex(hash) + " (" + chordIdPosition(hash) + ")");
                    ch.write(ChordLookup.of(IdentityPublicKey.of(s[0]), hash)).addListener((ChannelFutureListener) future -> {
                        if (future.cause() != null) {
                            System.err.println(future.cause());
                            future.cause().printStackTrace();
                        }
                    }).awaitUninterruptibly();
                }
            }
        }
        finally {
            group.shutdownGracefully();
        }
    }
}
