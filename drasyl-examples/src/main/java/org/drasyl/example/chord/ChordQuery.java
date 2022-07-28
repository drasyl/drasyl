package org.drasyl.example.chord;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.TraversingDrasylServerChannelInitializer;
import org.drasyl.handler.dht.chord.ChordCodec;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdPosition;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdToHex;
import static org.drasyl.handler.dht.chord.requester.ChordFindSuccessorRequester.findSuccessorRequest;
import static org.drasyl.handler.dht.chord.requester.ChordKeepRequester.keepRequest;
import static org.drasyl.handler.dht.chord.requester.ChordYourPredecessorRequester.yourPredecessorRequest;
import static org.drasyl.handler.dht.chord.requester.ChordYourSuccessorRequester.yourSuccessorRequest;

public class ChordQuery {
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
        final AtomicReference<IdentityPublicKey> predecessor = new AtomicReference<>();
        final ChordFingerTable fingerTable = new ChordFingerTable(identity.getIdentityPublicKey());
        System.out.println("My Address: " + identity.getAddress());
        System.out.println("My Id: " + chordIdToHex(myId) + " (" + chordIdPosition(myId) + ")");
        System.out.println();
        System.out.println("My Predecessor: " + predecessor.get());
        System.out.println("My Successor: " + fingerTable.get(1));
        System.out.println();

        final IdentityPublicKey contact = args.length > 0 ? IdentityPublicKey.of(args[0]) : null;

        if (contact == null) {
            System.out.println("\nInvalid input. Now exit.\n");
            System.exit(1);
        }

        final AtomicBoolean first = new AtomicBoolean(true);
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
                        p.addLast(new ChannelDuplexHandler() {
                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx,
                                                           final Object evt) {
                                ctx.fireUserEventTriggered(evt);
                                if (first.get() && evt instanceof AddPathAndSuperPeerEvent) {
                                    first.set(false);
                                    keepRequest(ctx, contact).addListener((FutureListener<Void>) future -> {
                                        if (future.cause() != null) { // FIXME: ich glaube hier ist im fehlerfall NULL
                                            System.out.println("\nCannot find node you are trying to contact. Now exit.\n");
                                            System.exit(0);
                                        }

                                        // it's alive, print connection info
                                        System.out.println("Connection to node " + contact + ", position " + chordId(contact) + " (" + chordIdPosition(contact) + ").");

                                        // check if system is stable
                                        checkStable(ctx, contact, ctx.executor().newPromise()).addListener(new FutureListener<>() {
                                            @Override
                                            public void operationComplete(final Future<Void> future) {
                                                takeInput(ctx, contact);
                                            }
                                        });
                                    });

//                                    ctx.pipeline().remove(ctx.name());
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
            ch.closeFuture().awaitUninterruptibly();
        }
        finally {
            group.shutdownGracefully();
        }
    }

    private static void takeInput(final ChannelHandlerContext ctx,
                                  final IdentityPublicKey contact) {
        // begin to take user input
        final Scanner userinput = new Scanner(System.in);
        System.out.println("\nPlease enter your search key (or type \"quit\" to leave): ");
        String command = null;
        command = userinput.nextLine();

        // quit
        if (command.startsWith("quit")) {
            System.exit(0);
        }

        // search
        else if (command.length() > 0) {
            final long hash = chordId(command);
            System.out.println("\nHash value is " + Long.toHexString(hash) + " (" + chordIdPosition(hash) + ")");
            findSuccessorRequest(ctx, hash, contact).addListener((FutureListener<IdentityPublicKey>) future -> {
                final IdentityPublicKey result = future.getNow();
                if (result == null) {
                    System.out.println("The node your are contacting is disconnected. Now exit.");
                    System.exit(0);
                }

                // print out response
                System.out.println("\nResponse from node " + contact + "\t" + chordIdToHex(chordId(contact)) + " (" + chordIdPosition(contact) + ").");
                System.out.println("Node " + result + "\t" + chordIdToHex(result) + " (" + chordIdPosition(result) + ").");

                takeInput(ctx, contact);
            });
        }
    }

    private static Promise<Void> checkStable(final ChannelHandlerContext ctx,
                                             final IdentityPublicKey contact,
                                             final Promise<Void> stableFuture) {
        yourPredecessorRequest(ctx, contact).addListener((FutureListener<IdentityPublicKey>) future12 -> {
            final IdentityPublicKey pred_addr = future12.getNow();
            yourSuccessorRequest(ctx, contact).addListener((FutureListener<IdentityPublicKey>) future1 -> {
                final IdentityPublicKey succ_addr = future1.getNow();

                if (pred_addr == null || succ_addr == null) {
                    System.out.println("The node your are contacting is disconnected. Now exit.");
                    System.exit(0);
                }

                final boolean pred = pred_addr.equals(contact);
                final boolean succ = succ_addr.equals(contact);

                if (pred ^ succ) {
                    System.out.println("Waiting for the system to be stable...");
                    ctx.executor().schedule(() -> checkStable(ctx, contact, stableFuture), 500, MILLISECONDS);
                }
                else {
                    stableFuture.setSuccess(null);
                }
            });
        });
        return stableFuture;
    }
}
