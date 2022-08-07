package org.drasyl.example.chord;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.channel.TraversingDrasylServerChannelInitializer;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.handler.dht.chord.ChordJoinHandler;
import org.drasyl.handler.dht.chord.ChordUtil;
import org.drasyl.handler.dht.chord.MyChordService;
import org.drasyl.handler.dht.chord.task.ChordAskPredecessorTask;
import org.drasyl.handler.dht.chord.task.ChordFixFingersTask;
import org.drasyl.handler.dht.chord.task.ChordStabilizeTask;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.handler.rmi.RmiCodec;
import org.drasyl.handler.rmi.RmiServerHandler;
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

        final long myId = chordId(identity.getAddress());
        final ChordFingerTable fingerTable = new ChordFingerTable(identity.getIdentityPublicKey());
        System.out.println("My Address : " + identity.getAddress());
        System.out.println("My Id      : " + ChordUtil.chordIdHex(myId) + " (" + chordIdPosition(myId) + ")");
        System.out.println();

        final IdentityPublicKey contact = args.length > 0 ? IdentityPublicKey.of(args[0]) : null;

        final EventLoopGroup group = new NioEventLoopGroup();
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .handler(new TraversingDrasylServerChannelInitializer(identity, 50000 + (int) (myId % 10000)) {
                    @Override
                    protected void initChannel(final DrasylServerChannel ch) {
                        super.initChannel(ch);

                        final ChannelPipeline p = ch.pipeline();

                        final RmiServerHandler server = new RmiServerHandler();
                        final RmiClientHandler client = new RmiClientHandler();
                        server.bind("ChordService", new MyChordService(fingerTable, client));

                        p.addLast(new ChordStabilizeTask(fingerTable, 500));
                        p.addLast(new ChordFixFingersTask(fingerTable, 500));
                        p.addLast(new ChordAskPredecessorTask(fingerTable, 500));

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
                        p.addLast(client);
                        p.addLast(server);

                        if (contact != null) {
                            p.addLast(new ChannelDuplexHandler() {
                                @Override
                                public void userEventTriggered(final ChannelHandlerContext ctx,
                                                               final Object evt) {
                                    ctx.fireUserEventTriggered(evt);
                                    if (evt instanceof AddPathAndSuperPeerEvent) {
                                        p.addAfter(p.context(ChordStabilizeTask.class).name(), null, new ChordJoinHandler(fingerTable, contact));
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
            final Scanner userInput = new Scanner(System.in);
            while (ch.isOpen()) {
                System.out.println("\nType \"info\" to check this node's data or \n type \"quit\"to leave ring: ");
                String command = null;
                command = userInput.next();
                if (command.startsWith("quit")) {
                    System.out.println("Leaving the ring...");
                    ch.close();
                    System.exit(0);
                }
                else if (command.startsWith("info")) {
                    System.out.println("==============================================================");
                    System.out.println();
                    System.out.print(fingerTable);
                    System.out.println();
                    System.out.println("==============================================================");
                }
            }
        }
        finally {
            group.shutdownGracefully();
        }
    }
}
